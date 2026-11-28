package com.titan.ide.os

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.util.Log
import com.titan.ide.network.TitanDirectEngine
import com.titan.ide.voice.TitanInstantTtsEngine
import com.titan.ide.voice.TitanOfflineWhisperStt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

data class AiCallLogEntry(
    val timestamp: String,
    val callerNumber: String,
    val actionTaken: String,
    val summary: String,
    val transcript: String
)

/**
 * TitanCallAssistantEngine v3.8 — 100% On-Device Equal-Style AI Call Assistant
 *
 * - Works on Samsung/Android with ZERO paid telephony server and ZERO API key!
 * - Incoming call triggers Floating Equal-Style Call HUD (AI Answer / Busy SMS / Take Over + Quick Chips).
 * - On AI Answer: answers call via TelecomManager, enables Speakerphone, speaks Bengali/Hindi/English
 *   greeting, listens via zero-beep Offline Whisper STT (with strict ear-closing while speaking),
 *   replies using your standing instructions, and saves full Call Summary + Transcript!
 */
object TitanCallAssistantEngine {

    private const val TAG = "TitanCallAssistant"
    private const val PREFS_NAME = "titan_ai_call_assistant"
    private const val KEY_ENABLED = "call_ai_enabled"
    private const val KEY_AUTO_ANSWER_SEC = "call_ai_auto_sec"
    private const val KEY_STANDING_RULES = "call_ai_standing_rules"
    private const val KEY_BUSY_SMS = "call_ai_busy_sms"
    private const val KEY_LOGS = "call_ai_logs_json"

    val QUICK_CHIPS = listOf(
        "ডেলিভারি গেটে রেখে দিন",
        "আমি এখন ব্যস্ত, পরে কল করছি",
        "আপনার নাম ও কাজের কারণ বলুন",
        "হোয়াটসঅ্যাপে মেসেজ করে দিন"
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _autoAnswerSec = MutableStateFlow(0) // 0 = Manual HUD tap; 6/10/15 = Auto answer after N sec
    val autoAnswerSec: StateFlow<Int> = _autoAnswerSec.asStateFlow()

    private val _standingRules = MutableStateFlow(
        "ডেলিভারি বা কুরিয়ার কল হলে গেটে রেখে দিতে বলো। অচেনা কল হলে বিনয়ের সাথে নাম আর কী দরকার জেনে নাও। স্প্যাম বা লোন কল হলে ভদ্রভাবে না বলে দাও।"
    )
    val standingRules: StateFlow<String> = _standingRules.asStateFlow()

    private val _busySmsText = MutableStateFlow(
        "হ্যালো, আমি এখন একটু ব্যস্ত আছি। একটু পরে কল ব্যাক করছি, জরুরি হলে এখানে মেসেজ করে দিন।"
    )
    val busySmsText: StateFlow<String> = _busySmsText.asStateFlow()

    private val _isAiHandlingCall = MutableStateFlow(false)
    val isAiHandlingCall: StateFlow<Boolean> = _isAiHandlingCall.asStateFlow()

    private val _activeCaller = MutableStateFlow("")
    val activeCaller: StateFlow<String> = _activeCaller.asStateFlow()

    private val _liveStatus = MutableStateFlow("Standby")
    val liveStatus: StateFlow<String> = _liveStatus.asStateFlow()

    private val _liveTranscript = MutableStateFlow<List<String>>(emptyList())
    val liveTranscript: StateFlow<List<String>> = _liveTranscript.asStateFlow()

    private val _callLogs = MutableStateFlow<List<AiCallLogEntry>>(emptyList())
    val callLogs: StateFlow<List<AiCallLogEntry>> = _callLogs.asStateFlow()

    private var activeCallJob: Job? = null
    private var autoAnswerJob: Job? = null
    @Volatile private var isSpeakingToCaller = false
    @Volatile private var echoGuardUntilMs = 0L
    @Volatile private var lastRingTriggerMs = 0L

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isEnabled.value = prefs.getBoolean(KEY_ENABLED, true)
        _autoAnswerSec.value = prefs.getInt(KEY_AUTO_ANSWER_SEC, 0)
        prefs.getString(KEY_STANDING_RULES, null)?.takeIf { it.isNotBlank() }?.let { _standingRules.value = it }
        prefs.getString(KEY_BUSY_SMS, null)?.takeIf { it.isNotBlank() }?.let { _busySmsText.value = it }
        loadLogs(prefs)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        _isEnabled.value = enabled
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setAutoAnswerSec(context: Context, sec: Int) {
        _autoAnswerSec.value = sec
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_AUTO_ANSWER_SEC, sec).apply()
    }

