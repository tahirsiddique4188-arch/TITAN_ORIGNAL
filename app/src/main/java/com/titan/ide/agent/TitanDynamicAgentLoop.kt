package com.titan.ide.agent

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.SenderRole
import com.example.services.TitanAccessibilityService
import com.example.services.TitanAgentComputerUse
import com.titan.ide.network.TitanDirectEngine
import com.titan.ide.voice.TitanAudioSessionManager
import com.titan.ide.voice.TitanContinuousSpeechEngine
import com.titan.ide.voice.TitanInstantTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.telephony.SmsManager
import com.titan.ide.os.TitanSystemTelemetry
import com.example.services.TitanNotificationListenerService

/**
 * TitanDynamicAgentLoop — Zero-Hardcode Universal Android Autonomous UI Agent
 *
 * Research-backed ReAct + Self-Reflection Architecture (inspired by DroidRun / MobileAgent / MobileUse):
 *  1. ZERO Hardcoded Tasks or App Scripts: Reasons purely from the live pruned Accessibility UI Tree (#1, #2, ...).
 *  2. 25-Step Observe -> Reflect -> Plan -> Act Loop: Handles complex multi-app goals end-to-end.
 *  3. Automatic Stuck-Screen Detection & Recovery: Compares screen tree hashes across steps; if a tap/action
 *     fails to change the screen, automatically prompts the LLM to reflect and try an alternate strategy.
 *  4. Universal OS Primitives Only: OPEN_APP, CLICK_TEXT (supports #index or label + XY fallback), TAP_XY,
 *     LONG_PRESS_XY, SWIPE, TYPE_TEXT, SCROLL_DOWN/UP, PRESS_BACK/HOME/RECENTS, OPEN_NOTIFICATIONS,
 *     OPEN_QUICK_SETTINGS, CALL_NUMBER, OPEN_URL, READ_NOTIFICATIONS, REPLY_NOTIFICATION (target, text), GET_DEVICE_STATUS, SET_BRIGHTNESS (percent), SET_VOLUME (percent, stream), TOGGLE_FLASHLIGHT (enable), TOGGLE_DND (enable), SET_ALARM (hour, minute, message), SEND_SMS (number, message), LOCK_SCREEN, WAIT, DONE.
 *  5. Unified Selected Voice: Speaks updates/completion using the user's selected VoicePersona via TitanInstantTtsEngine.
 */
data class MissionStepLog(
    val step: Int,
    val action: String,
    val thought: String,
    val outcome: String
)

object TitanDynamicAgentLoop {

    private const val TAG = "TitanDynamicAgent"
    private const val MAX_STEPS = 30
    private val agentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isAssistantModeActive = MutableStateFlow(false)
    val isAssistantModeActive: StateFlow<Boolean> = _isAssistantModeActive.asStateFlow()

    private val _isExecutingGoal = MutableStateFlow(false)
    val isExecutingGoal: StateFlow<Boolean> = _isExecutingGoal.asStateFlow()

    private val _statusLog = MutableStateFlow("Autonomous Agent Standby")
    val statusLog: StateFlow<String> = _statusLog.asStateFlow()

    private val _activeSubAgentSummary = MutableStateFlow("")
    val activeSubAgentSummary: StateFlow<String> = _activeSubAgentSummary.asStateFlow()

    private val _missionLogs = MutableStateFlow<List<MissionStepLog>>(emptyList())
    val missionLogs: StateFlow<List<MissionStepLog>> = _missionLogs.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _activeGoalText = MutableStateFlow("")
    val activeGoalText: StateFlow<String> = _activeGoalText.asStateFlow()

    private var activeGoalJob: Job? = null

    fun emergencyStop() {
        activeGoalJob?.cancel()
        activeGoalJob = null
        _isExecutingGoal.value = false
        _statusLog.value = "Mission Aborted by User"
    }

    /**
     * Spawns a NON-BLOCKING Background Sub-Agent so the user can keep talking freely in Live Call
     * while the sub-agent operates the phone with BOTH Accessibility UI Tree + Real Screen Vision Eyes.
     */
    fun dispatchBackgroundSubAgent(
        context: Context,
        userGoal: String,
        onCompleted: ((String) -> Unit)? = null
    ) {
        _activeSubAgentSummary.value = "Sub-Agent running: $userGoal (starting...)"
        executeDynamicGoal(
            context = context.applicationContext,
            userGoal = userGoal,
            speakCompletion = false,
            useVisionEyes = true,
            onGoalFinishedWithResult = { finalMsg ->
                _activeSubAgentSummary.value = ""
                onCompleted?.invoke(finalMsg)
            }
        )
    }

