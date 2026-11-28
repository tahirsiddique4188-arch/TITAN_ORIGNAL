package com.example.data.model

import java.util.Locale

data class VoicePersona(
    val id: String,
    val name: String,
    val roleTitle: String,
    val gender: String,
    val accent: String,
    val pitchMultiplier: Float,
    val defaultSpeed: Float,
    val description: String,
    val sampleText: String,
    val edgeVoiceEn: String = "en-IN-NeerjaExpressiveNeural",
    val edgeVoiceHi: String = "hi-IN-SwaraNeural",
    val edgeVoiceBn: String = "bn-BD-NabanitaNeural",
    val prosodyRate: String = "+0%",
    val prosodyPitch: String = "+0Hz",
    val characterTag: String = roleTitle,
    val locale: Locale = Locale("bn", "IN")
)

/**
 * Pure Microsoft Edge Neural Voice Roster (Bengali, Hindi & Indian English)
 * Zero artificial pitch/speed distortion (+0% rate, +0Hz pitch) for 100% authentic human speech.
 */
object VoicePersonas {

    // ===== 4 AUTHENTIC EDGE NEURAL FEMALE VOICES (BENGALI / HINDI / INDIAN) =====
    val NABANITA = VoicePersona(
        id = "nabanita",
        name = "Nabanita",
        roleTitle = "Edge Bengali Neural (Warm)",
        gender = "Female",
        accent = "Bengali",
        pitchMultiplier = 1.00f,
        defaultSpeed = 1.00f,
        description = "Natural warm Bengali neural female voice (Default)",
        sampleText = "হ্যালো ভাই, আমি নবনিতা। তোমার সাহায্য করার জন্য আমি সবসময় তৈরি!",
        edgeVoiceBn = "bn-BD-NabanitaNeural",
        edgeVoiceHi = "hi-IN-SwaraNeural",
        edgeVoiceEn = "en-IN-NeerjaExpressiveNeural",
        prosodyRate = "+0%",
        prosodyPitch = "+0Hz",
        locale = Locale("bn", "BD")
    )

    val TANISHAA = VoicePersona(
        id = "tanishaa",
        name = "Tanishaa",
        roleTitle = "Edge Kolkata Bengali",
        gender = "Female",
        accent = "Indian Bengali",
        pitchMultiplier = 1.00f,
        defaultSpeed = 1.00f,
        description = "Clear Kolkata Indian Bengali neural female voice",
        sampleText = "নমস্কার, আমি তানিশা। বলো আজ কী কাজ শুরু করব?",
        edgeVoiceBn = "bn-IN-TanishaaNeural",
        edgeVoiceHi = "hi-IN-SwaraNeural",
        edgeVoiceEn = "en-IN-NeerjaNeural",
        prosodyRate = "+0%",
        prosodyPitch = "+0Hz",
        locale = Locale("bn", "IN")
    )

    val SWARA = VoicePersona(
        id = "swara",
        name = "Swara",
        roleTitle = "Edge Hindi Neural",
        gender = "Female",
        accent = "Hindi",
        pitchMultiplier = 1.00f,
        defaultSpeed = 1.00f,
        description = "Expressive natural Indian Hindi neural female voice",
        sampleText = "नमस्ते भाई, मैं स्वरा हूँ। बताइए आज क्या मदद करूँ?",
        edgeVoiceBn = "bn-IN-TanishaaNeural",
        edgeVoiceHi = "hi-IN-SwaraNeural",
        edgeVoiceEn = "en-IN-NeerjaExpressiveNeural",
        prosodyRate = "+0%",
        prosodyPitch = "+0Hz",
        locale = Locale("hi", "IN")
    )

    val NEERJA = VoicePersona(
        id = "neerja",
        name = "Neerja",
        roleTitle = "Edge Indian Expressive",
        gender = "Female",
        accent = "Indian English",
        pitchMultiplier = 1.00f,
        defaultSpeed = 1.00f,
        description = "Expressive Indian English & multilingual neural voice",
        sampleText = "Hello! I am Neerja, your Titan AI voice assistant.",
        edgeVoiceBn = "bn-BD-NabanitaNeural",
        edgeVoiceHi = "hi-IN-SwaraNeural",
        edgeVoiceEn = "en-IN-NeerjaExpressiveNeural",
        prosodyRate = "+0%",
        prosodyPitch = "+0Hz",
        locale = Locale("en", "IN")
    )

    // ===== 4 AUTHENTIC EDGE NEURAL MALE VOICES (BENGALI / HINDI / INDIAN) =====
    val BASHKAR = VoicePersona(
        id = "bashkar",
        name = "Bashkar",
        roleTitle = "Edge Kolkata Bengali Male",
        gender = "Male",
        accent = "Indian Bengali",
        pitchMultiplier = 1.00f,
        defaultSpeed = 1.00f,
        description = "Natural Kolkata Indian Bengali neural male voice",
        sampleText = "নমস্কার ভাই, আমি ভাস্কর। সব সিস্টেম রেডি আছে, বলো কী করতে হবে?",
        edgeVoiceBn = "bn-IN-BashkarNeural",
        edgeVoiceHi = "hi-IN-MadhurNeural",
        edgeVoiceEn = "en-IN-PrabhatNeural",
        prosodyRate = "+0%",
        prosodyPitch = "+0Hz",
        locale = Locale("bn", "IN")
    )