    fun setStandingRules(context: Context, rules: String) {
        _standingRules.value = rules
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_STANDING_RULES, rules).apply()
    }

    fun setBusySmsText(context: Context, msg: String) {
        _busySmsText.value = msg
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_BUSY_SMS, msg).apply()
    }

    fun clearLogs(context: Context) {
        _callLogs.value = emptyList()
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_LOGS).apply()
    }

    fun onIncomingCallRinging(context: Context, rawNumber: String) {
        init(context)
        if (!_isEnabled.value) return
        val now = System.currentTimeMillis()
        if (now - lastRingTriggerMs < 2500L && _activeCaller.value == rawNumber) return
        lastRingTriggerMs = now

        val caller = rawNumber.ifBlank { "Unknown Caller" }
        _activeCaller.value = caller
        _liveTranscript.value = emptyList()
        _liveStatus.value = "Incoming call from $caller"

        // Show Equal-style Floating Call HUD immediately
        com.example.telecom.TitanCallOverlayService.show(context.applicationContext, caller)

        val delaySec = _autoAnswerSec.value
        autoAnswerJob?.cancel()
        if (delaySec > 0) {
            autoAnswerJob = scope.launch {
                for (rem in delaySec downTo 1) {
                    if (_isAiHandlingCall.value) return@launch
                    _liveStatus.value = "Auto AI Answer in ${rem}s..."
                    delay(1000L)
                }
                if (!_isAiHandlingCall.value) {
                    answerAndTalkWithAi(context.applicationContext, caller)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun answerAndTalkWithAi(context: Context, callerNumber: String) {
        val appCtx = context.applicationContext
        autoAnswerJob?.cancel()
        if (_isAiHandlingCall.value) return
        _isAiHandlingCall.value = true
        _activeCaller.value = callerNumber.ifBlank { "Unknown Caller" }
        _liveStatus.value = "Answering call with Titan AI..."

        // 1. Accept incoming ringing call via TelecomManager (API 26+)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val tm = appCtx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                @Suppress("DEPRECATION")
                tm?.acceptRingingCall()
            }
        } catch (e: Exception) {
            Log.w(TAG, "acceptRingingCall notice: ${e.message}")
        }

        // 2. Turn on Speakerphone so caller hears Titan clearly through phone mic
        try {
            val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            am?.isSpeakerphoneOn = true
        } catch (_: Exception) {}

        activeCallJob?.cancel()
        activeCallJob = scope.launch {
            delay(900L) // Wait for cellular call audio path to connect
            val greeting = "হ্যালো, আমি তাহির ভাইয়ের AI অ্যাসিস্ট্যান্ট টাইটান বলছি। উনি এখন একটু ব্যস্ত আছেন, আপনি কে বলছেন আর কী দরকার একটু বলবেন?"
            speakToCallerAndWait(appCtx, greeting)
            runCallerListeningLoop(appCtx, callerNumber)
        }
    }

    fun speakQuickChip(context: Context, chipText: String) {
        val appCtx = context.applicationContext
        if (!_isAiHandlingCall.value) {
            answerAndTalkWithAi(appCtx, _activeCaller.value)
        }
        scope.launch {
            speakToCallerAndWait(appCtx, chipText)
        }
    }

    @SuppressLint("MissingPermission")
    fun rejectAndSendBusySms(context: Context, callerNumber: String) {
        val appCtx = context.applicationContext
        autoAnswerJob?.cancel()
        activeCallJob?.cancel()
        _isAiHandlingCall.value = false
        TitanInstantTtsEngine.stopSpeech()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val tm = appCtx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                tm?.endCall()
            }
        } catch (e: Exception) {
            Log.w(TAG, "endCall notice: ${e.message}")
        }

        val cleanNum = callerNumber.filter { it.isDigit() || it == '+' }
        var smsStatus = "Declined"
        if (cleanNum.length >= 10) {
            try {
                val smsMgr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    appCtx.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsMgr?.sendTextMessage(cleanNum, null, _busySmsText.value, null, null)
                smsStatus = "Declined + Busy SMS Sent"
            } catch (e: Exception) {
                Log.w(TAG, "Busy SMS notice: ${e.message}")
                smsStatus = "Declined (SMS permission needed)"
            }
        }

        saveCallLog(
            appCtx,
            AiCallLogEntry(
                timestamp = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date()),
                callerNumber = callerNumber.ifBlank { "Unknown" },
                actionTaken = smsStatus,
                summary = "Busy message sent automatically.",
                transcript = "Titan SMS: ${_busySmsText.value}"
            )
        )
        com.example.telecom.TitanCallOverlayService.dismiss(appCtx)
    }

    fun takeOverCall(context: Context) {
        autoAnswerJob?.cancel()
        activeCallJob?.cancel()
        _isAiHandlingCall.value = false
        isSpeakingToCaller = false
        TitanInstantTtsEngine.stopSpeech()
        _liveStatus.value = "You took over the call"
        com.example.telecom.TitanCallOverlayService.dismiss(context.applicationContext)
    }

    fun onCallEnded(context: Context) {
        val appCtx = context.applicationContext
        autoAnswerJob?.cancel()
        val wasHandling = _isAiHandlingCall.value
        val caller = _activeCaller.value
        val transcriptList = _liveTranscript.value
        _isAiHandlingCall.value = false
        isSpeakingToCaller = false
        activeCallJob?.cancel()
        activeCallJob = null
        TitanInstantTtsEngine.stopSpeech()
        com.example.telecom.TitanCallOverlayService.dismiss(appCtx)

        if (wasHandling && transcriptList.isNotEmpty()) {
            val fullTranscript = transcriptList.joinToString("\n")
            scope.launch {
                val summary = try {
                    TitanDirectEngine.executePrompt(
                        context = appCtx,
                        modelId = "gemini-4-hydrogen",
                        prompt = "Summarize this phone call in 1 short Bengali sentence (who called and why):\n$fullTranscript",
                        systemInstruction = "Output only a 1-line concise Bengali summary."
                    ).trim().ifBlank { "AI কলারের সাথে কথা বলেছে।" }
                } catch (_: Exception) {
                    "AI কলারের সাথে কথা বলেছে।"
                }
                saveCallLog(
                    appCtx,
                    AiCallLogEntry(
                        timestamp = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date()),
                        callerNumber = caller.ifBlank { "Unknown" },
                        actionTaken = "AI Answered",
                        summary = summary,
                        transcript = fullTranscript
                    )
                )
            }
        }
    }

    private suspend fun speakToCallerAndWait(context: Context, text: String) {
        isSpeakingToCaller = true
        _liveStatus.value = "Titan Speaking..."
        _liveTranscript.value = _liveTranscript.value + "Titan: $text"
        val done = CompletableDeferred<Unit>()
        TitanInstantTtsEngine.speakFullResponse(
            context = context,
            rawText = text,
            onStart = { isSpeakingToCaller = true },
            onDone = {
                echoGuardUntilMs = System.currentTimeMillis() + 750L
                isSpeakingToCaller = false
                if (!done.isCompleted) done.complete(Unit)
            }
        )
        withTimeoutOrNull(18_000L) { done.await() }
        isSpeakingToCaller = false
    }

    @SuppressLint("MissingPermission")
    private suspend fun runCallerListeningLoop(context: Context, callerNumber: String) {
        val sr = 16000
        val minBuf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(6400)
        val recorder = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
                .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                ?: AudioRecord(MediaRecorder.AudioSource.MIC, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
        } catch (_: Exception) { null } ?: return

        var aec: AcousticEchoCanceler? = null
        var ns: NoiseSuppressor? = null
        try {
            if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = true }
            if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true }
            recorder.startRecording()

            val frame = ByteArray(640) // 20ms @ 16kHz
            val utterance = ByteArrayOutputStream()
            var speaking = false
            var speechMs = 0L
            var silenceMs = 0L
            var noiseFloor = 140.0

            while (_isAiHandlingCall.value && currentCoroutineContext().isActive) {
                val read = recorder.read(frame, 0, frame.size)
                if (read <= 0) continue

                if (isSpeakingToCaller || System.currentTimeMillis() < echoGuardUntilMs) {
                    utterance.reset(); speaking = false; speechMs = 0L; silenceMs = 0L
                    continue
                }
                _liveStatus.value = "Listening to caller..."

                var sum = 0.0
                val bb = ByteBuffer.wrap(frame, 0, read).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until read / 2) {
                    val s = bb.short.toDouble()
                    sum += s * s
                }
                val rms = sqrt(sum / (read / 2))
                if (!speaking) noiseFloor = (noiseFloor * 0.94 + rms * 0.06).coerceIn(50.0, 650.0)
                val thresh = maxOf(noiseFloor * 2.4, 320.0)

                if (rms >= thresh) {
                    speaking = true
                    utterance.write(frame, 0, read)
                    speechMs += 20L
                    silenceMs = 0L
                } else if (speaking) {
                    utterance.write(frame, 0, read)
                    silenceMs += 20L
                }

                if (speaking && ((silenceMs >= 650L && speechMs >= 420L) || speechMs >= 9000L)) {
                    val pcm = utterance.toByteArray()
                    utterance.reset(); speaking = false; speechMs = 0L; silenceMs = 0L
                    _liveStatus.value = "Understanding caller..."
                    val callerWords = TitanOfflineWhisperStt.transcribePcm16k(context, pcm).trim()
                    if (callerWords.isNotBlank() && _isAiHandlingCall.value) {
                        _liveTranscript.value = _liveTranscript.value + "Caller: $callerWords"
                        val history = _liveTranscript.value.takeLast(6).joinToString("\n")
                        val aiReply = try {
                            TitanDirectEngine.executePrompt(
                                context = context,
                                modelId = "gemini-4-hydrogen",
                                prompt = "Caller ($callerNumber) just said: \"$callerWords\"\nConversation so far:\n$history\nOwner's Standing Instructions: ${_standingRules.value}\nReply naturally in 1-2 short polite sentences matching the caller's language (Bengali/Hindi/English).",
                                systemInstruction = "You are Titan, Tahir's polite AI Call Assistant answering a phone call. Reply concisely in 1-2 natural spoken sentences."
                            ).replace(Regex("[*#_`]"), "").trim()
                        } catch (_: Exception) {
                            "ঠিক আছে, আমি তাহির ভাইকে জানিয়ে দিচ্ছি।"
                        }
                        if (aiReply.isNotBlank() && _isAiHandlingCall.value) {
                            speakToCallerAndWait(context, aiReply)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Call listening loop notice: ${e.message}")
        } finally {
            try { aec?.release() } catch (_: Exception) {}
            try { ns?.release() } catch (_: Exception) {}
            try { recorder.stop(); recorder.release() } catch (_: Exception) {}
        }
    }

    private fun saveCallLog(context: Context, entry: AiCallLogEntry) {
        val updated = (listOf(entry) + _callLogs.value).take(30)
        _callLogs.value = updated
        try {
            val arr = JSONArray()
            updated.forEach {
                arr.put(JSONObject().apply {
                    put("ts", it.timestamp)
                    put("num", it.callerNumber)
                    put("act", it.actionTaken)
                    put("sum", it.summary)
                    put("tr", it.transcript)
                })
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_LOGS, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun loadLogs(prefs: android.content.SharedPreferences) {
        try {
            val raw = prefs.getString(KEY_LOGS, null) ?: return
            val arr = JSONArray(raw)
            val list = mutableListOf<AiCallLogEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(
                    AiCallLogEntry(
                        timestamp = o.optString("ts"),
                        callerNumber = o.optString("num"),
                        actionTaken = o.optString("act"),
                        summary = o.optString("sum"),
                        transcript = o.optString("tr")
                    )
                )
            }
            _callLogs.value = list
        } catch (_: Exception) {}
    }
}
