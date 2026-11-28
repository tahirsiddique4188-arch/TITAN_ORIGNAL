package com.titan.ide.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.example.services.TitanAccessibilityService
import com.example.services.TitanNotificationListenerService
import com.titan.ide.brain.TitanBrainMemoryEngine
import com.titan.ide.os.TitanSystemTelemetry
import java.util.Locale

data class InstantReflexResult(
    val handled: Boolean,
    val replyText: String = "",
    val actionLabel: String = ""
)

/**
 * TitanInstantReflexExecutor — 0.1-Second Zero-LLM Direct Android OS Reflex Engine
 * Executes common phone-control commands instantaneously (flashlight, brightness, volume, DND,
 * lock screen, open any app, YouTube play, phone call by number/contact, alarm, notifications,
 * device health) with 100% reliability and zero JSON-parse failure.
 */
object TitanInstantReflexExecutor {

    private val APP_ALIASES = mapOf(
        "whatsapp" to "com.whatsapp",
        "হোয়াটসঅ্যাপ" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "ইউটিউব" to "com.google.android.youtube",
        "instagram" to "com.instagram.android",
        "ইনস্টাগ্রাম" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "ফেসবুক" to "com.facebook.katana",
        "messenger" to "com.facebook.orca",
        "telegram" to "org.telegram.messenger",
        "chrome" to "com.android.chrome",
        "ক্রোম" to "com.android.chrome",
        "camera" to "camera",
        "ক্যামেরা" to "camera",
        "settings" to "com.android.settings",
        "সেটিংস" to "com.android.settings",
        "gallery" to "gallery",
        "গ্যালারি" to "gallery",
        "play store" to "com.android.vending",
        "maps" to "com.google.android.apps.maps",
        "gmail" to "com.google.android.gm",
        "spotify" to "com.spotify.music",
        "termux" to "com.termux"
    )

    /**
     * Returns true if the user prompt clearly asks Titan to perform an autonomous phone/device action.
     */
    fun looksLikePhoneActionIntent(prompt: String): Boolean {
        val q = prompt.trim().lowercase(Locale.ROOT)
        if (q.length < 3) return false
        val triggers = listOf(
            "flashlight", "torch", "টর্চ", "ফ্ল্যাশলাইট",
            "brightness", "ব্রাইটনেস", "আলো কমাও", "আলো বাড়াও", "আলো বাড়াও",
            "volume", "ভলিউম", "সাউন্ড", "mute", "silent", "সাইলেন্ট",
            "do not disturb", "dnd", "ডু নট ডিস্টার্ব",
            "lock phone", "lock screen", "ফোন লক", "স্ক্রিন লক",
            "open ", "খোলো", "খুলে দাও", "চালু করো", "on koro", "off koro",
            "call ", "কল করো", "ফোন করো",
            "alarm", "অ্যালার্ম",
            "play ", "গান চালাও", "ভিডিও চালাও",
            "whatsapp-এ", "whatsapp e", "হোয়াটসঅ্যাপে", "মেসেজ পাঠাও", "message pathao", "send message",
            "read notification", "নোটিফিকেশন পড়", "নোটিফিকেশন পড়", "মেসেজ পড়ে শোনাও",
            "battery status", "device status", "ফোনের হেলথ", "ব্যাটারি কত"
        )
        return triggers.any { q.contains(it) }
    }