    val PRADEEP = VoicePersona(
        id = "pradeep",
        name = "Pradeep",
        roleTitle = "Edge Bengali Male (Deep)",
        gender = "Male",
        accent = "Bengali",
        pitchMultiplier = 1.00f,
        defaultSpeed = 1.00f,
        description = "Deep natural Bengali neural male voice",
        sampleText = "হ্যালো ভাই, আমি প্রদীপ। আমি সবসময় তোমার পাশে আছি।",
        edgeVoiceBn = "bn-BD-PradeepNeural",
        edgeVoiceHi = "hi-IN-MadhurNeural",
        edgeVoiceEn = "en-IN-PrabhatNeural",
        prosodyRate = "+0%",
        prosodyPitch = "+0Hz",
        locale = Locale("bn", "BD")
    )

    val MADHUR = VoicePersona(
        id = "madhur",
        name = "Madhur",
        roleTitle = "Edge Hindi Neural Male",
        gender = "Male",
        accent = "Hindi",
        pitchMultiplier = 1.00f,
        defaultSpeed = 1.00f,
        description = "Natural Indian Hindi neural male voice",
        sampleText = "नमस्ते भाई, मैं मधुर हूँ। बताइए क्या काम करना है?",
        edgeVoiceBn = "bn-IN-BashkarNeural",
        edgeVoiceHi = "hi-IN-MadhurNeural",
        edgeVoiceEn = "en-IN-PrabhatNeural",
        prosodyRate = "+0%",
        prosodyPitch = "+0Hz",
        locale = Locale("hi", "IN")
    )

    val PRABHAT = VoicePersona(
        id = "prabhat",
        name = "Prabhat",
        roleTitle = "Edge Indian Male",
        gender = "Male",
        accent = "Indian English",
        pitchMultiplier = 1.00f,
        defaultSpeed = 1.00f,
        description = "Clear crisp Indian English neural male voice",
        sampleText = "Hello! I am Prabhat, ready to assist you anytime.",
        edgeVoiceBn = "bn-BD-PradeepNeural",
        edgeVoiceHi = "hi-IN-MadhurNeural",
        edgeVoiceEn = "en-IN-PrabhatNeural",
        prosodyRate = "+0%",
        prosodyPitch = "+0Hz",
        locale = Locale("en", "IN")
    )

    // Backward-compatible aliases so no existing callers break
    val ROHAN = BASHKAR
    val ARYAN = PRADEEP
    val KABIR = MADHUR
    val DEV = PRABHAT
    val ARJUN = BASHKAR
    val ZARA = NABANITA
    val ANANYA = TANISHAA
    val RIYA = NEERJA
    val NISHA = SWARA

    val DEFAULT_FEMALE = NABANITA
    val DEFAULT_MALE = BASHKAR
    val DEFAULT = NABANITA

    val ALL_PERSONAS: List<VoicePersona> = listOf(
        NABANITA, TANISHAA, SWARA, NEERJA,
        BASHKAR, PRADEEP, MADHUR, PRABHAT
    )

    val SUPPORTED_LANGUAGES = listOf(
        SupportedLanguage("Bengali (BD)", "bn-BD", Locale.Builder().setLanguage("bn").setRegion("BD").build()),
        SupportedLanguage("Bengali (IN)", "bn-IN", Locale.Builder().setLanguage("bn").setRegion("IN").build()),
        SupportedLanguage("Hindi", "hi-IN", Locale.Builder().setLanguage("hi").setRegion("IN").build()),
        SupportedLanguage("English (India)", "en-IN", Locale.Builder().setLanguage("en").setRegion("IN").build())
    )

    fun getById(id: String): VoicePersona {
        val clean = id.trim().lowercase()
        return ALL_PERSONAS.find {
            it.id.equals(clean, ignoreCase = true) ||
                it.name.equals(clean, ignoreCase = true)
        } ?: when (clean) {
            "zara", "emma", "meera", "aoede" -> NABANITA
            "ananya", "kiara", "diya" -> TANISHAA
            "nisha", "tara", "siya" -> SWARA
            "riya", "kore", "alina" -> NEERJA
            "rohan", "arjun", "puck", "aarav" -> BASHKAR
            "aryan", "fenrir" -> PRADEEP
            "kabir", "charon", "vikram" -> MADHUR
            "dev", "orus", "neel" -> PRABHAT
            else -> DEFAULT
        }
    }

    fun getMaleVoices(): List<VoicePersona> = ALL_PERSONAS.filter { it.gender.equals("Male", ignoreCase = true) }
    fun getFemaleVoices(): List<VoicePersona> = ALL_PERSONAS.filter { it.gender.equals("Female", ignoreCase = true) }
}

data class SupportedLanguage(
    val displayName: String,
    val code: String,
    val locale: Locale
)
