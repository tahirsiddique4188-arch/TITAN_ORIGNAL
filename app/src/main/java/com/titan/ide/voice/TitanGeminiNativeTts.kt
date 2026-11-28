package com.titan.ide.voice

import android.content.Context
import android.util.Base64
import android.util.Log
import com.titan.ide.network.TitanDirectEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * TitanGeminiNativeTts — Ultra-Natural Studio Human Voices via Gemini 2.5 Flash Native Audio TTS
 * Supports natural Bengali, Hindi, and English with zero robotic raspiness:
 *   - Female: Aoede (warm conversational), Kore (crisp friendly)
 *   - Male: Charon (calm Jarvis-like), Fenrir (energetic natural)
 */
object TitanGeminiNativeTts {

    private const val TAG = "TitanGeminiNativeTts"
    private const val SAMPLE_RATE_HZ = 24000

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var lastFailureMs = 0L
    private const val COOLDOWN_MS = 60_000L

    fun resolveGeminiVoiceName(personaId: String?): String {
        val id = (personaId ?: "").lowercase()
        val isMale = id.contains("male") || id.contains("jarvis") || id.contains("arjun") ||
            id.contains("bashkar") || id.contains("pradeep") || id.contains("madhur") ||
            id.contains("rohan") || id.contains("fenrir") || id.contains("charon") ||
            id.contains("man") || id.contains("boy")
        return when {
            id.contains("fenrir") || id.contains("rohan") -> "Fenrir"
            isMale -> "Charon"
            id.contains("kore") || id.contains("zara") -> "Kore"
            else -> "Aoede"
        }
    }

    /**
     * Synthesizes 24 kHz 16-bit mono PCM via Gemini Native Audio TTS.
     * Returns Pair(pcmBytes, sampleRate) or null if API key is missing or endpoint unavailable.
     */
    suspend fun synthesizeNaturalPcm(
        context: Context,
        text: String,
        personaId: String? = null
    ): Pair<ByteArray, Int>? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastFailureMs < COOLDOWN_MS) return@withContext null

        val apiKey = TitanDirectEngine.getApiKey(context, "gemini").trim()
        if (!apiKey.startsWith("AIza") || apiKey.contains("Titan")) {
            return@withContext null
        }

        val cleanText = text.trim().take(700)
        if (cleanText.isBlank()) return@withContext null

        val voiceName = resolveGeminiVoiceName(personaId)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent?key=$apiKey"

        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "Speak this naturally in a warm, human, conversational voice: $cleanText")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().put("AUDIO"))
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().apply {
                            put("voiceName", voiceName)
                        })
                    })
                })
            })
        }

        return@withContext try {
            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Gemini Native TTS HTTP ${resp.code}")
                    if (resp.code in listOf(401, 403, 404, 429)) {
                        lastFailureMs = System.currentTimeMillis()
                    }
                    return@withContext null
                }
                val body = JSONObject(resp.body?.string() ?: "{}")
                val parts = body.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val inline = parts.optJSONObject(i)?.optJSONObject("inlineData")
                        val b64 = inline?.optString("data", "").orEmpty()
                        if (b64.isNotBlank()) {
                            val raw = Base64.decode(b64, Base64.DEFAULT)
                            if (raw.size > 200) {
                                return@withContext raw to SAMPLE_RATE_HZ
                            }
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini Native TTS error: ${e.message}")
            null
        }
    }
}
