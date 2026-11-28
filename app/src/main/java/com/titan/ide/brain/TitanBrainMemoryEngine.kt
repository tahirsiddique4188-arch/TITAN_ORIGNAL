package com.titan.ide.brain

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * TitanBrainMemoryEngine
 * Long-Term Semantic Memory & Adaptive Context Engine for Titan IDE.
 * Features:
 *  - Automatically remembers user instructions, names, favorite voices, preferred languages, and project details.
 *  - Injects contextual memory into Chat, Live Voice, and Video Generation prompts.
 *  - Supports Romanized Hindi/Urdu, Bengali, and English triggers (e.g. "Mara name tahir Hai ya memory ma save karlo").
 *  - Clean UTF-8 without BOM.
 */
object TitanBrainMemoryEngine {

    private const val TAG = "TitanBrainMemory"
    private const val PREFS_NAME = "titan_long_term_brain"
    private const val KEY_FACTS = "brain_facts_json"
    private const val KEY_CACHED_NAME = "brain_cached_user_name"
    private const val MAX_FACTS = 100
    private const val KEY_CROSS_CHAT_LOG = "brain_cross_chat_recent_json"
    private const val MAX_CROSS_CHAT_ITEMS = 14
    private const val KEY_ARCHETYPE = "brain_character_archetype"
    private const val KEY_USER_CALL_NAME = "brain_user_call_name"
    private const val KEY_CUSTOM_PERSONA = "brain_custom_persona_prompt"

    enum class CharacterArchetype(val label: String, val directive: String) {
        JARVIS_PRO(
            "Titan Pro",
            "You are Titan, a loyal, sharp, calm, high-IQ personal AI companion. Be respectful, proactive, concise, and direct. Never lecture, never break character."
        ),
        BEST_FRIEND(
            "Best Friend / Choto Bhai",
            "You are Titan, Tahir's deeply loyal younger brother and closest friend who stays awake beside him 24/7. Speak warmly in his chosen language, care about him so he never feels alone, and execute his tasks super fast."
        ),
        EXECUTIVE(
            "Executive",
            "You are a crisp, hyper-organized executive personal assistant. Keep replies structured, action-oriented, punctual, and zero-fluff."
        ),
        CUSTOM(
            "Custom",
            "Follow the user's custom character definition strictly."
        )
    }

