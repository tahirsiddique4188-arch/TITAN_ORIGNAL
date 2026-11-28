package com.titan.ide.brain

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * TitanFirebaseBrainSync — Zero-SDK Firebase Realtime Database REST API Cloud Brain Sync & Restore
 *
 * Keeps the user's entire Assistant Brain (Identity, Locked Character, Assistant Name,
 * User Name, All Categorized Memories & Cross-Chat Context) safely backed up in Firebase.
 * If the user loses their phone or switches devices, entering their Firebase URL + Brain Sync Code
 * restores their exact Assistant & full Brain in 1 tap!
 */
object TitanFirebaseBrainSync {

    private const val TAG = "TitanCloudBrain"
    private const val PREFS_NAME = "titan_cloud_brain_prefs"
    private const val KEY_FIREBASE_URL = "firebase_db_url"
    private const val KEY_SYNC_CODE = "brain_sync_code"
    private const val KEY_AUTH_SECRET = "firebase_auth_secret"
    private const val KEY_AUTO_SYNC = "brain_auto_sync_enabled"
    private const val KEY_LAST_SYNC_MS = "brain_last_sync_ms"

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun getFirebaseUrl(context: Context): String {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FIREBASE_URL, "")?.trim().orEmpty()
    }

    fun getAuthSecret(context: Context): String {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AUTH_SECRET, "")?.trim().orEmpty()
    }

    fun isAutoSyncEnabled(context: Context): Boolean {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SYNC, true)
    }

    fun getLastSyncTimeMs(context: Context): Long {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC_MS, 0L)
    }

    fun getOrCreateSyncCode(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_SYNC_CODE, "")?.trim().orEmpty()
        if (existing.isNotBlank()) return existing
        val generated = "TITAN-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"
        prefs.edit().putString(KEY_SYNC_CODE, generated).apply()
        return generated
    }

    fun saveCloudConfig(
        context: Context,
        firebaseUrl: String,
        syncCode: String,
        authSecret: String = "",
        autoSync: Boolean = true
    ) {
        val cleanUrl = firebaseUrl.trim().trimEnd('/')
        val cleanCode = sanitizeSyncCode(syncCode).ifBlank { getOrCreateSyncCode(context) }
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FIREBASE_URL, cleanUrl)
            .putString(KEY_SYNC_CODE, cleanCode)
            .putString(KEY_AUTH_SECRET, authSecret.trim())
            .putBoolean(KEY_AUTO_SYNC, autoSync)
            .apply()
    }

    private fun sanitizeSyncCode(raw: String): String {
        return raw.trim().replace(Regex("[^a-zA-Z0-9_-]"), "").take(48)
    }

    private fun buildEndpointUrl(firebaseUrl: String, syncCode: String, authSecret: String): String? {
        val base = firebaseUrl.trim().trimEnd('/')
        val code = sanitizeSyncCode(syncCode)
        if (!base.startsWith("https://") || code.length < 3) return null
        val path = "$base/titan_brains/$code.json"
        return if (authSecret.isNotBlank()) "$path?auth=$authSecret" else path
    }

    /**
     * Triggers silent non-blocking cloud backup whenever a memory or character setting changes.
     */
    fun triggerAutoSyncIfConfigured(context: Context) {
        val appCtx = context.applicationContext
        if (!isAutoSyncEnabled(appCtx)) return
        val url = getFirebaseUrl(appCtx)
        if (!url.startsWith("https://")) return
        syncScope.launch {
            backupBrainToFirebase(appCtx)
        }
    }

    /**
     * Uploads the complete Brain snapshot (Identity, Character Lock, Facts, Recent Context) to Firebase.
     */
    suspend fun backupBrainToFirebase(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val fbUrl = getFirebaseUrl(appCtx)
        val syncCode = getOrCreateSyncCode(appCtx)
        val auth = getAuthSecret(appCtx)
        val endpoint = buildEndpointUrl(fbUrl, syncCode, auth)
            ?: return@withContext false to "Valid Firebase URL (https://...firebaseio.com) and Sync Code required."

        try {
            val payload = TitanBrainMemoryEngine.exportFullBrainJson(appCtx)
            payload.put("syncedAt", System.currentTimeMillis())
            payload.put("syncCode", syncCode)

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url(endpoint).put(body).build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putLong(KEY_LAST_SYNC_MS, System.currentTimeMillis()).apply()
                    true to "Brain synced to Cloud ($syncCode)"
                } else {
                    false to "Firebase error HTTP ${resp.code}: Check Database Rules or Auth Secret."
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cloud backup failed: ${e.message}")
            false to "Cloud backup failed: ${e.message}"
        }
    }

    /**
     * Restores the user's complete Assistant & Brain from Firebase using their Firebase URL + Sync Code!
     */
    suspend fun restoreBrainFromFirebase(
        context: Context,
        firebaseUrl: String,
        syncCode: String,
        authSecret: String = ""
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val endpoint = buildEndpointUrl(firebaseUrl, syncCode, authSecret)
            ?: return@withContext false to "Enter a valid Firebase URL (https://...) and Sync Code."

        try {
            val req = Request.Builder().url(endpoint).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext false to "Restore failed (HTTP ${resp.code}). Check URL or permissions."
                }
                val bodyStr = resp.body?.string().orEmpty().trim()
                if (bodyStr.isBlank() || bodyStr == "null") {
                    return@withContext false to "No Brain backup found for code: ${sanitizeSyncCode(syncCode)}"
                }
                val json = JSONObject(bodyStr)
                val restoredCount = TitanBrainMemoryEngine.importFullBrainJson(appCtx, json)
                saveCloudConfig(appCtx, firebaseUrl, syncCode, authSecret, true)
                appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_LAST_SYNC_MS, System.currentTimeMillis()).apply()
                val botName = json.optString("assistantName", "Titan")
                true to "Restored $botName's Brain ($restoredCount memories + character lock)!"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud restore failed", e)
            false to "Restore error: ${e.message}"
        }
    }
}
