package com.titan.ide.voice

import android.content.Context
import android.util.Log
import com.example.voice.TitanAssistantConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * TitanOfflineWhisperStt v3.5 — 100% On-Device Multilingual Offline Neural STT
 *
 * - Runs directly on TitanLiveCallEngine's continuous hardware AudioRecord 16kHz PCM stream!
 * - ZERO Android SpeechRecognizer -> ZERO Samsung "ting-ting" beep EVER!
 * - ZERO Gemini Session / PSID Sign-In!
 * - ZERO API Keys!
 * - Supports Bengali, Hindi, English & 96+ languages via int8 ONNX Whisper Tiny (downloaded once).
 */
object TitanOfflineWhisperStt {

    private const val TAG = "TitanOfflineWhisper"
    private const val BASE_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main"

    private data class ModelAsset(
        val fileName: String,
        val minBytes: Long
    )

    private val ASSETS = listOf(
        ModelAsset("tiny-tokens.txt", 100_000L),
        ModelAsset("tiny-encoder.int8.onnx", 5_000_000L),
        ModelAsset("tiny-decoder.int8.onnx", 40_000_000L)
    )

    private val initMutex = Mutex()
    private val decodeMutex = Mutex()

    @Volatile
    private var recognizer: OfflineRecognizer? = null
    @Volatile
    private var loadedLanguage: String = ""