    fun tryExecuteInstant(context: Context, rawPrompt: String): InstantReflexResult {
        val q = rawPrompt.trim().lowercase(Locale.ROOT)
        val svc = TitanAccessibilityService.instance

        // 1. Flashlight / Torch
        if (q.contains("torch") || q.contains("flashlight") || q.contains("টর্চ") || q.contains("ফ্ল্যাশলাইট")) {
            val turnOff = q.contains("off") || q.contains("বন্ধ") || q.contains("bondho") || q.contains("নিভাও")
            val enable = !turnOff
            val toggledDirect = try {
                if (svc != null) {
                    svc.toggleFlashlight(enable)
                    true
                } else {
                    val cm = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
                    val camId = cm?.cameraIdList?.firstOrNull { id ->
                        cm.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }
                    if (camId != null) {
                        cm.setTorchMode(camId, enable)
                        true
                    } else false
                }
            } catch (_: Exception) { false }
            if (toggledDirect) {
                recordSkill(context, rawPrompt, "FLASHLIGHT($enable)")
                val reply = if (enable) "ফ্ল্যাশলাইট অন করে দিয়েছি ভাই।" else "ফ্ল্যাশলাইট বন্ধ করে দিয়েছি ভাই।"
                return InstantReflexResult(true, reply, "TOGGLE_FLASHLIGHT")
            }
        }

        // 2. Brightness
        if (q.contains("brightness") || q.contains("ব্রাইটনেস")) {
            val num = Regex("\\d+").find(q)?.value?.toIntOrNull()
            val pct = when {
                num != null -> num.coerceIn(5, 100)
                q.contains("full") || q.contains("max") || q.contains("ফুল") || q.contains("বেশি") -> 100
                q.contains("low") || q.contains("min") || q.contains("কম") || q.contains("kom") -> 15
                else -> 50
            }
            val res = TitanSystemTelemetry.setBrightnessPercent(context, pct)
            recordSkill(context, rawPrompt, "SET_BRIGHTNESS($pct%)")
            return InstantReflexResult(true, "ব্রাইটনেস $pct% করে দিয়েছি ভাই ($res)।", "SET_BRIGHTNESS")
        }

        // 3. Volume
        if (q.contains("volume") || q.contains("ভলিউম")) {
            val num = Regex("\\d+").find(q)?.value?.toIntOrNull()
            val pct = when {
                num != null -> num.coerceIn(0, 100)
                q.contains("full") || q.contains("max") || q.contains("ফুল") -> 100
                q.contains("mute") || q.contains("zero") || q.contains("বন্ধ") -> 0
                q.contains("low") || q.contains("কম") -> 25
                else -> 60
            }
            val volOk = try {
                if (svc != null) {
                    svc.setVolumeLevel(pct, "media")
                    true
                } else {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    if (am != null) {
                        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                        am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (max * pct / 100).coerceIn(0, max), 0)
                        true
                    } else false
                }
            } catch (_: Exception) { false }
            if (volOk) {
                recordSkill(context, rawPrompt, "SET_VOLUME($pct%)")
                return InstantReflexResult(true, "মিডিয়া ভলিউম $pct% করে দিয়েছি ভাই।", "SET_VOLUME")
            }
        }

        // 4. Do Not Disturb
        if (q.contains("dnd") || q.contains("do not disturb") || q.contains("ডু নট ডিস্টার্ব")) {
            val disable = q.contains("off") || q.contains("বন্ধ") || q.contains("disable")
            val res = TitanSystemTelemetry.toggleDoNotDisturb(context, !disable)
            recordSkill(context, rawPrompt, "TOGGLE_DND(${!disable})")
            return InstantReflexResult(true, "$res।", "TOGGLE_DND")
        }

        // 5. Lock Screen
        if (q.contains("lock phone") || q.contains("lock screen") || q.contains("ফোন লক") || q.contains("স্ক্রিন লক")) {
            if (svc != null) {
                svc.lockPhone()
                recordSkill(context, rawPrompt, "LOCK_SCREEN")
                return InstantReflexResult(true, "ফোন লক করে দিয়েছি ভাই।", "LOCK_SCREEN")
            }
        }

        // 6. Device Health / Telemetry
        if (q.contains("battery status") || q.contains("device status") || q.contains("ফোনের হেলথ") || q.contains("ফোন স্ট্যাটাস")) {
            val report = TitanSystemTelemetry.formatTelemetryForAi(context)
            return InstantReflexResult(true, "ফোনের লাইভ হেলথ রিপোর্ট: $report", "GET_DEVICE_STATUS")
        }

        // 7. Read Unread Notifications
        if (q.contains("read notification") || q.contains("নোটিফিকেশন পড়") || q.contains("নোটিফিকেশন পড়") || q.contains("মেসেজ পড়ে শোনাও")) {
            val summary = TitanNotificationListenerService.getSummaryForAi(8)
            return InstantReflexResult(true, summary, "READ_NOTIFICATIONS")
        }

