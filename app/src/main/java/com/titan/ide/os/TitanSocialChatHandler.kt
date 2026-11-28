package com.titan.ide.os

import android.app.KeyguardManager
import android.content.Context
import android.os.Environment
import android.os.PowerManager
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.SenderRole
import com.example.services.TitanNotificationEntry
import com.example.services.TitanNotificationListenerService
import com.example.voice.TitanAssistantConfig
import com.titan.ide.brain.TitanBrainMemoryEngine
import com.titan.ide.network.TitanDirectEngine
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
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class SocialAppOption(
    val packageName: String,
    val displayName: String,
    val extraPackages: List<String> = emptyList()
)

enum class ContactRelationshipMode(val label: String, val promptTone: String) {
    NORMAL(
        "Normal",
        "Reply like a polite, natural, friendly real person. Keep it casual, warm, and short (1-2 sentences)."
    ),
    BEST_FRIEND(
        "Best Friend",
        "Reply like a super close best friend (tui/tumi casual friendly banter, warm, playful, natural human texting)."
    ),
    GIRLFRIEND(
        "Girlfriend",
        "Reply as a loving, affectionate, caring boyfriend texting his girlfriend. Be sweet, romantic, attentive, warm, and completely natural like a real partner."
    ),
    BOYFRIEND(
        "Boyfriend",
        "Reply as a loving, affectionate, caring girlfriend texting her boyfriend. Be sweet, romantic, attentive, warm, and completely natural like a real partner."
    ),
    FAMILY(
        "Family",
        "Reply respectfully, warmly, and lovingly like talking to a close family member."
    ),
    WORK_FORMAL(
        "Work / Boss",
        "Reply professionally, concisely, and respectfully as a work colleague or assistant."
    )
}

data class AutoReplyLogEntry(
    val timestamp: String,
    val appLabel: String,
    val sender: String,
    val incomingText: String,
    val sentReply: String,
    val status: String
)

/**
 * TitanSocialChatHandler v3.1 — Rocket-Speed Parallel Multi-Agent Social Chat Engine
 * Features:
 *  - Unlimited parallel per-contact Coroutine Agents (10 contacts message = 10 agents run simultaneously!)
 *  - 1.1s Smart Burst Collector (batches rapid multi-messages and replies at rocket speed)
 *  - Zero Self-Reply Loop (blocks "You"/"Me" + tracks recently sent replies for 90s)
 *  - Ironclad Language Match (NEVER randomly replies in Marathi; defaults to Bengali/Banglish for short greetings)
 *  - Per-Contact Relationship Modes (Normal, Best Friend, Girlfriend, Boyfriend, Family, Work)
 *  - Screen-Off PARTIAL_WAKE_LOCK support + Smart Hybrid Voice-Note Reader
 */
object TitanSocialChatHandler {

    private const val TAG = "TitanChatHandler"
    private const val PREFS_NAME = "titan_social_chat_handler"
    private const val KEY_ENABLED = "handler_enabled"
    private const val KEY_IGNORE_GROUPS = "ignore_groups"
    private const val KEY_CUSTOM_STYLE = "custom_reply_style"
    private const val KEY_ALLOWED_PACKAGES = "allowed_packages_set"
    private const val KEY_CONTACT_MODES = "contact_modes_json"
    private const val KEY_VOICE_NOTE_MODE = "voice_note_hybrid_enabled"

    val SUPPORTED_APPS = listOf(
        SocialAppOption("com.whatsapp", "WhatsApp"),
        SocialAppOption("com.whatsapp.w4b", "WhatsApp Business"),
        SocialAppOption("com.instagram.android", "Instagram DM"),
        SocialAppOption("com.facebook.orca", "Messenger", listOf("com.facebook.mlite")),
        SocialAppOption("org.telegram.messenger", "Telegram", listOf("org.telegram.messenger.web", "org.thunderdog.challegram")),
        SocialAppOption("com.google.android.apps.messaging", "Messages (SMS)", listOf("com.samsung.android.messaging", "com.android.mms")),
        SocialAppOption("com.snapchat.android", "Snapchat"),
        SocialAppOption("com.twitter.android", "X (Twitter)")
    )

    private val SELF_SENDER_NAMES = setOf(
        "you", "me", "yourself", "আপনি", "আমি", "आप", "मैं"
    )