    private val downloadClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun getModelDir(context: Context): File {
        val dir = File(context.applicationContext.filesDir, "titan_whisper_tiny")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isModelDownloaded(context: Context): Boolean {
        val dir = getModelDir(context)
        return ASSETS.all { asset ->
            val f = File(dir, asset.fileName)
            f.exists() && f.length() >= asset.minBytes
        }
    }

    private fun resolveWhisperLanguage(context: Context): String {
        val tag = TitanAssistantConfig.getSelectedLanguage(context.applicationContext).bcp47Tag.lowercase()
        return when {
            tag.startsWith("bn") -> "bn"
            tag.startsWith("hi") -> "hi"
            tag.startsWith("en") -> "en"
            else -> "bn" // Default Bengali to prevent Whisper Tiny English "sad music" hallucinations
        }
    }

    /**
     * Ensures the on-device Whisper Tiny int8 model is downloaded and initialized in RAM.
     * Reports human-readable status updates (e.g. download percentage) via onStatus.
     */
    suspend fun ensureReady(
        context: Context,
        onStatus: ((String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val desiredLang = resolveWhisperLanguage(appCtx)

        if (recognizer != null && loadedLanguage == desiredLang) {
            return@withContext true
        }

        initMutex.withLock {
            if (recognizer != null && loadedLanguage == desiredLang) {
                return@withLock true
            }

            val dir = getModelDir(appCtx)
            if (!isModelDownloaded(appCtx)) {
                val ok = downloadAllAssets(dir, onStatus)
                if (!ok) {
                    onStatus?.invoke("ভয়েস মডেল ডাউনলোড ব্যর্থ — ইন্টারনেট চেক করে আবার চেষ্টা করো")
                    return@withLock false
                }
            }

            try {
                onStatus?.invoke("অফলাইন ভয়েস ইঞ্জিন চালু হচ্ছে...")
                try { recognizer?.release() } catch (_: Exception) {}
                recognizer = null

                val encoderPath = File(dir, "tiny-encoder.int8.onnx").absolutePath
                val decoderPath = File(dir, "tiny-decoder.int8.onnx").absolutePath
                val tokensPath = File(dir, "tiny-tokens.txt").absolutePath

                val config = OfflineRecognizerConfig(
                    modelConfig = OfflineModelConfig(
                        whisper = OfflineWhisperModelConfig(
                            encoder = encoderPath,
                            decoder = decoderPath,
                            language = desiredLang,
                            task = "transcribe"
                        ),
                        tokens = tokensPath,
                        numThreads = 2,
                        debug = false,
                        modelType = "whisper"
                    )
                )

                recognizer = OfflineRecognizer(assetManager = null, config = config)
                loadedLanguage = desiredLang
                Log.i(TAG, "On-Device Whisper Tiny Ready (lang='${desiredLang.ifBlank { "auto" }}')")
                onStatus?.invoke("")
                true
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to init on-device Whisper: ${e.message}", e)
                recognizer = null
                onStatus?.invoke("অফলাইন ভয়েস লোড করতে সমস্যা হয়েছে")
                false
            }
        }
    }

    private fun downloadAllAssets(dir: File, onStatus: ((String) -> Unit)?): Boolean {
        val totalApproxBytes = 103_600_000L
        var downloadedBeforeCurrent = 0L

        for (asset in ASSETS) {
            val target = File(dir, asset.fileName)
            if (target.exists() && target.length() >= asset.minBytes) {
                downloadedBeforeCurrent += target.length()
                continue
            }

            val partFile = File(dir, "${asset.fileName}.part")
            if (partFile.exists()) partFile.delete()

            val url = "$BASE_URL/${asset.fileName}"
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "TitanIDE/3.5")
                    .build()

                downloadClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.e(TAG, "Download HTTP ${resp.code} for ${asset.fileName}")
                        return false
                    }
                    val body = resp.body ?: return false
                    val buffer = ByteArray(32 * 1024)
                    var fileBytes = 0L
                    var lastReportedPct = -1

                    FileOutputStream(partFile).use { fos ->
                        body.byteStream().use { input ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                fos.write(buffer, 0, read)
                                fileBytes += read
                                val pct = (((downloadedBeforeCurrent + fileBytes) * 100L) / totalApproxBytes)
                                    .toInt()
                                    .coerceIn(1, 99)
                                if (pct != lastReportedPct) {
                                    lastReportedPct = pct
                                    onStatus?.invoke("অফলাইন ভয়েস মডেল ডাউনলোড হচ্ছে... $pct% (একবারই লাগবে)")
                                }
                            }
                        }
                    }

                    if (fileBytes < asset.minBytes) {
                        partFile.delete()
                        return false
                    }
                    if (target.exists()) target.delete()
                    if (!partFile.renameTo(target)) {
                        partFile.copyTo(target, overwrite = true)
                        partFile.delete()
                    }
                    downloadedBeforeCurrent += fileBytes
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading ${asset.fileName}: ${e.message}", e)
                if (partFile.exists()) partFile.delete()
                return false
            }
        }
        return true
    }

    /**
     * Transcribes 16kHz Mono 16-bit Little-Endian PCM directly on-device with ZERO beeps & ZERO sign-in.
     */
    suspend fun transcribePcm16k(
        context: Context,
        pcmBytes: ByteArray,
        onStatus: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        if (pcmBytes.size < 3200) return@withContext ""
        if (!ensureReady(context, onStatus)) return@withContext ""

        val rec = recognizer ?: return@withContext ""
        val sampleCount = pcmBytes.size / 2
        val samples = FloatArray(sampleCount)
        val bb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            samples[i] = bb.short / 32768.0f
        }

        decodeMutex.withLock {
            try {
                val stream = rec.createStream()
                try {
                    stream.acceptWaveform(samples, 16000)
                    rec.decode(stream)
                    val raw = rec.getResult(stream).text.trim()
                    cleanWhisperText(raw)
                } finally {
                    stream.release()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "On-device Whisper decode notice: ${e.message}")
                ""
            }
        }
    }

    private fun cleanWhisperText(raw: String): String {
        if (raw.isBlank()) return ""
        // Strip non-speech Whisper hallucination tags like [BLANK_AUDIO], (music), [Silence]
        val stripped = raw
            .replace(Regex("\\[[^\\]]*\\]"), " ")
            .replace(Regex("\\([^\\)]*\\)"), " ")
            .replace(Regex("\\*[^\\*]*\\*"), " ")
            .replace(Regex("♪[^♪]*♪"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val lower = stripped.lowercase()
        val hallucinations = setOf(
            "thank you.", "thank you", "thanks for watching.", "thanks for watching",
            "you", "bye.", "bye", "mbc", "subtitle", "subtitles", "...",
            "sad music", "music", "applause", "laughter", "sigh", "cheering",
            "footsteps", "silence", "foreign", "music playing", "soft music", "background music"
        )
        if (stripped.length < 2 || lower in hallucinations) return ""
        return stripped
    }

    fun release() {
        try { recognizer?.release() } catch (_: Exception) {}
        recognizer = null
        loadedLanguage = ""
    }
}