        // 8. YouTube Play ("play lofi song on youtube" / "youtube-এ অরিজিৎ সিং গান চালাও")
        if ((q.contains("youtube") || q.contains("ইউটিউব")) && (q.contains("play") || q.contains("চালাও") || q.contains("গান") || q.contains("song") || q.contains("video"))) {
            val cleaned = rawPrompt
                .replace(Regex("(?i)(on youtube|in youtube|youtube-এ|youtube e|ইউটিউবে|play|চালাও|দাও|করো)"), "")
                .trim()
                .ifBlank { "trending music" }
            if (svc != null) {
                svc.playYouTube(cleaned)
            } else {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(cleaned)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try { context.startActivity(intent) } catch (_: Exception) {}
            }
            recordSkill(context, rawPrompt, "PLAY_YOUTUBE($cleaned)")
            return InstantReflexResult(true, "YouTube-এ '$cleaned' চালিয়ে দিয়েছি ভাই।", "PLAY_YOUTUBE")
        }

        // 9. Simple Open App ("open whatsapp" / "youtube খোলো") — ONLY when NOT a multi-step message task!
        val isMultiStep = q.contains("message") || q.contains("মেসেজ") || q.contains("send") || q.contains("পাঠাও") || q.contains("search") || q.contains("সার্চ")
        if (!isMultiStep && (q.startsWith("open ") || q.contains("খোলো") || q.contains("খুলে দাও") || q.endsWith(" open koro") || q.endsWith(" kholo"))) {
            for ((alias, pkg) in APP_ALIASES) {
                if (q.contains(alias)) {
                    val launched = if (pkg.contains(".")) {
                        val i = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (i != null) {
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(i)
                            true
                        } else false
                    } else false
                    if (launched || (svc != null && svc.openApp(alias).contains("Opened", true))) {
                        recordSkill(context, rawPrompt, "OPEN_APP($alias)")
                        return InstantReflexResult(true, "$alias ওপেন করে দিয়েছি ভাই।", "OPEN_APP")
                    }
                }
            }
        }

        // 10. Direct Phone Call (Number or Contact Name)
        if (q.startsWith("call ") || q.contains("কল করো") || q.contains("ফোন করো")) {
            val digits = Regex("\\+?\\d{7,15}").find(q)?.value
            val targetNumber = digits ?: run {
                val contactQuery = rawPrompt
                    .replace(Regex("(?i)(call|কল করো|ফোন করো|কে|ke|please|bhai|ভাই)"), "")
                    .trim()
                if (contactQuery.length >= 2) lookupContactPhone(context, contactQuery) else null
            }
            if (!targetNumber.isNullOrBlank()) {
                try {
                    context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$targetNumber")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    recordSkill(context, rawPrompt, "CALL($targetNumber)")
                    return InstantReflexResult(true, "$targetNumber-এ কল লাগিয়ে দিয়েছি ভাই।", "CALL_NUMBER")
                } catch (_: Exception) {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$targetNumber")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    return InstantReflexResult(true, "$targetNumber ডায়ালারে ওপেন করেছি ভাই।", "CALL_DIAL")
                }
            }
        }

        // 11. Set Alarm ("set alarm 6:30" / "সকাল ৭টায় অ্যালার্ম দাও")
        if (q.contains("alarm") || q.contains("অ্যালার্ম")) {
            val hm = Regex("(\\d{1,2})[:.](\\d{2})").find(q)
            val hour = hm?.groupValues?.get(1)?.toIntOrNull() ?: Regex("\\d{1,2}").find(q)?.value?.toIntOrNull()
            val min = hm?.groupValues?.get(2)?.toIntOrNull() ?: 0
            if (hour != null && hour in 0..23 && svc != null) {
                svc.setAlarm(hour, min.coerceIn(0, 59), "Titan Alarm")
                recordSkill(context, rawPrompt, "SET_ALARM($hour:$min)")
                return InstantReflexResult(true, "$hour:${String.format("%02d", min)}-এ অ্যালার্ম সেট করে দিয়েছি ভাই।", "SET_ALARM")
            }
        }

        return InstantReflexResult(false)
    }

    private fun lookupContactPhone(context: Context, nameQuery: String): String? {
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$nameQuery%"),
                null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(1)?.replace(Regex("[^+0-9]"), "") else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun recordSkill(context: Context, goal: String, action: String) {
        try {
            TitanBrainMemoryEngine.recordTrainedSkill(context, goal, action, "Instant 0.1s Reflex")
        } catch (_: Exception) {}
    }
}
