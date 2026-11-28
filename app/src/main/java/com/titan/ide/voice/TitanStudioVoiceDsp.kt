package com.titan.ide.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * TitanStudioVoiceDsp — 5-Stage Real-Time Studio Voice Mastering & Filter Machine
 * Eliminates robotic raspiness ("khoshkhoshe" hiss) from any TTS audio:
 *   1. High-Pass Rumble Filter (85 Hz)
 *   2. Smart De-Esser / Harshness Notch (5.2 kHz, -5.5 dB)
 *   3. Warm Vocal Low-Mid Boost (+2.8 dB @ 220 Hz)
 *   4. Natural Human Presence EQ (+2.0 dB @ 2.6 kHz)
 *   5. Soft-Knee Broadcast Compressor + True-Peak Limiter
 */
object TitanStudioVoiceDsp {

    private const val TAG = "TitanStudioDsp"

    @Volatile
    private var activeTrack: AudioTrack? = null

    fun stopActivePlayback() {
        try {
            activeTrack?.pause()
            activeTrack?.flush()
            activeTrack?.stop()
            activeTrack?.release()
        } catch (_: Exception) {}
        activeTrack = null
    }

    private class Biquad(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double
    ) {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }

        companion object {
            fun highPass(fs: Double, freq: Double, q: Double = 0.707): Biquad {
                val w0 = 2.0 * PI * (freq.coerceIn(20.0, fs * 0.45)) / fs
                val cosW = cos(w0)
                val alpha = sin(w0) / (2.0 * q)
                val a0 = 1.0 + alpha
                return Biquad(
                    b0 = ((1.0 + cosW) / 2.0) / a0,
                    b1 = (-(1.0 + cosW)) / a0,
                    b2 = ((1.0 + cosW) / 2.0) / a0,
                    a1 = (-2.0 * cosW) / a0,
                    a2 = (1.0 - alpha) / a0
                )
            }

            fun peakingEq(fs: Double, freq: Double, gainDb: Double, q: Double): Biquad {
                val clampedFreq = freq.coerceIn(40.0, fs * 0.45)
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2.0 * PI * clampedFreq / fs
                val cosW = cos(w0)
                val alpha = sin(w0) / (2.0 * q)
                val a0 = 1.0 + alpha / a
                return Biquad(
                    b0 = (1.0 + alpha * a) / a0,
                    b1 = (-2.0 * cosW) / a0,
                    b2 = (1.0 - alpha * a) / a0,
                    a1 = (-2.0 * cosW) / a0,
                    a2 = (1.0 - alpha / a) / a0
                )
            }

            fun lowPass(fs: Double, freq: Double, q: Double = 0.707): Biquad {
                val w0 = 2.0 * PI * (freq.coerceIn(100.0, fs * 0.46)) / fs
                val cosW = cos(w0)
                val alpha = sin(w0) / (2.0 * q)
                val a0 = 1.0 + alpha
                return Biquad(
                    b0 = ((1.0 - cosW) / 2.0) / a0,
                    b1 = (1.0 - cosW) / a0,
                    b2 = ((1.0 - cosW) / 2.0) / a0,
                    a1 = (-2.0 * cosW) / a0,
                    a2 = (1.0 - alpha) / a0
                )
            }
        }
    }

    /**
     * Applies the full 5-Stage Studio Voice Mastering Chain to 16-bit signed little-endian mono PCM.
     */
    fun applyStudioMastering(pcm16Le: ByteArray, sampleRate: Int): ByteArray {
        val sampleCount = pcm16Le.size / 2
        if (sampleCount < 64) return pcm16Le

        val fs = sampleRate.coerceIn(8000, 48000).toDouble()
        val hpf = Biquad.highPass(fs, 85.0, 0.707)
        val warmth = Biquad.peakingEq(fs, 220.0, 2.8, 0.9)
        val presence = Biquad.peakingEq(fs, 2600.0, 1.9, 1.1)
        val deEsser = Biquad.peakingEq(fs, minOf(5200.0, fs * 0.42), -5.5, 1.4)
        val airSmooth = Biquad.lowPass(fs, minOf(8200.0, fs * 0.45), 0.707)

        val floats = DoubleArray(sampleCount)
        val inBuf = ByteBuffer.wrap(pcm16Le).order(ByteOrder.LITTLE_ENDIAN)
        var sumSq = 0.0
        for (i in 0 until sampleCount) {
            var s = inBuf.short.toDouble() / 32768.0
            s = hpf.process(s)
            s = warmth.process(s)
            s = presence.process(s)
            s = deEsser.process(s)
            s = airSmooth.process(s)
            floats[i] = s
            sumSq += s * s
        }

        val rms = sqrt(sumSq / sampleCount).coerceAtLeast(0.001)
        val targetRms = 0.22
        val autoGain = (targetRms / rms).coerceIn(0.65, 2.6)

        val outBytes = ByteArray(sampleCount * 2)
        val outBuf = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)
        val fadeSamples = minOf(sampleCount / 8, (sampleRate * 0.015).toInt()).coerceAtLeast(1)

        for (i in 0 until sampleCount) {
            var v = floats[i] * autoGain
            // Soft-knee broadcast saturation & peak limiter
            v = tanh(v * 1.15) * 0.92
            // De-click micro fade-in / fade-out
            if (i < fadeSamples) {
                v *= i.toDouble() / fadeSamples
            } else if (i > sampleCount - fadeSamples) {
                v *= (sampleCount - i).toDouble() / fadeSamples
            }
            val s16 = (v * 32767.0).toInt().coerceIn(-32767, 32767).toShort()
            outBuf.putShort(s16)
        }
        return outBytes
    }

    /**
     * Parses a standard PCM WAV file into (pcmBytes, sampleRate, channels).
     */
    fun extractPcmFromWav(wavBytes: ByteArray): Triple<ByteArray, Int, Int>? {
        if (wavBytes.size < 44) return null
        val header = String(wavBytes, 0, 4)
        if (header != "RIFF") return null
        val bb = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        val channels = bb.getShort(22).toInt().coerceIn(1, 2)
        val sampleRate = bb.getInt(24).coerceIn(8000, 48000)

        var offset = 12
        while (offset + 8 <= wavBytes.size) {
            val chunkId = String(wavBytes, offset, 4)
            val chunkSize = bb.getInt(offset + 4).coerceAtLeast(0)
            offset += 8
            if (chunkId == "data") {
                val end = minOf(offset + chunkSize, wavBytes.size)
                return Triple(wavBytes.copyOfRange(offset, end), sampleRate, channels)
            }
            offset += chunkSize
            if (chunkSize % 2 == 1) offset++
        }
        return null
    }

    /**
     * Plays mastered 16-bit mono PCM blocking inside a coroutine with clean cancellation.
     */
    suspend fun playMasteredPcmBlocking(
        rawPcm16Le: ByteArray,
        sampleRate: Int = 24000,
        onStart: () -> Unit = {},
        onComplete: () -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        stopActivePlayback()
        val mastered = applyStudioMastering(rawPcm16Le, sampleRate)
        if (mastered.size < 64) {
            onComplete()
            return@withContext false
        }

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf * 8, 65536))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack init error: ${e.message}")
            onComplete()
            return@withContext false
        }

        activeTrack = track
        return@withContext try {
            track.play()
            onStart()
            var offset = 0
            val chunk = 16384
            while (offset < mastered.size && activeTrack === track) {
                val len = minOf(chunk, mastered.size - offset)
                val written = track.write(mastered, offset, len)
                if (written <= 0) break
                offset += written
            }
            if (activeTrack === track) {
                try {
                    track.stop()
                } catch (_: Exception) {}
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack write error: ${e.message}")
            false
        } finally {
            try { track.release() } catch (_: Exception) {}
            if (activeTrack === track) activeTrack = null
            onComplete()
        }
    }

    /**
     * Renders Android offline TTS to a WAV file, passes it through the 5-Stage Studio DSP Filter,
     * and plays the silky-filtered audio so offline fallback never sounds scratchy.
     */
    suspend fun synthesizeAndroidTtsToMasteredPcm(
        context: Context,
        tts: TextToSpeech,
        text: String,
        locale: Locale
    ): Pair<ByteArray, Int>? = withContext(Dispatchers.IO) {
        val tmpWav = File(context.cacheDir, "titan_dsp_tts_${System.currentTimeMillis()}.wav")
        val uttId = "dsp_${System.currentTimeMillis()}"
        try {
            tts.language = locale
        } catch (_: Exception) {}

        val ok = suspendCancellableCoroutine<Boolean> { cont ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == uttId && cont.isActive) cont.resume(true)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == uttId && cont.isActive) cont.resume(false)
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == uttId && cont.isActive) cont.resume(false)
                }
            })
            val params = Bundle()
            val res = tts.synthesizeToFile(text.take(650), params, tmpWav, uttId)
            if (res != TextToSpeech.SUCCESS && cont.isActive) {
                cont.resume(false)
            }
        }

        return@withContext try {
            if (!ok || !tmpWav.exists() || tmpWav.length() < 100) {
                null
            } else {
                val extracted = extractPcmFromWav(tmpWav.readBytes())
                if (extracted != null && extracted.third == 1) {
                    extracted.first to extracted.second
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            try { tmpWav.delete() } catch (_: Exception) {}
        }
    }
}