    fun getBackgroundStatusForLiveChat(): String {
        val summary = _activeSubAgentSummary.value
        return if (summary.isNotBlank()) {
            "[BACKGROUND SUB-AGENT LIVE STATUS: $summary | Latest step: ${_statusLog.value}]"
        } else ""
    }

    fun toggleAssistantMode(context: Context) {
        if (_isAssistantModeActive.value) {
            stopAssistantMode()
        } else {
            startAssistantMode(context)
        }
    }

    fun startAssistantMode(context: Context) {
        if (_isAssistantModeActive.value) return
        TitanAudioSessionManager.requestMicOwnership("TitanDynamicAgentLoop")
        _isAssistantModeActive.value = true
        _statusLog.value = "Autonomous Mode Active — Speak any task..."

        TitanInstantTtsEngine.speakWithPersona(
            context = context,
            rawText = "Ready. Tell me what to do on your phone.",
            onDone = {
                if (_isAssistantModeActive.value) {
                    listenForNextDynamicGoal(context)
                }
            }
        )
    }

    fun stopAssistantMode() {
        _isAssistantModeActive.value = false
        _isExecutingGoal.value = false
        activeGoalJob?.cancel()
        activeGoalJob = null
        TitanContinuousSpeechEngine.stopListening()
        TitanAudioSessionManager.releaseMicOwnership("TitanDynamicAgentLoop")
        _statusLog.value = "Autonomous Mode Stopped"
    }

    private fun listenForNextDynamicGoal(context: Context) {
        if (!_isAssistantModeActive.value) return
        TitanContinuousSpeechEngine.startContinuousListening(
            context = context,
            languageTag = "auto",
            pauseOnResultForLiveTurn = true,
            onPartial = { partial ->
                if (_isAssistantModeActive.value && !_isExecutingGoal.value && partial.isNotBlank()) {
                    _statusLog.value = "🗣️ \"$partial\""
                }
            },
            onResult = { goal ->
                if (_isAssistantModeActive.value && goal.isNotBlank()) {
                    executeDynamicGoal(context, goal)
                }
            }
        )
    }

