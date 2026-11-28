package com.titan.ide.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.voice.TitanAssistantConfig

/**
 * TitanSmartVoiceRecognizer v3.5 — Clean Single-Shot On-Demand Speech Recognizer
 *
 * - NEVER mutes or locks any system audio stream.
 * - NEVER loops during silence (no repeating Samsung "ting-ting" loop!).
 * - Triggered on-demand by TitanLiveCallEngine's continuous hardware AudioRecord VAD
 *   when no Gemini PSID/API key is configured, so Live Call works 100% out-of-the-box
 *   with ZERO Gemini Session sign-in and ZERO API keys.
 */
object TitanSmartVoiceRecognizer {

    private const val TAG = "TitanSmartStt"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    @Volatile
    private var active = false
    @Volatile
    private var continuousLoop = false
    private var lastPartialCb: ((String) -> Unit)? = null
    private var lastFinalCb: ((String) -> Unit)? = null
    private var lastEndedNoResultCb: (() -> Unit)? = null
    private var lastLangTag: String = "bn-IN"

    fun isAvailable(context: Context): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(context.applicationContext)
        } catch (_: Exception) {
            false
        }
    }

    fun startListening(
        context: Context,
        languageTag: String? = null,
        keepLooping: Boolean = false,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onEndedWithoutResult: (() -> Unit)? = null
    ) {
        val appCtx = context.applicationContext
        lastPartialCb = onPartial
        lastFinalCb = onFinal
        lastEndedNoResultCb = onEndedWithoutResult
        continuousLoop = keepLooping
        val configured = TitanAssistantConfig.getSelectedLanguage(appCtx).bcp47Tag
        lastLangTag = when {
            !languageTag.isNullOrBlank() && languageTag != "auto" -> languageTag
            configured.isNotBlank() && configured != "auto" -> configured
            else -> "bn-IN"
        }
        active = true

        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            startInternal(appCtx)
        }
    }

    fun stopListening() {
        active = false
        continuousLoop = false
        lastEndedNoResultCb = null
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            try {
                recognizer?.stopListening()
                recognizer?.cancel()
                recognizer?.destroy()
            } catch (_: Exception) {}
            recognizer = null
        }
    }

    fun pauseTemporarily() {
        active = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            try {
                recognizer?.stopListening()
                recognizer?.cancel()
                recognizer?.destroy()
            } catch (_: Exception) {}
            recognizer = null
        }
    }

    fun resumeIfLooping(context: Context) {
        if (!continuousLoop) return
        active = true
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (active && continuousLoop) startInternal(context.applicationContext)
        }, 400L)
    }

    private fun startInternal(appCtx: Context) {
        if (!active) return
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(appCtx).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onPartialResults(partialResults: Bundle?) {
                        val txt = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (txt.isNotBlank() && active) {
                            lastPartialCb?.invoke(txt)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val txt = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (txt.isNotBlank()) {
                            active = false
                            lastFinalCb?.invoke(txt)
                        } else if (active && continuousLoop) {
                            mainHandler.postDelayed({
                                if (active && continuousLoop) startInternal(appCtx)
                            }, 900L)
                        } else {
                            active = false
                            lastEndedNoResultCb?.invoke()
                        }
                    }

                    override fun onError(error: Int) {
                        Log.d(TAG, "STT cycle notice code=$error")
                        if (active && continuousLoop) {
                            val delayMs = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 1400L
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1800L
                                else -> 1200L
                            }
                            mainHandler.postDelayed({
                                if (active && continuousLoop) startInternal(appCtx)
                            }, delayMs)
                        } else if (active) {
                            active = false
                            lastEndedNoResultCb?.invoke()
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lastLangTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lastLangTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 4500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Smart STT start error: ${e.message}")
            active = false
            lastEndedNoResultCb?.invoke()
        }
    }
}
