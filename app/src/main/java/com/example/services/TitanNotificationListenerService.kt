package com.example.services

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.SenderRole
import com.example.voice.TitanAssistantConfig
import com.titan.ide.brain.TitanBrainMemoryEngine
import com.titan.ide.network.TitanDirectEngine
import com.titan.ide.os.TitanSocialChatHandler
import com.titan.ide.voice.TitanContinuousSpeechEngine
import com.titan.ide.voice.TitanInstantTtsEngine
import com.titan.ide.voice.TitanLiveCallEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class TitanNotificationEntry(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val canDirectReply: Boolean
)

/**
 * TitanNotificationListenerService v3.1 — Smart Friend Notification Brain + Hands-Free Voice Reply Window
 * 1. Filters out WhatsApp self-echoes ("You"/"Me") & system junk.
 * 2. When Auto-Chat Handler is ON for an app -> dispatches silently to parallel rocket agents (never reads aloud).
 * 3. When Auto-Chat Handler is OFF -> translates & announces the message in user's chosen language AND
 *    automatically opens an 8-second hands-free microphone window so the user can speak a reply immediately!
 */
class TitanNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "TitanNotifListener"
        private const val MAX_BUFFER = 40
        private val notifScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        var instance: TitanNotificationListenerService? = null
            private set

        @Volatile
        var lastAnnouncedKey: String? = null
            private set

        @Volatile
        var lastAnnouncedSender: String = ""
            private set

        @Volatile
        var lastAnnouncedApp: String = ""
            private set

        @Volatile
        private var lastAnnounceTimestamp = 0L

        @Volatile
        private var voiceReplyWindowJob: Job? = null

        private val _notifications = MutableStateFlow<List<TitanNotificationEntry>>(emptyList())
        val notifications: StateFlow<List<TitanNotificationEntry>> = _notifications.asStateFlow()

        private val _isAutoAnnounceEnabled = MutableStateFlow(true)
        val isAutoAnnounceEnabled: StateFlow<Boolean> = _isAutoAnnounceEnabled.asStateFlow()

        private val replyActions = ConcurrentHashMap<String, Notification.Action>()

        private val JUNK_PATTERNS = listOf(
            "checking for new messages",
            "new messages",
            "messages from",
            "chats",
            "backup in progress",
            "ongoing voice call",
            "ongoing video call",
            "whatsapp web is currently active",
            "linked devices",
            "downloading",
            "uploading",
            "sending message"
        )

        private val CANCEL_WORDS = listOf(
            "no", "nope", "cancel", "skip", "leave it", "never mind", "stop",
            "না", "লাগবে না", "থাক", "দরকার নেই", "বাদ দাও", "na", "lagbe na", "thak", "nahi", "mat bhejo"
        )

        fun setAutoAnnounce(enabled: Boolean) {
            _isAutoAnnounceEnabled.value = enabled
        }

        fun isPermissionGranted(context: Context): Boolean {
            return try {
                val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
                val cn = ComponentName(context, TitanNotificationListenerService::class.java).flattenToString()
                flat.contains(cn) || flat.contains(context.packageName)
            } catch (_: Exception) {
                false
            }
        }

        fun getLatestPendingForReply(): String {
            val sender = lastAnnouncedSender.trim()
            if (sender.isBlank()) return ""
            val latest = _notifications.value.firstOrNull { it.key == lastAnnouncedKey } ?: return ""
            return "LATEST INCOMING MESSAGE WAITING FOR REPLY: App=${latest.appLabel}, Sender=${latest.title}, Text=\"${latest.text}\", CanDirectReply=${latest.canDirectReply}"
        }

        fun replyToNotification(context: Context, keyOrSender: String, replyText: String): String {
            val trimmed = replyText.trim()
            if (trimmed.isBlank()) return "Empty reply text"

            val targetKey = when {
                keyOrSender.isNotBlank() && replyActions.containsKey(keyOrSender) -> keyOrSender
                keyOrSender.isNotBlank() -> _notifications.value.firstOrNull {
                    it.canDirectReply && (
                        it.title.contains(keyOrSender, ignoreCase = true) ||
                            it.appLabel.contains(keyOrSender, ignoreCase = true)
                        )
                }?.key
                else -> lastAnnouncedKey ?: _notifications.value.firstOrNull { it.canDirectReply }?.key
            } ?: return "No replyable notification found for '$keyOrSender'"

            val action = replyActions[targetKey] ?: return "Direct reply action expired"
            val remoteInputs = action.remoteInputs ?: return "No RemoteInput field"
            return try {
                TitanSocialChatHandler.markOwnSentReply(trimmed)
                val intent = Intent()
                val bundle = Bundle()
                for (ri in remoteInputs) {
                    bundle.putCharSequence(ri.resultKey, trimmed)
                }
                RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
                action.actionIntent.send(context, 0, intent)
                "Replied directly: '$trimmed'"
            } catch (e: Exception) {
                Log.e(TAG, "Direct reply failed: ${e.message}", e)
                "Reply failed: ${e.message}"
            }
        }

        fun dismissNotificationByKey(key: String) {
            try {
                instance?.cancelNotification(key)
            } catch (_: Exception) {}
            replyActions.remove(key)
            _notifications.value = _notifications.value.filterNot { it.key == key }
        }

        fun getSummaryForAi(limit: Int = 12): String {
            val list = _notifications.value.take(limit)
            if (list.isEmpty()) return "No unread notifications in buffer."
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            return buildString {
                appendLine("LIVE NOTIFICATIONS (${list.size}):")
                list.forEachIndexed { idx, n ->
                    val replyTag = if (n.canDirectReply) " [CAN_REPLY]" else ""
                    appendLine("${idx + 1}. [${n.appLabel} ${fmt.format(Date(n.timestamp))}]$replyTag ${n.title}: ${n.text}")
                }
            }
        }

        private fun sanitizeMessageText(raw: String, langTag: String): String {
            val lower = raw.trim().lowercase(Locale.ROOT)
            val isBn = langTag.startsWith("bn", true) || langTag.isBlank()
            val isHi = langTag.startsWith("hi", true)
            return when {
                lower.contains("photo") || raw.contains("📷") -> if (isBn) "একটা ছবি পাঠিয়েছে" else if (isHi) "ek photo bheja hai" else "sent a photo"
                lower.contains("voice message") || lower.contains("audio") || raw.contains("🎤") -> if (isBn) "একটা ভয়েস মেসেজ পাঠিয়েছে" else if (isHi) "ek voice message bheja hai" else "sent a voice message"
                lower.contains("video") || raw.contains("🎥") -> if (isBn) "একটা ভিডিও পাঠিয়েছে" else if (isHi) "ek video bheja hai" else "sent a video"
                lower.contains("sticker") -> if (isBn) "একটা স্টিকার পাঠিয়েছে" else if (isHi) "ek sticker bheja hai" else "sent a sticker"
                lower.contains("gif") -> if (isBn) "একটা জিআইএফ পাঠিয়েছে" else if (isHi) "ek GIF bheja hai" else "sent a GIF"
                lower.contains("missed voice call") || lower.contains("missed call") -> if (isBn) "মিসড কল দিয়েছে" else if (isHi) "missed call kiya hai" else "missed call"
                else -> raw.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), "").trim()
            }
        }

        private fun buildFallbackFriendlyAnnouncement(entry: TitanNotificationEntry, langTag: String): String {
            val cleanMsg = sanitizeMessageText(entry.text, langTag).take(120)
            val isHi = langTag.startsWith("hi", true)
            val isEn = langTag.startsWith("en", true)
            return when {
                isEn -> "Hey bro, ${entry.title} messaged you on ${entry.appLabel}: $cleanMsg. Want me to send a reply?"
                isHi -> "Bhai, ${entry.appLabel} par ${entry.title} ka message aaya hai: $cleanMsg. Kya reply bhej doon?"
                else -> "ভাই, ${entry.appLabel}-এ ${entry.title} মেসেজ পাঠিয়েছে — $cleanMsg। ওকে কি কোনো রিপ্লাই পাঠাবো?"
            }
        }

        /**
         * Opens an automatic 8-second Hands-Free Voice Reply Window right after announcing a message.
         * Listens to Tahir's spoken reply, cleans out command prefixes, sends via RemoteInput, and confirms!
         */
        private fun openHandsFreeVoiceReplyWindow(
            context: Context,
            entry: TitanNotificationEntry,
            langTag: String
        ) {
            if (!entry.canDirectReply) return
            // Don't hijack mic if user is already in an active Live Call
            if (TitanLiveCallEngine.callState.value != TitanLiveCallEngine.LiveCallState.IDLE) return

            voiceReplyWindowJob?.cancel()
            voiceReplyWindowJob = notifScope.launch {
                var handled = false
                try {
                    TitanContinuousSpeechEngine.startContinuousListening(
                        context = context,
                        languageTag = langTag,
                        pauseOnResultForLiveTurn = true,
                        onPartial = {},
                        onResult = { spoken ->
                            val cleanSpoken = spoken.trim()
                            if (handled || cleanSpoken.length < 2) return@startContinuousListening
                            handled = true
                            TitanContinuousSpeechEngine.stopListening()
                            voiceReplyWindowJob?.cancel()

                            val lower = cleanSpoken.lowercase(Locale.ROOT)
                            if (CANCEL_WORDS.any { lower == it || lower.startsWith("$it ") }) {
                                val ack = if (langTag.startsWith("en", true)) "Okay bro." else "ঠিক আছে ভাই।"
                                TitanInstantTtsEngine.speakWithPersona(context, ack)
                                return@startContinuousListening
                            }

                            // Extract clean reply message to send (strips "haan bolo / reply dao / oke bolo")
                            notifScope.launch {
                                val extractedReply = withTimeoutOrNull(4500L) {
                                    try {
                                        val p = "Extract ONLY the exact short reply message to send to ${entry.title} from this voice command. " +
                                            "Remove helper words like 'haan bolo', 'reply dao', 'oke message kore dao', 'হ্যাঁ বলো', 'ওকে বলো'. " +
                                            "Keep the exact language & meaning. Output ONLY the clean message text, nothing else.\n" +
                                            "Voice command: $cleanSpoken"
                                        TitanDirectEngine.executeChat(
                                            context = context,
                                            modelId = "gemini-3.5-flash-lite",
                                            messages = listOf(ChatMessage(sender = SenderRole.USER, content = p))
                                        ) {}
                                    } catch (_: Exception) { null }
                                }?.trim()?.trim('"')?.takeIf { it.isNotBlank() && !it.contains("Network", true) }
                                    ?: cleanSpoken
                                        .replace(Regex("(?i)^(হ্যাঁ\\s*)?(ওকে\\s*)?(বলো|বলে দাও|রিপ্লাই দাও|মেসেজ করে দাও|haan\\s*bolo|reply\\s*dao|reply\\s*kore\\s*dao|tell\\s*him|tell\\s*her|reply)\\s*"), "")
                                        .trim()
                                        .ifBlank { cleanSpoken }

                                val res = replyToNotification(context, entry.key, extractedReply)
                                val ok = res.startsWith("Replied", ignoreCase = true)
                                if (ok) {
                                    TitanBrainMemoryEngine.recordTrainedSkill(
                                        context = context,
                                        userGoal = "Voice reply to ${entry.title} on ${entry.appLabel}",
                                        actionSteps = "HANDS_FREE_VOICE_REPLY(${entry.appLabel}, ${entry.title})",
                                        outcomeSummary = extractedReply.take(70)
                                    )
                                    val doneTxt = if (langTag.startsWith("en", true)) {
                                        "Sent to ${entry.title}."
                                    } else {
                                        "পাঠিয়ে দিয়েছি ভাই।"
                                    }
                                    TitanInstantTtsEngine.speakWithPersona(context, doneTxt)
                                }
                            }
                        }
                    )
                    // Keep mic open for 8.5 seconds waiting for user's reply, then close cleanly
                    delay(8500L)
                    if (!handled) {
                        TitanContinuousSpeechEngine.stopListening()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Voice reply window error: ${e.message}")
                    TitanContinuousSpeechEngine.stopListening()
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "Titan Live Notification Brain connected")
        try {
            activeNotifications?.forEach { captureNotification(it, announce = false) }
        } catch (e: Exception) {
            Log.w(TAG, "Initial active notifications read failed: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        captureNotification(sbn, announce = _isAutoAnnounceEnabled.value)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        replyActions.remove(sbn.key)
        _notifications.value = _notifications.value.filterNot { it.key == sbn.key }
    }

    private fun captureNotification(sbn: StatusBarNotification, announce: Boolean) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return
        val notif = sbn.notification ?: return
        if ((notif.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = notif.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val rawText = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString()?.trim().orEmpty()
        if (title.isBlank() && rawText.isBlank()) return

        // Anti-Loop Guard: Skip self-sent WhatsApp notification echoes ("You" / "Me" / own sent reply)
        if (TitanSocialChatHandler.isSelfEchoOrOwnMessage(title, rawText)) return

        // Also inspect MessagingStyle last message sender to block self-replies on WhatsApp
        try {
            val msgs = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (!msgs.isNullOrEmpty()) {
                val lastBundle = msgs.lastOrNull() as? Bundle
                val lastSender = lastBundle?.getCharSequence("sender")?.toString()?.trim().orEmpty()
                if (lastSender.isNotBlank() && TitanSocialChatHandler.isSelfEchoOrOwnMessage(lastSender, rawText)) {
                    return
                }
            }
        } catch (_: Exception) {}

        val lowerCombo = "$title $rawText".lowercase(Locale.ROOT)
        if (JUNK_PATTERNS.any { lowerCombo.contains(it) }) return

        val replyAction = notif.actions?.firstOrNull { act ->
            act.remoteInputs != null && act.remoteInputs.isNotEmpty()
        }
        if (replyAction != null) {
            replyActions[sbn.key] = replyAction
        }

        val appLabel = try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }

        val entry = TitanNotificationEntry(
            key = sbn.key,
            packageName = pkg,
            appLabel = appLabel,
            title = title.ifBlank { appLabel },
            text = rawText.take(280),
            timestamp = sbn.postTime,
            canDirectReply = replyAction != null
        )

        val updated = buildList {
            add(entry)
            addAll(_notifications.value.filterNot { it.key == entry.key })
        }.take(MAX_BUFFER)
        _notifications.value = updated

        // 1. If Auto-Chat Handler is ON & this app is selected -> Silent Rocket Parallel Reply (do NOT read aloud!)
        if (announce && replyAction != null) {
            TitanSocialChatHandler.ensureLoaded(applicationContext)
            val isAutoChatHandlingThisApp =
                TitanSocialChatHandler.isEnabled.value &&
                TitanSocialChatHandler.isAppSelected(applicationContext, pkg)
            if (isAutoChatHandlingThisApp) {
                val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
                TitanSocialChatHandler.onIncomingNotification(applicationContext, entry, isGroup)
                return
            }
        }

        // 2. Otherwise, Auto-Translate & Announce in User's Language + Open Hands-Free 8.5s Voice Reply Window!
        val now = System.currentTimeMillis()
        if (announce && (replyAction != null || rawText.contains("OTP", ignoreCase = true)) && (now - lastAnnounceTimestamp > 3500L)) {
            lastAnnounceTimestamp = now
            lastAnnouncedKey = entry.key
            lastAnnouncedSender = entry.title
            lastAnnouncedApp = entry.appLabel

            val ctx = applicationContext
            notifScope.launch {
                val selLang = TitanAssistantConfig.getSelectedLanguage(ctx)
                val targetLangName = if (selLang.promptDirective.isNotBlank()) selLang.displayName else "Bengali"
                val langTag = selLang.bcp47Tag.ifBlank { "bn-IN" }
                val cleanBody = sanitizeMessageText(entry.text, langTag)

                val friendlySpoken = withTimeoutOrNull(4500L) {
                    try {
                        val prompt = "[NEW INDEPENDENT TASK — IGNORE PREVIOUS LANGUAGES]\n" +
                            "You are Titan, Tahir's close personal friend and younger brother. " +
                            "Translate this incoming message into natural, warm spoken $targetLangName in 1 short sentence (NO markdown, NO brackets, NEVER use Marathi unless requested). " +
                            "Tell him who messaged on ${entry.appLabel}, what they said, and ask if he wants to send a reply.\n" +
                            "Sender: ${entry.title}\nMessage: $cleanBody"
                        val aiLine = TitanDirectEngine.executeChat(
                            context = ctx,
                            modelId = "gemini-3.5-flash-lite",
                            messages = listOf(ChatMessage(sender = SenderRole.USER, content = prompt))
                        ) {}
                        aiLine.trim().takeIf { it.length in 8..240 && !it.contains("Network", true) }
                    } catch (_: Exception) {
                        null
                    }
                } ?: buildFallbackFriendlyAnnouncement(entry, langTag)

                try {
                    TitanInstantTtsEngine.speakWithPersona(
                        context = ctx,
                        rawText = friendlySpoken,
                        onDone = {
                            if (entry.canDirectReply) {
                                openHandsFreeVoiceReplyWindow(ctx, entry, langTag)
                            }
                        }
                    )
                } catch (_: Exception) {}
            }
        }
    }
}