    fun executeDynamicGoal(
        context: Context,
        userGoal: String,
        speakCompletion: Boolean = true,
        onGoalFinished: (() -> Unit)? = null,
        useVisionEyes: Boolean = true,
        onGoalFinishedWithResult: ((String) -> Unit)? = null
    ) {
        activeGoalJob?.cancel()
        _isExecutingGoal.value = true
        _activeGoalText.value = userGoal
        _currentStep.value = 0
        _missionLogs.value = emptyList()
        // Only pause speech engine when running standalone Assistant Mode (NOT when spawned as background sub-agent during Live Call!)
        if (onGoalFinishedWithResult == null) {
            TitanContinuousSpeechEngine.setPaused(true)
        }

        activeGoalJob = agentScope.launch {
            val stepHistory = mutableListOf<String>()
                val trainedShortcut = com.titan.ide.brain.TitanBrainMemoryEngine.findMatchingTrainedSkill(context, userGoal)
            var lastScreenHash = 0
            var consecutiveSameScreen = 0
            var finalReply = "Task completed."

            try {
                // FAST-PATH 0.1s INSTANT HARDWARE & OS REFLEX (Zero LLM wait!)
                val reflex = TitanInstantReflexExecutor.tryExecuteInstant(context, userGoal)
                if (reflex.handled) {
                    _currentStep.value = 1
                    _missionLogs.value = listOf(MissionStepLog(1, reflex.actionLabel, "Instant 0.1s OS Reflex", reflex.replyText))
                    stepHistory.add("${reflex.actionLabel}(Instant)")
                    finalReply = reflex.replyText
                } else {
                for (step in 1..MAX_STEPS) {
                    val svc = TitanAccessibilityService.instance
                    val currentPackage = svc?.rootInActiveWindow?.packageName?.toString().orEmpty()
                    val screenTree = if (svc != null) {
                        TitanAgentComputerUse.getScreenPerceptionPrompt(svc)
                    } else {
                        "[Accessibility Service is OFF — you can still use OPEN_APP, CALL_NUMBER, OPEN_URL. If on-screen tapping is needed, return DONE asking the user to enable Titan Accessibility Service.]"
                    }

                    val screenHash = (currentPackage + "|" + screenTree).hashCode()
                    if (step > 1 && screenHash == lastScreenHash) {
                        consecutiveSameScreen++
                    } else {
                        consecutiveSameScreen = 0
                    }
                    lastScreenHash = screenHash

                    val reflectionWarning = if (consecutiveSameScreen >= 2) {
                        "\n⚠️ REFLECTION ALERT: The screen did NOT change for $consecutiveSameScreen steps! Do NOT repeat the exact same tap/action. Try tapping exact (x,y) coordinates, scrolling, pressing BACK, or another visible element."
                    } else ""

                    val shortPkg = currentPackage.substringAfterLast('.').ifBlank { "OS" }
                    _statusLog.value = "🧠 Step $step/$MAX_STEPS ($shortPkg)"
                    if (_activeSubAgentSummary.value.isNotBlank()) {
                        _activeSubAgentSummary.value = "Working on '$userGoal' — Step $step/$MAX_STEPS ($shortPkg)"
                    }

                    // HYBRID VISUAL EYES: Capture live screenshot via AccessibilityService (API 30+) on Step 1,
                    // whenever UI tree has few nodes (<12 lines), or when stuck >= 1 step!
                    var visualEyesBlock = ""
                    if (useVisionEyes && svc != null && (consecutiveSameScreen >= 1 || screenTree.lines().size < 10)) {
                        try {
                            val b64 = TitanScreenVisionEngine.captureScreenJpegBase64(svc)
                            if (!b64.isNullOrBlank()) {
                                val dm = context.resources.displayMetrics
                                val vis = TitanScreenVisionEngine.describeScreenForGoal(context, b64, userGoal, dm.widthPixels, dm.heightPixels)
                                if (vis.isNotBlank()) {
                                    visualEyesBlock = "\nLIVE VISUAL SCREENSHOT ANALYSIS (EYES):\n$vis\n"
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    val agentPrompt = """
                        You are Titan Universal Autonomous Android Agent. You have ZERO hardcoded scripts — reason dynamically from the live Accessibility UI tree to complete ANY user goal on the phone.
                        USER GOAL: "$userGoal"
                        ${if (!trainedShortcut.isNullOrBlank()) "TRAINED NEURAL BRAIN FAST-PATH: $trainedShortcut" else ""}
                        STEP: $step of $MAX_STEPS
                        ACTIVE APP PACKAGE: ${currentPackage.ifBlank { "unknown" }}
                        HISTORY: ${stepHistory.takeLast(8).joinToString(" -> ").ifBlank { "None" }}$reflectionWarning

                        LIVE SCREEN ELEMENTS (#index, Type, Label, Center XY):
                        $screenTree$visualEyesBlock

                        Pick the SINGLE NEXT atomic action to advance toward the goal.
                        Universal JSON Actions:
                        1. {"action":"OPEN_APP","app":"app name or package"}
                        2. {"action":"CLICK_TEXT","target":"#index or visible text/content-desc"}
                        3. {"action":"TAP_XY","x":540,"y":1200}
                        4. {"action":"LONG_PRESS_XY","x":540,"y":1200}
                        5. {"action":"SWIPE","x1":540,"y1":1600,"x2":540,"y2":500,"ms":300}
                        6. {"action":"TYPE_TEXT","text":"text to type into focused or editable field"}
                        7. {"action":"SCROLL_DOWN"} or {"action":"SCROLL_UP"}
                        8. {"action":"PRESS_BACK"} or {"action":"PRESS_HOME"} or {"action":"PRESS_RECENTS"}
                        9. {"action":"OPEN_NOTIFICATIONS"} or {"action":"OPEN_QUICK_SETTINGS"}
                        10. {"action":"CALL_NUMBER","number":"+91..."}
                        11. {"action":"OPEN_URL","url":"https://..."}
                        12. {"action":"READ_NOTIFICATIONS"} or {"action":"REPLY_NOTIFICATION","target":"sender","text":"reply"}
                        13. {"action":"GET_DEVICE_STATUS"}
                        14. {"action":"SET_BRIGHTNESS","percent":50} or {"action":"SET_VOLUME","percent":60,"stream":"media"}
                        15. {"action":"TOGGLE_FLASHLIGHT","enable":true} or {"action":"TOGGLE_DND","enable":true}
                        16. {"action":"SET_ALARM","hour":7,"minute":0,"message":"Wake up"}
                        17. {"action":"SEND_SMS","number":"+91...","message":"text"}
                        18. {"action":"LOCK_SCREEN"}
                        19. {"action":"WAIT","ms":1000}
                        20. {"action":"DONE","reply":"short natural confirmation in the user's exact language"}

                        Reply ONLY with one valid JSON object. No markdown, no extra text.
                    """.trimIndent()

                    val aiRaw = TitanDirectEngine.executeChat(
                        context = context,
                        modelId = "gemini-3.5-flash-lite",
                        messages = listOf(ChatMessage(sender = SenderRole.USER, content = agentPrompt))
                    )

                    val json = extractJsonObject(aiRaw)
                    if (json == null) {
                        stepHistory.add("S$step:ParseRetry")
                        if (step >= 3 && stepHistory.takeLast(3).all { it.contains("ParseRetry") }) {
                            finalReply = "Unable to read screen. Please try again."
                            break
                        }
                        delay(500L)
                        continue
                    }

                    val action = json.optString("action", "").uppercase()
                    _statusLog.value = "⚡ Step $step: $action"

                    if (action == "DONE") {
                        finalReply = json.optString("reply", finalReply)
                        break
                    }

                    val outcome = executeSingleAction(context, svc, json)
                    stepHistory.add("$action($outcome)")
                    delay(950L)
                }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Dynamic agent error: ${e.message}", e)
                finalReply = "Task failed: ${e.message}"
            } finally {
                _isExecutingGoal.value = false
                withContext(Dispatchers.Main) {
                    _statusLog.value = "✅ $finalReply"
                    if (stepHistory.isNotEmpty() && !finalReply.startsWith("Task failed")) {
                        com.titan.ide.brain.TitanBrainMemoryEngine.recordTrainedSkill(
                            context = context,
                            userGoal = userGoal,
                            actionSteps = stepHistory.joinToString(" -> "),
                            outcomeSummary = finalReply
                        )
                    }
                    onGoalFinishedWithResult?.invoke(finalReply)
                    if (speakCompletion && finalReply.isNotBlank()) {
                        TitanInstantTtsEngine.speakWithPersona(
                            context = context,
                            rawText = finalReply,
                            onDone = {
                                if (onGoalFinished != null) {
                                    onGoalFinished()
                                } else if (_isAssistantModeActive.value) {
                                    TitanContinuousSpeechEngine.setPaused(false)
                                }
                            }
                        )
                    } else {
                        if (onGoalFinished != null) {
                            onGoalFinished()
                        } else if (_isAssistantModeActive.value) {
                            TitanContinuousSpeechEngine.setPaused(false)
                        }
                    }
                }
            }
        }
    }

    private suspend fun executeSingleAction(
        context: Context,
        svc: TitanAccessibilityService?,
        json: JSONObject
    ): String {
        return when (json.optString("action", "").uppercase()) {
            "OPEN_APP" -> {
                val app = json.optString("app", json.optString("package", ""))
                val ok = TitanAgentComputerUse.launchApp(context, app)
                if (ok) "Opened $app" else (svc?.openApp(app) ?: "App not found: $app")
            }
            "CLICK_TEXT" -> {
                val target = json.optString("target", "")
                if (svc == null) "Accessibility OFF"
                else if (TitanAgentComputerUse.clickNodeSmart(svc, target)) "Clicked $target"
                else svc.clickNodeByTextOrId(target)
            }
            "TAP_XY" -> {
                val x = json.optDouble("x", 0.0).toFloat()
                val y = json.optDouble("y", 0.0).toFloat()
                if (svc == null) "Accessibility OFF"
                else {
                    TitanAgentComputerUse.tapAt(svc, x, y)
                    "Tapped(${x.toInt()},${y.toInt()})"
                }
            }
            "LONG_PRESS_XY" -> {
                val x = json.optDouble("x", 0.0).toFloat()
                val y = json.optDouble("y", 0.0).toFloat()
                if (svc == null) "Accessibility OFF"
                else {
                    TitanAgentComputerUse.longPressAt(svc, x, y)
                    "LongPressed(${x.toInt()},${y.toInt()})"
                }
            }
            "SWIPE" -> {
                val x1 = json.optDouble("x1", 540.0).toFloat()
                val y1 = json.optDouble("y1", 1500.0).toFloat()
                val x2 = json.optDouble("x2", 540.0).toFloat()
                val y2 = json.optDouble("y2", 500.0).toFloat()
                val ms = json.optLong("ms", 300L).coerceIn(100L, 1500L)
                if (svc == null) "Accessibility OFF"
                else {
                    TitanAgentComputerUse.swipe(svc, x1, y1, x2, y2, ms)
                    "Swiped"
                }
            }
            "TYPE_TEXT" -> {
                val txt = json.optString("text", "")
                if (svc == null) "Accessibility OFF"
                else {
                    val ok = TitanAgentComputerUse.typeText(svc, txt) || svc.autoInsertTextIntoFocusedField(txt)
                    if (ok) "Typed" else "No editable field"
                }
            }
            "SCROLL_DOWN" -> {
                svc?.scrollScreen(down = true)
                "ScrolledDown"
            }
            "SCROLL_UP" -> {
                svc?.scrollScreen(down = false)
                "ScrolledUp"
            }
            "PRESS_BACK" -> {
                svc?.triggerGoBack()
                "Back"
            }
            "PRESS_HOME" -> {
                svc?.triggerGoHome()
                "Home"
            }
            "PRESS_RECENTS" -> {
                svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                "Recents"
            }
            "OPEN_NOTIFICATIONS" -> {
                svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                "Notifications"
            }
            "OPEN_QUICK_SETTINGS" -> {
                svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                "QuickSettings"
            }
            "CALL_NUMBER" -> {
                val num = json.optString("number", "").trim()
                if (num.isBlank()) "No number"
                else try {
                    context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    "Calling $num"
                } catch (_: Exception) {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    "Dialer $num"
                }
            }
            "OPEN_URL" -> {
                val url = json.optString("url", "").trim()
                if (url.isBlank()) "No URL"
                else try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    "Opened URL"
                } catch (e: Exception) {
                    "URL error: ${e.message}"
                }
            }
            "READ_NOTIFICATIONS" -> {
                TitanNotificationListenerService.getSummaryForAi(12)
            }
            "REPLY_NOTIFICATION" -> {
                val target = json.optString("target", json.optString("sender", ""))
                val msg = json.optString("text", json.optString("message", ""))
                TitanNotificationListenerService.replyToNotification(context, target, msg)
            }
            "GET_DEVICE_STATUS" -> {
                TitanSystemTelemetry.formatTelemetryForAi(context)
            }
            "SET_BRIGHTNESS" -> {
                val pct = json.optInt("percent", 50)
                TitanSystemTelemetry.setBrightnessPercent(context, pct)
            }
            "SET_VOLUME" -> {
                val pct = json.optInt("percent", 50)
                val stream = json.optString("stream", "media")
                svc?.setVolumeLevel(pct, stream) ?: "Accessibility OFF"
            }
            "TOGGLE_FLASHLIGHT" -> {
                val en = json.optBoolean("enable", true)
                svc?.toggleFlashlight(en)
                if (en) "Flashlight ON" else "Flashlight OFF"
            }
            "TOGGLE_DND" -> {
                val en = json.optBoolean("enable", true)
                TitanSystemTelemetry.toggleDoNotDisturb(context, en)
            }
            "SET_ALARM" -> {
                val h = json.optInt("hour", 7)
                val m = json.optInt("minute", 0)
                val msg = json.optString("message", "Titan Alarm")
                svc?.setAlarm(h, m, msg) ?: "Accessibility OFF"
            }
            "SEND_SMS" -> {
                val num = json.optString("number", "").trim()
                val msg = json.optString("message", json.optString("text", "")).trim()
                if (num.isBlank() || msg.isBlank()) "Missing SMS number or text"
                else try {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault().sendTextMessage(num, null, msg, null, null)
                    "SMS sent to $num"
                } catch (e: Exception) {
                    "SMS error: ${e.message}"
                }
            }
            "LOCK_SCREEN" -> {
                svc?.lockPhone() ?: "Accessibility OFF"
            }
            "WAIT" -> {
                delay(json.optLong("ms", 1000L).coerceIn(300L, 4000L))
                "Waited"
            }
            else -> "Unknown"
        }
    }

    private fun extractJsonObject(raw: String): JSONObject? {
        return try {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start != -1 && end > start) JSONObject(raw.substring(start, end + 1)) else null
        } catch (_: Exception) { null }
    }
}