    fun getCharacterArchetype(context: Context): CharacterArchetype {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ARCHETYPE, CharacterArchetype.BEST_FRIEND.name) ?: CharacterArchetype.BEST_FRIEND.name
        return runCatching { CharacterArchetype.valueOf(raw) }.getOrDefault(CharacterArchetype.BEST_FRIEND)
    }

    fun getUserCallName(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_CALL_NAME, "Bhai")?.takeIf { it.isNotBlank() } ?: "Bhai"
    }

    fun getCustomCharacterPrompt(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_PERSONA, "")?.trim().orEmpty()
    }

    fun saveCharacterLock(
        context: Context,
        archetype: CharacterArchetype,
        userCallName: String,
        customPrompt: String
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ARCHETYPE, archetype.name)
            .putString(KEY_USER_CALL_NAME, userCallName.trim().ifBlank { "Bhai" })
            .putString(KEY_CUSTOM_PERSONA, customPrompt.trim())
            .apply()
    }

    fun deleteFactAt(context: Context, index: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY_FACTS, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        val updated = JSONArray()
        for (i in 0 until arr.length()) {
            if (i != index) updated.put(arr.get(i))
        }
        prefs.edit().putString(KEY_FACTS, updated.toString()).apply()
    }

    fun exportFullBrainJson(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val factsArr = try { JSONArray(prefs.getString(KEY_FACTS, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        val recentArr = try { JSONArray(prefs.getString(KEY_CROSS_CHAT_LOG, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        return JSONObject().apply {
            put("assistantName", com.example.voice.TitanAssistantConfig.getAssistantName(context))
            put("userName", getUserName(context) ?: "Tahir")
            put("userCallName", getUserCallName(context))
            put("characterArchetype", getCharacterArchetype(context).name)
            put("customPersonaPrompt", getCustomCharacterPrompt(context))
            put("facts", factsArr)
            put("recentContext", recentArr)
        }
    }

    fun importFullBrainJson(context: Context, json: JSONObject): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val botName = json.optString("assistantName", "").trim()
        if (botName.isNotBlank()) {
            com.example.voice.TitanAssistantConfig.setAssistantName(context, botName)
        }
        val uName = json.optString("userName", "").trim()
        val callName = json.optString("userCallName", "Bhai").trim().ifBlank { "Bhai" }
        val archName = json.optString("characterArchetype", CharacterArchetype.JARVIS_PRO.name)
        val customP = json.optString("customPersonaPrompt", "")
        val factsArr = json.optJSONArray("facts") ?: JSONArray()
        val recentArr = json.optJSONArray("recentContext") ?: JSONArray()

        prefs.edit()
            .putString(KEY_CACHED_NAME, uName.ifBlank { "Tahir" })
            .putString(KEY_USER_CALL_NAME, callName)
            .putString(KEY_ARCHETYPE, archName)
            .putString(KEY_CUSTOM_PERSONA, customP)
            .putString(KEY_FACTS, factsArr.toString())
            .putString(KEY_CROSS_CHAT_LOG, recentArr.toString())
            .apply()

        return factsArr.length()
    }

    data class MemoryFact(
        val fact: String,
        val category: String,
        val timestamp: Long
    )

    fun saveFact(context: Context, fact: String, category: String = "general") {
        val clean = fact.trim()
        if (clean.length < 2) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY_FACTS, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }

        // Deduplicate
        for (i in 0 until arr.length()) {
            val existing = arr.optJSONObject(i)?.optString("fact", "") ?: ""
            if (existing.equals(clean, ignoreCase = true)) return
        }

        val updated = JSONArray()
        updated.put(JSONObject().apply {
            put("fact", clean)
            put("category", category)
            put("ts", System.currentTimeMillis())
        })
        for (i in 0 until arr.length().coerceAtMost(MAX_FACTS - 1)) {
            updated.put(arr.get(i))
        }
        prefs.edit().putString(KEY_FACTS, updated.toString()).apply()
        Log.d(TAG, "Brain remembered: [$category] $clean")
        TitanFirebaseBrainSync.triggerAutoSyncIfConfigured(context)

        // Sync with TitanMemoryManager
        try {
            com.example.data.local.TitanMemoryManager.getInstance(context).saveFact(category, clean)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mirror fact to TitanMemoryManager: ${e.message}")
        }
    }

    fun getAllFacts(context: Context): List<MemoryFact> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY_FACTS, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        val list = mutableListOf<MemoryFact>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                MemoryFact(
                    fact = obj.optString("fact", ""),
                    category = obj.optString("category", "general"),
                    timestamp = obj.optLong("ts", 0L)
                )
            )
        }
        return list
    }

    fun getUserName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_CACHED_NAME, null)
        if (!cached.isNullOrBlank()) return cached

        val facts = getAllFacts(context)
        for (f in facts) {
            if (f.category == "identity" && f.fact.contains("name is", ignoreCase = true)) {
                val name = f.fact.substringAfter("name is").trim()
                if (name.isNotBlank()) {
                    prefs.edit().putString(KEY_CACHED_NAME, name).apply()
                    return name
                }
            }
        }
        val fallback = try {
            com.example.data.local.TitanMemoryManager.getInstance(context).userName
        } catch (e: Exception) {
            "Tahir"
        }
        return if (!fallback.isNullOrBlank()) fallback else "Tahir"
    }

    fun setUserName(context: Context, name: String) {
        val clean = name.trim()
        if (clean.isNotBlank()) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_CACHED_NAME, clean).apply()
            saveFact(context, "User's name is $clean", "identity")
            try {
                com.example.data.local.TitanMemoryManager.getInstance(context).userName = clean
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync user name to TitanMemoryManager: ${e.message}")
            }
        }
    }

    fun isBengali(text: String): Boolean {
        return text.any { it in '\u0980'..'\u09FF' }
    }

    fun isHindiOrHinglish(text: String): Boolean {
        if (text.any { it in '\u0900'..'\u097F' }) return true
        val lower = text.lowercase()
        val hinglishWords = listOf(
            "mara", "mera", "meri", "mere", "hai", "hain", "karo", "karlo", "karna", "kardo",
            "yaad", "naam", "name", "bhai", "kya", "kaise", "batao", "mujhe", "apna", "aapka",
            "rakhna", "rakho", "bolo", "bol", "sun", "suno", "sunao", "dikhaye", "dikhao"
        )
        return hinglishWords.any { Regex("\\b$it\\b").containsMatchIn(lower) }
    }

    fun buildMemoryConfirmation(userMessage: String, extractedName: String?): String {
        return if (!extractedName.isNullOrBlank()) {
            "Saved your name ($extractedName) to memory."
        } else {
            "Saved to memory."
        }
    }

    fun recordCrossChatTurn(context: Context, userMessage: String) {
        val clean = userMessage.replace(Regex("\\s+"), " ").trim().take(140)
        if (clean.length < 4) return
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val arr = try { JSONArray(prefs.getString(KEY_CROSS_CHAT_LOG, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
            if (arr.length() > 0 && arr.optString(0).equals(clean, ignoreCase = true)) return
            val updated = JSONArray()
            updated.put(clean)
            for (i in 0 until arr.length().coerceAtMost(MAX_CROSS_CHAT_ITEMS - 1)) {
                val item = arr.optString(i, "")
                if (item.isNotBlank() && !item.equals(clean, ignoreCase = true)) {
                    updated.put(item)
                }
            }
            prefs.edit().putString(KEY_CROSS_CHAT_LOG, updated.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getMemoryContextBlock(context: Context, maxFacts: Int = 20): String {
        val name = getUserName(context)
        val facts = getAllFacts(context).take(maxFacts)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val recentArr = try { JSONArray(prefs.getString(KEY_CROSS_CHAT_LOG, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        val recentList = mutableListOf<String>()
        for (i in 0 until recentArr.length().coerceAtMost(10)) {
            val s = recentArr.optString(i, "").trim()
            if (s.isNotBlank()) recentList.add(s)
        }
        return buildString {
            val rawBotName = com.example.voice.TitanAssistantConfig.getAssistantName(context)
            val botName = if (rawBotName.isBlank() || rawBotName.equals("Jarvis", ignoreCase = true)) "Titan" else rawBotName
            val arch = getCharacterArchetype(context)
            val callName = getUserCallName(context)
            val customP = getCustomCharacterPrompt(context)
            val personaRule = if (arch == CharacterArchetype.CUSTOM && customP.isNotBlank()) customP else arch.directive
            append("[LOCKED ASSISTANT IDENTITY, CREATOR & BUILT-IN TOOLS]\n")
            append("- Your Name: $botName (Titan IDE — created by Developer Tahir / @Tahirsiddik)\n")
            append("- Address User As: $callName\n")
            append("- Locked Character Style (${arch.label}): $personaRule\n")
            append("- Your Real Built-In Capabilities: Autonomous Android Phone Control & OS Command HUD, Zero-Beep Continuous Phone-Call Live Voice (Offline Whisper STT + 10 Studio Voices), Parallel Multi-Agent WhatsApp/Instagram/Telegram/SMS Auto-Reply (with Relationship Modes & auto language switching), 3D Titan Neural Brain Memory, Dynamic 2D Story Animation Video Studio (auto-generated characters + MP4 export), In-Chat AI Image Creator & Editor, Multi-Speaker Podcast Studio, Mobile Code IDE + Localhost Server, and Live Notification Brain + Telemetry.\n")
            append("- STRICT RULE: Always stay 100% in character as $botName. When asked who you are or what tools/features you have, accurately explain your real Titan IDE tools above. NEVER say 'Jarvis' or 'As an AI language model trained by Google'.\n\n")
            append("[PERSISTENT USER MEMORY ACROSS ALL CHATS]\n")
            if (!name.isNullOrBlank()) append("- User Name: $name\n")
            if (facts.isNotEmpty()) {
                append("- Saved Long-Term Facts:\n")
                facts.forEach { append("  * ${it.fact}\n") }
            }
            if (recentList.isNotEmpty()) {
                append("- Recent Context From Previous Chats (Remember seamlessly even in a new chat):\n")
                recentList.forEach { append("  * $it\n") }
            }
            append("Always use this memory naturally so the user never has to repeat themselves across new chats.\n")
        }
    }

    /**
     * Automatically extracts user preferences, rules, name, project info from every message!
     * Handles Romanized Hindi/Urdu ("Mara name tahir Hai ya memory ma save karlo"), Bengali, and English.
     */
    fun autoLearnFromUserMessage(context: Context, userMessage: String): Pair<Boolean, String?> {
        val clean = userMessage.trim()
        if (clean.length !in 3..400) return false to null
        val lower = clean.lowercase()

        // 1. Multilingual Name Triggers & Extraction
        val namePatterns = listOf(
            Regex("""(?i)(?:mara\s+name|mera\s+name|mara\s+nam|mera\s+nam|mera\s+naam|mara\s+naam|amar\s+nam|amar\s+name|amr\s+nam|my\s+name\s+is|call\s+me)\s*(?:is|hai|hobe|hoy|:)?\s*([a-zA-Z\u0980-\u09FF\u0900-\u097F]+)"""),
            Regex("""(?i)([a-zA-Z\u0980-\u09FF\u0900-\u097F]+)\s+(?:mera\s+name\s+hai|mara\s+name\s+hai|mera\s+naam\s+hai|mara\s+naam\s+hai|amar\s+nam)""")
        )

        for (pattern in namePatterns) {
            val match = pattern.find(clean)
            if (match != null) {
                val candidateName = match.groupValues[1]
                    .replace(Regex("(?i)^(is|hai|ya|me|ma|memory|hobe|hoy)$"), "")
                    .trim()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                if (candidateName.length in 2..30 &&
                    !candidateName.equals("hai", ignoreCase = true) &&
                    !candidateName.equals("memory", ignoreCase = true) &&
                    !candidateName.equals("kardo", ignoreCase = true)
                ) {
                    setUserName(context, candidateName)
                    try {
                        com.example.data.local.TitanMemoryManager.getInstance(context).userName = candidateName
                        com.example.data.local.TitanMemoryManager.getInstance(context).saveFact("User Name", candidateName)
                    } catch (e: Exception) {}
                    Log.d(TAG, "Extracted user name: $candidateName")
                    return true to candidateName
                }
            }
        }

        // 2. Explicit Memory & Instruction Triggers
        val isExplicitMemorySave = lower.contains("memory ma save") ||
                lower.contains("memory me save") ||
                lower.contains("memory te save") ||
                lower.contains("save karlo") ||
                lower.contains("save kar lo") ||
                lower.contains("save in memory") ||
                lower.contains("save to memory") ||
                lower.contains("remember that") ||
                lower.contains("remember this") ||
                lower.contains("don't forget") ||
                lower.contains("mone rakhbe") ||
                lower.contains("mone rekho") ||
                lower.contains("mona rakhba") ||
                lower.contains("yaad rakhna") ||
                lower.contains("yaad rakh") ||
                lower.contains("yaad rakho")

        if (isExplicitMemorySave) {
            val cleanFact = clean
                .replace(Regex("(?i)(ya\\s+memory\\s+ma\\s+save\\s+karlo|memory\\s+me\\s+save\\s+karlo|save\\s+in\\s+memory|save\\s+to\\s+memory|save\\s+karlo|yaad\\s+rakhna|yaad\\s+rakh|mone\\s+rekho|mona\\s+rakhba)"), "")
                .trim()
            if (cleanFact.isNotBlank() && cleanFact.length > 3) {
                saveFact(context, cleanFact, "instruction")
                try {
                    com.example.data.local.TitanMemoryManager.getInstance(context).saveFact("Instruction", cleanFact)
                } catch (e: Exception) {}
                return true to null
            } else {
                saveFact(context, clean, "instruction")
                return true to null
            }
        }

        // 3. User Preferences (Language, Voice, Style, Orb)
        val prefTriggers = listOf(
            "i prefer", "i like", "always use", "never use", "ami chai", "amar pochondo",
            "সবসময়", "কখনোই না", "मुझे पसंद है", "हमेशा", "कभी मत", "amar favorite"
        )
        if (prefTriggers.any { lower.contains(it) }) {
            saveFact(context, clean, "preference")
            return true to null
        }

        // 4. Project & Coding details
        val projectTriggers = listOf(
            "my project is", "i am building", "amar project", "mera project", "मेरा प्रोजेक्ट",
            "we are working on", "titan ide"
        )
        if (projectTriggers.any { lower.contains(it) }) {
            saveFact(context, clean, "project")
            return true to null
        }

        return false to null
    }


    /**
     * Trains Titan's 3D Neural Brain automatically from every completed task or autonomous workflow
     * so repeated daily tasks execute much faster next time.
     */
    fun recordTrainedSkill(
        context: Context,
        userGoal: String,
        actionSteps: String,
        outcomeSummary: String = ""
    ) {
        val cleanGoal = userGoal.trim().take(120)
        val cleanSteps = actionSteps.trim().take(240)
        if (cleanGoal.length < 4 || cleanSteps.isBlank()) return
        val noise = setOf("hi", "hello", "hey", "ok", "okay", "হাই", "হ্যালো", "ঠিক আছে")
        if (cleanGoal.lowercase() in noise) return

        val entry = if (outcomeSummary.isNotBlank()) {
            "SKILL [$cleanGoal] => STEPS: $cleanSteps | RESULT: ${outcomeSummary.take(90)}"
        } else {
            "SKILL [$cleanGoal] => STEPS: $cleanSteps"
        }
        saveFact(context, entry, category = "skill")
    }

    /**
     * Looks up a previously trained skill workflow in Titan's Neural Brain for instant fast-path execution.
     */
    fun findMatchingTrainedSkill(context: Context, userGoal: String): String? {
        val goalWords = userGoal.lowercase()
            .split(Regex("[^a-z0-9\u0980-\u09FF\u0900-\u097F]+"))
            .filter { it.length >= 3 }
            .toSet()
        if (goalWords.isEmpty()) return null

        val skills = getAllFacts(context).filter { it.category == "skill" || it.fact.startsWith("SKILL [") }
        var bestFact: String? = null
        var bestScore = 0
        for (sf in skills) {
            val sfLower = sf.fact.lowercase()
            val score = goalWords.count { sfLower.contains(it) }
            if (score >= 2 && score > bestScore) {
                bestScore = score
                bestFact = sf.fact
            }
        }
        return bestFact
    }

    fun clearAllMemory(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
