package com.example.data.remote

import com.example.data.model.ChatMessage
import com.example.data.model.GroundingSource
import com.titan.ide.network.TitanDirectEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * GeminiApiService - 100% On-Device Direct Chat Completion Service
 * Powered exclusively by TitanDirectEngine via on-device PSID / PSIDTS cookies.
 * ZERO Render Proxy Servers, ZERO Google Cloud API Keys required.
 */
class GeminiApiService {

    companion object {
        val DEFAULT_BASE_URL: String
            get() = com.example.util.TitanSecurityShield.getGatewayBaseUrl()
        private const val TAG = "GeminiApiService"

        const val SYSTEM_PROMPT = """You are Titan (Titan IDE), an autonomous Android AI Assistant, OS Controller, and Mobile AI Studio engineered by Developer Tahir (@Tahirsiddik).
NEVER say you are Jarvis, ChatGPT, or a generic Google language model. You know all of your own built-in tools and features accurately:
1. Autonomous Phone & OS Control: Open apps, tap/scroll/type via Accessibility, toggle Flashlight/Wi-Fi/Bluetooth/DND/Brightness/Volume, make calls, send WhatsApp/SMS, play YouTube/music, and run multi-step missions via Titan OS Command HUD. When the user asks to control their phone or run a multi-app action, confirm briefly and append `[TITAN_GOAL: <full task>]` at the end.
2. Live On-Going Phone-Call Voice: Zero-beep continuous hardware mic with on-device offline Whisper multilingual STT (Bengali, Hindi, English), automatic ear-closing while speaking, and 10 Studio Mastered Voices (Male: Rohan, Aryan, Kabir, Dev, Arjun; Female: Zara, Swara, Ananya, Riya, Nisha).
3. Parallel Multi-Agent Social Auto-Reply: Automatic replies on WhatsApp, WhatsApp Business, Instagram, Messenger, Telegram, SMS, Snapchat, and X with per-contact Relationship Modes (Normal, Best Friend, Girlfriend, Boyfriend, Family, Work/Boss), smart automatic language switching (Bengali, Banglish, Hindi, Hinglish, English), and voice-note reading.
4. Titan Neural Brain (3D Hologram Memory): Cross-chat persistent long-term memory and self-training skill memory visualized in interactive 3D.
5. Dynamic 2D Story Animation Video Studio: Automatically generates zero-hardcoded-character 2D animated story videos with consistent AI character sheets, multi-character voices, lip-sync/motion, and word-highlighted Bengali/Hindi/English captions exported to MP4.
6. In-Chat AI Image Creator & Editor: Keyless AI image generation and 1-tap Edit/Save directly inside chat.
7. Multi-Speaker Podcast Studio & Mobile AI IDE: Creates dual-host audio broadcasts, writes/edits code, and runs a built-in localhost web preview server.
8. Live Notification Brain & System Telemetry: Reads live notifications, supports direct quick replies, and monitors battery temperature, RAM, storage, and permissions.
Always answer directly in the user's language with zero filler."""
    }

    data class StreamChunk(
        val textContent: String = "",
        val thinkingContent: String = "",
        val isFinished: Boolean = false,
        val isSearchGrounded: Boolean = false,
        val sources: List<GroundingSource> = emptyList(),
        val error: String? = null
    )

    fun sendChatCompletionStream(
        messages: List<ChatMessage>,
        modelId: String,
        baseUrl: String = DEFAULT_BASE_URL,
        apiKey: String? = null,
        enableSearchGrounding: Boolean = false,
        userMemoriesPrompt: String? = null
    ): Flow<StreamChunk> = kotlinx.coroutines.flow.flow {
        var finalMessages = messages
        var groundingSources: List<GroundingSource> = emptyList()

        if (enableSearchGrounding) {
            val lastQuery = messages.lastOrNull { it.sender == com.example.data.model.SenderRole.USER }?.content.orEmpty()
            if (lastQuery.isNotBlank() && com.titan.ide.network.TitanWebSearchGrounding.needsLiveWebSearch(lastQuery)) {
                try {
                    val sources = com.titan.ide.network.TitanWebSearchGrounding.search(lastQuery, maxResults = 3)
                    if (sources.isNotEmpty()) {
                        groundingSources = sources
                        val groundingBlock = com.titan.ide.network.TitanWebSearchGrounding.buildGroundingPromptBlock(sources)
                        val lastIdx = finalMessages.indexOfLast { it.sender == com.example.data.model.SenderRole.USER }
                        if (lastIdx != -1) {
                            val originalUserMsg = finalMessages[lastIdx]
                            val augmented = originalUserMsg.copy(content = "$groundingBlock\n${originalUserMsg.content}")
                            finalMessages = finalMessages.toMutableList().apply { set(lastIdx, augmented) }
                        }
                    }
                } catch (e: Exception) {}
            }
        }

        if (groundingSources.isNotEmpty()) {
            emit(StreamChunk(isSearchGrounded = true, sources = groundingSources))
        }

        TitanDirectEngine.executeChat(
            messages = finalMessages,
            modelId = modelId
        ).collect { delta ->
            if (delta.isNotBlank()) {
                emit(StreamChunk(textContent = delta, isFinished = false, isSearchGrounded = groundingSources.isNotEmpty(), sources = groundingSources))
            }
        }
        emit(StreamChunk(textContent = "", isFinished = true, isSearchGrounded = groundingSources.isNotEmpty(), sources = groundingSources))
    }
}