    private val SENSITIVE_KEYWORDS = listOf(
        "otp", "verification code", "verify", "cvv", "pin", "password", "passcode",
        "upi", "gpay", "phonepe", "paytm", "bank", "account number", "ifsc",
        "transfer", "send money", "taka", "টাকা", "rupees", "rs.", "loan", "urgent money"
    )

    // Unlimited parallel worker pool on Dispatchers.IO
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Per-contact parallel state
    private val contactHistory = ConcurrentHashMap<String, MutableList<Pair<String, String>>>()
    private val contactLanguageOverride = ConcurrentHashMap<String, String>()
    private val pendingBursts = ConcurrentHashMap<String, MutableList<String>>()
    private val activeAgentJobs = ConcurrentHashMap<String, Job>()
    private val latestEntryByContact = ConcurrentHashMap<String, TitanNotificationEntry>()
    private val recentlySentReplies = ConcurrentHashMap<String, Long>()
    private val lastProcessedMsgByContact = ConcurrentHashMap<String, String>()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _ignoreGroups = MutableStateFlow(true)
    val ignoreGroups: StateFlow<Boolean> = _ignoreGroups.asStateFlow()

    private val _voiceNoteHybridEnabled = MutableStateFlow(true)
    val voiceNoteHybridEnabled: StateFlow<Boolean> = _voiceNoteHybridEnabled.asStateFlow()

    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()

    private val _customStyle = MutableStateFlow("")
    val customStyle: StateFlow<String> = _customStyle.asStateFlow()

    private val _contactModes = MutableStateFlow<Map<String, ContactRelationshipMode>>(emptyMap())
    val contactModes: StateFlow<Map<String, ContactRelationshipMode>> = _contactModes.asStateFlow()

    private val _activeAgentCount = MutableStateFlow(0)
    val activeAgentCount: StateFlow<Int> = _activeAgentCount.asStateFlow()

    private val _replyLogs = MutableStateFlow<List<AutoReplyLogEntry>>(emptyList())
    val replyLogs: StateFlow<List<AutoReplyLogEntry>> = _replyLogs.asStateFlow()

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isEnabled.value = prefs.getBoolean(KEY_ENABLED, false)
        _ignoreGroups.value = prefs.getBoolean(KEY_IGNORE_GROUPS, true)
        _voiceNoteHybridEnabled.value = prefs.getBoolean(KEY_VOICE_NOTE_MODE, true)
        _customStyle.value = prefs.getString(KEY_CUSTOM_STYLE, "") ?: ""
        _selectedPackages.value = prefs.getStringSet(KEY_ALLOWED_PACKAGES, emptySet())?.toSet() ?: emptySet()

        val modesRaw = prefs.getString(KEY_CONTACT_MODES, "{}") ?: "{}"
        val parsedModes = mutableMapOf<String, ContactRelationshipMode>()
        try {
            val obj = JSONObject(modesRaw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k, ContactRelationshipMode.NORMAL.name)
                parsedModes[k] = runCatching { ContactRelationshipMode.valueOf(v) }
                    .getOrDefault(ContactRelationshipMode.NORMAL)
            }
        } catch (_: Exception) {}
        _contactModes.value = parsedModes
        loaded = true
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        ensureLoaded(context)
        _isEnabled.value = enabled
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setIgnoreGroups(context: Context, ignore: Boolean) {
        ensureLoaded(context)
        _ignoreGroups.value = ignore
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_IGNORE_GROUPS, ignore).apply()
    }

    fun setVoiceNoteHybridEnabled(context: Context, enabled: Boolean) {
        ensureLoaded(context)
        _voiceNoteHybridEnabled.value = enabled
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_VOICE_NOTE_MODE, enabled).apply()
    }

    fun setCustomStyle(context: Context, style: String) {
        ensureLoaded(context)
        val clean = style.trim().take(240)
        _customStyle.value = clean
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CUSTOM_STYLE, clean).apply()
    }

    fun setContactMode(context: Context, contactName: String, mode: ContactRelationshipMode) {
        ensureLoaded(context)
        val clean = contactName.trim()
        if (clean.isBlank()) return
        val updated = _contactModes.value.toMutableMap()
        updated[clean] = mode
        _contactModes.value = updated
        persistContactModes(context, updated)
    }

    fun removeContactMode(context: Context, contactName: String) {
        ensureLoaded(context)
        val updated = _contactModes.value.toMutableMap()
        updated.remove(contactName)
        _contactModes.value = updated
        persistContactModes(context, updated)
    }

    private fun persistContactModes(context: Context, map: Map<String, ContactRelationshipMode>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v.name) }
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CONTACT_MODES, obj.toString()).apply()
    }

    fun resolveModeForSender(sender: String): ContactRelationshipMode {
        val clean = sender.trim()
        val exact = _contactModes.value.entries.firstOrNull { it.key.equals(clean, ignoreCase = true) }?.value
        if (exact != null) return exact
        val partial = _contactModes.value.entries.firstOrNull {
            clean.contains(it.key, ignoreCase = true) || it.key.contains(clean, ignoreCase = true)
        }?.value
        return partial ?: ContactRelationshipMode.NORMAL
    }

    fun toggleAppSelection(context: Context, app: SocialAppOption) {
        ensureLoaded(context)
        val current = _selectedPackages.value.toMutableSet()
        val allPkgs = listOf(app.packageName) + app.extraPackages
        if (current.contains(app.packageName)) {
            current.removeAll(allPkgs.toSet())
        } else {
            current.addAll(allPkgs)
        }
        _selectedPackages.value = current
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_ALLOWED_PACKAGES, current).apply()
    }

    fun isAppSelected(context: Context, packageName: String): Boolean {
        ensureLoaded(context)
        return _selectedPackages.value.contains(packageName)
    }

    fun isSelfEchoOrOwnMessage(sender: String, text: String): Boolean {
        val sLower = sender.trim().lowercase(Locale.ROOT)
        if (sLower in SELF_SENDER_NAMES) return true
        val tNorm = text.trim()
        if (tNorm.isBlank()) return true

        val now = System.currentTimeMillis()
        // Prune expired echoes (> 90s)
        recentlySentReplies.entries.removeIf { now - it.value > 90_000L }
        return recentlySentReplies.keys.any { sent ->
            tNorm.equals(sent, ignoreCase = true) ||
                (sent.length >= 8 && tNorm.contains(sent, ignoreCase = true))
        }
    }

    fun markOwnSentReply(replyText: String) {
        val clean = replyText.trim()
        if (clean.isNotBlank()) {
            recentlySentReplies[clean] = System.currentTimeMillis()
        }
    }

    fun clearLogs() {
        _replyLogs.value = emptyList()
    }

    /**
     * Dispatches a dedicated parallel Coroutine Agent for this contact with a 1.1s Smart Burst Collector.
     * If 10 different people message at once, 10 independent agents execute simultaneously!
     */
    fun onIncomingNotification(context: Context, entry: TitanNotificationEntry, isGroupHint: Boolean) {
        ensureLoaded(context)
        if (!_isEnabled.value) return
        if (!entry.canDirectReply) return
        if (!_selectedPackages.value.contains(entry.packageName)) return

        val sender = entry.title.trim()
        val msg = entry.text.trim()
        if (sender.isBlank() || msg.isBlank()) return

        // Anti-Loop Guard: NEVER reply to "You", "Me", or our own echoed reply!
        if (isSelfEchoOrOwnMessage(sender, msg)) return

        // Group Protection
        val looksLikeGroup = isGroupHint || sender.contains(":") || sender.contains(" messages)")
        if (_ignoreGroups.value && looksLikeGroup) {
            appendLog(entry.appLabel, sender, msg, "-", "Skipped (Group)")
            return
        }

        // Duplicate notification guard for same message text
        val contactId = "${entry.packageName}::$sender"
        if (lastProcessedMsgByContact[contactId] == msg) return
        lastProcessedMsgByContact[contactId] = msg

        // Safety Guard: Never auto-reply to OTP, money, UPI, or banking requests
        val lower = msg.lowercase(Locale.ROOT)
        if (SENSITIVE_KEYWORDS.any { lower.contains(it) }) {
            appendLog(entry.appLabel, sender, msg, "-", "Blocked (Sensitive/Money)")
            return
        }

        // Add message into this contact's Smart Burst Collector
        latestEntryByContact[contactId] = entry
        val burst = pendingBursts.getOrPut(contactId) { mutableListOf() }
        synchronized(burst) {
            if (burst.lastOrNull() != msg) burst.add(msg)
        }

        // Cancel previous debounce timer for ONLY this contact & launch fresh 1.1s rocket worker
        activeAgentJobs[contactId]?.cancel()
        val appCtx = context.applicationContext
        val job = scope.launch {
            try {
                // 1.1s Smart Burst Window: collects rapid back-to-back messages cleanly
                delay(1100L)
                _activeAgentCount.value = activeAgentJobs.size

                val batchedMessages = synchronized(burst) {
                    val copy = burst.toList()
                    burst.clear()
                    copy
                }
                if (batchedMessages.isEmpty()) return@launch
                val activeEntry = latestEntryByContact[contactId] ?: entry
                var combinedIncoming = batchedMessages.joinToString("\n")

                // Acquire brief PARTIAL_WAKE_LOCK so screen-off background replies execute at full speed
                val pm = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = try {
                    pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanIDE:ChatAgent")?.apply {
                        acquire(15_000L)
                    }
                } catch (_: Exception) { null }

                try {
                    // Smart Hybrid Voice-Note Support: if incoming is a WhatsApp Voice Note, read latest .opus file!
                    val isVoiceNote = lower.contains("voice message") || lower.contains("audio") || combinedIncoming.contains("🎤")
                    if (isVoiceNote && _voiceNoteHybridEnabled.value) {
                        val transcript = tryReadLatestWhatsAppVoiceNote(appCtx)
                        if (!transcript.isNullOrBlank()) {
                            combinedIncoming = "[Voice Note Transcript]: $transcript"
                        }
                    }

                    val relMode = resolveModeForSender(sender)
                    val historyList = contactHistory.getOrPut(contactId) { mutableListOf() }
                    val ownerName = TitanBrainMemoryEngine.getUserName(appCtx) ?: "Tahir"
                    val defaultLang = TitanAssistantConfig.getSelectedLanguage(appCtx).displayName.ifBlank { "Bengali" }
                    val extraStyle = _customStyle.value.takeIf { it.isNotBlank() } ?: ""

                    // Smart Per-Contact Language Detector & Explicit Switch Handler
                    val detectedLang = detectReplyLanguage(combinedIncoming, contactId, defaultLang)
                    if (detectedLang.second) {
                        // Explicit language switch requested: clear old-language history so previous script does not leak
                        synchronized(historyList) { historyList.clear() }
                    }
                    val requiredLangRule = detectedLang.first
                    val historySnippet = synchronized(historyList) {
                        historyList.takeLast(6).joinToString("\n") { (who, txt) -> "$who: $txt" }
                    }

                    val prompt = buildString {
                        appendLine("[NEW INDEPENDENT CHAT SESSION]")
                        appendLine("STRICT REQUIRED REPLY LANGUAGE: $requiredLangRule")
                        appendLine("You are replying on ${activeEntry.appLabel} on behalf of $ownerName to $sender.")
                        appendLine("RELATIONSHIP MODE (${relMode.label}): ${relMode.promptTone}")
                        if (extraStyle.isNotBlank()) appendLine("OWNER CUSTOM STYLE: $extraStyle")
                        appendLine("IRONCLAD LANGUAGE RULES (MUST OBEY 100%):")
                        appendLine("- ALWAYS write your ENTIRE reply strictly in: $requiredLangRule")
                        appendLine("- If the sender asked to switch language (e.g. Hindi, English, Bengali), IMMEDIATELY reply in that NEW requested language itself — NEVER confirm in Bengali when they asked for Hindi or English!")
                        appendLine("- Match script naturally: Hinglish -> Hinglish (Roman Hindi), Devanagari Hindi -> Hindi, English -> English, Banglish -> Banglish, Bengali script -> Bengali.")
                        appendLine("- HARD BAN: NEVER reply in Marathi, Gujarati, Tamil, or any unrequested language!")
                        appendLine("- Keep reply super crisp, natural, human (1-2 short sentences). NO markdown, NO quotes, NEVER say you are an AI.")
                        if (historySnippet.isNotBlank()) {
                            appendLine("Recent chat with $sender:")
                            appendLine(historySnippet)
                        }
                        appendLine("Latest message(s) from $sender:")
                        appendLine(combinedIncoming)
                        append("Reply:")
                    }

                    val generated = withTimeoutOrNull(7500L) {
                        TitanDirectEngine.executeChat(
                            context = appCtx,
                            modelId = "gemini-3.5-flash-lite",
                            messages = listOf(ChatMessage(sender = SenderRole.USER, content = prompt))
                        ) {}
                    }?.trim()?.trim('"')?.take(240).orEmpty()

                    if (generated.isBlank() || generated.contains("Network unreachable", true) || generated.contains("Error", true)) {
                        appendLog(activeEntry.appLabel, sender, combinedIncoming, "-", "Skipped (Offline)")
                        return@launch
                    }

                    // Register own reply BEFORE sending so WhatsApp's instant echo is 100% blocked!
                    markOwnSentReply(generated)

                    val sendResult = TitanNotificationListenerService.replyToNotification(appCtx, activeEntry.key, generated)
                    val success = sendResult.startsWith("Replied", ignoreCase = true)
                    if (success) {
                        synchronized(historyList) {
                            historyList.add(sender to combinedIncoming)
                            historyList.add(ownerName to generated)
                            if (historyList.size > 16) {
                                historyList.subList(0, historyList.size - 16).clear()
                            }
                        }
                        TitanBrainMemoryEngine.recordTrainedSkill(
                            context = appCtx,
                            userGoal = "Auto-chat (${relMode.label}) with $sender on ${activeEntry.appLabel}",
                            actionSteps = "PARALLEL_CHAT_AGENT(${activeEntry.appLabel}, $sender, ${relMode.label})",
                            outcomeSummary = generated.take(70)
                        )
                        val km = appCtx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        val screenLocked = (km?.isKeyguardLocked == true) || (pm?.isInteractive == false)
                        val statusTag = when {
                            isVoiceNote && screenLocked -> "SENT (LOCKED-TEXT)"
                            isVoiceNote -> "SENT (VOICE-HYBRID)"
                            else -> "SENT (${relMode.label})"
                        }
                        appendLog(activeEntry.appLabel, sender, combinedIncoming, generated, statusTag)
                    } else {
                        appendLog(activeEntry.appLabel, sender, combinedIncoming, generated, "Failed")
                    }
                } finally {
                    try { if (wakeLock?.isHeld == true) wakeLock.release() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Parallel agent error for $sender: ${e.message}")
            } finally {
                activeAgentJobs.remove(contactId)
                _activeAgentCount.value = activeAgentJobs.size
            }
        }
        activeAgentJobs[contactId] = job
        _activeAgentCount.value = activeAgentJobs.size
    }

    /**
     * Scans WhatsApp's local Voice Notes folders for a voice note received within the last 45 seconds
     * and transcribes it using Titan's zero-API-key audio transcription pipeline.
     */
    private suspend fun tryReadLatestWhatsAppVoiceNote(context: Context): String? {
        return try {
            val ext = Environment.getExternalStorageDirectory()
            val candidateDirs = listOf(
                File(ext, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"),
                File(ext, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes"),
                File(ext, "WhatsApp/Media/WhatsApp Voice Notes")
            )
            val now = System.currentTimeMillis()
            var newestFile: File? = null
            var newestTime = 0L
            for (rootDir in candidateDirs) {
                if (!rootDir.exists() || !rootDir.canRead()) continue
                val subDirs = rootDir.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.lastModified() }?.take(2) ?: continue
                for (sub in subDirs) {
                    sub.listFiles()?.forEach { f ->
                        if (f.isFile && (f.name.endsWith(".opus", true) || f.name.endsWith(".ogg", true) || f.name.endsWith(".m4a", true))) {
                            val lm = f.lastModified()
                            if (now - lm <= 45_000L && lm > newestTime && f.length() in 500L..2_500_000L) {
                                newestTime = lm
                                newestFile = f
                            }
                        }
                    }
                }
            }
            val target = newestFile ?: return null
            val bytes = target.readBytes()
            withTimeoutOrNull(6000L) {
                TitanDirectEngine.uploadWavAndTranscribeViaPsid(context, bytes)
            }?.trim()?.takeIf { it.isNotBlank() && !it.startsWith("Error", true) }
        } catch (_: Exception) {
            null
        }
    }

    private fun appendLog(appLabel: String, sender: String, incoming: String, reply: String, status: String) {
        val ts = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val item = AutoReplyLogEntry(ts, appLabel, sender, incoming.take(100), reply.take(120), status)
        _replyLogs.value = (listOf(item) + _replyLogs.value).take(40)
    }

    /**
     * Detects the exact required reply language for a contact message.
     * Returns Pair(languageInstruction, didExplicitlySwitchLanguage).
     */
    private fun detectReplyLanguage(
        incoming: String,
        contactId: String,
        defaultLang: String
    ): Pair<String, Boolean> {
        val text = incoming.trim()
        val low = text.lowercase()
        val tokens = low.split(Regex("[^a-z0-9\u0980-\u09FF\u0900-\u097F]+")).filter { it.isNotBlank() }.toSet()

        // 1. Explicit language switch request (handles typos like "hondi", "hindee", "inglish")
        val asksSwitch = low.contains("language") || low.contains("bolo") || low.contains("bol") ||
            low.contains("speak") || low.contains("talk") || low.contains("change") ||
            low.contains("কথা") || low.contains("বলো") || low.contains("बात") || low.contains("बोलो")
        if (asksSwitch || tokens.size <= 3) {
            if (low.contains("hindi") || low.contains("hondi") || low.contains("hindee") || low.contains("hinglish") || low.contains("हिंदी") || low.contains("हिन्दी")) {
                val rule = "NATURAL HINGLISH (Hindi written in English letters, e.g. 'Haan bilkul, ab se Hindi mein baat karte hain! Batao kya haal hai?'). ZERO Bengali words!"
                contactLanguageOverride[contactId] = rule
                return rule to true
            }
            if (low.contains("english") || low.contains("inglish") || low.contains("ইংরেজি") || low.contains("अंग्रेजी")) {
                val rule = "NATURAL ENGLISH ONLY. ZERO Bengali or Hindi words!"
                contactLanguageOverride[contactId] = rule
                return rule to true
            }
            if (low.contains("banglish")) {
                val rule = "NATURAL BANGLISH (Bengali written in English letters, e.g. 'Thik ache, bolo ki khobor?')."
                contactLanguageOverride[contactId] = rule
                return rule to true
            }
            if (low.contains("bangla") || low.contains("bengali") || low.contains("বাংলা")) {
                val rule = "NATURAL BENGALI (বাংলা)."
                contactLanguageOverride[contactId] = rule
                return rule to true
            }
        }

        // 2. Native script detection
        if (text.any { it in '\u0900'..'\u097F' }) {
            val rule = "NATURAL HINDI (हिन्दी). ZERO Bengali words!"
            contactLanguageOverride[contactId] = rule
            return rule to false
        }
        if (text.any { it in '\u0980'..'\u09FF' }) {
            val rule = "NATURAL BENGALI (বাংলা)."
            contactLanguageOverride[contactId] = rule
            return rule to false
        }

        // 3. Hinglish vs Banglish vs English vocabulary detection
        val hinglishWords = setOf(
            "kya", "kar", "raha", "rahi", "rahe", "ho", "hai", "hain", "tum", "aap", "kaise", "kaisa",
            "kahan", "kab", "kyu", "kyun", "nahi", "nhi", "haan", "han", "theek", "thik", "accha",
            "acha", "yaar", "batao", "bata", "mera", "tera", "tumhara", "abhi", "chal", "bhai", "kuch", "mein", "hoon"
        )
        val banglishWords = setOf(
            "kemon", "acho", "aso", "korcho", "korso", "korchi", "ki", "tumi", "ami", "bhalo", "valo",
            "kothay", "bolcho", "khabo", "jabo", "khobor", "hobe", "ache", "ase", "amar", "tomar", "dada", "bondhu", "keno"
        )
        val englishWords = setOf(
            "please", "change", "your", "language", "what", "where", "when", "how", "are", "you",
            "doing", "thanks", "thank", "good", "morning", "night", "sorry", "call", "send", "busy", "free", "need", "help", "can", "will"
        )

        val hiScore = tokens.count { it in hinglishWords }
        val bnScore = tokens.count { it in banglishWords }
        val enScore = tokens.count { it in englishWords }

        if (hiScore >= 2 || (hiScore >= 1 && hiScore > bnScore && hiScore >= enScore)) {
            val rule = "NATURAL HINGLISH (Hindi written in English letters). ZERO Bengali words!"
            contactLanguageOverride[contactId] = rule
            return rule to false
        }
        if (bnScore >= 2 || (bnScore >= 1 && bnScore > hiScore && bnScore >= enScore)) {
            val rule = "NATURAL BANGLISH (Bengali written in English letters)."
            contactLanguageOverride[contactId] = rule
            return rule to false
        }
        if (enScore >= 2) {
            val rule = "NATURAL ENGLISH ONLY. ZERO Bengali or Hindi words!"
            contactLanguageOverride[contactId] = rule
            return rule to false
        }

        // 4. Fall back to contact's saved override if they already established a language, else defaultLang
        val saved = contactLanguageOverride[contactId]
        if (!saved.isNullOrBlank()) return saved to false

        return "NATURAL $defaultLang" to false
    }
}
