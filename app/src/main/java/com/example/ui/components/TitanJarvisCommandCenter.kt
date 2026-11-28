package com.example.ui.components

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.services.TitanNotificationListenerService
import com.titan.ide.agent.TitanDynamicAgentLoop
import com.titan.ide.os.ContactRelationshipMode
import com.titan.ide.os.TitanSocialChatHandler
import com.titan.ide.os.TitanSystemTelemetry
import kotlinx.coroutines.delay

/**
 * TitanJarvisCommandCenter — F.R.I.D.A.Y. Hi-Tech Holographic HUD & J.A.R.V.I.S. Autonomous OS Deck
 */
@Composable
fun TitanJarvisCommandCenter(
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var missionInput by remember { mutableStateOf("") }
    var refreshTick by remember { mutableIntStateOf(0) }
    var replyTargetKey by remember { mutableStateOf<String?>(null) }
    var replyText by remember { mutableStateOf("") }

    val isExecuting by TitanDynamicAgentLoop.isExecutingGoal.collectAsState()
    val statusLog by TitanDynamicAgentLoop.statusLog.collectAsState()
    val missionLogs by TitanDynamicAgentLoop.missionLogs.collectAsState()
    val currentStep by TitanDynamicAgentLoop.currentStep.collectAsState()
    val activeGoalText by TitanDynamicAgentLoop.activeGoalText.collectAsState()

    val notifications by TitanNotificationListenerService.notifications.collectAsState()
    val autoAnnounce by TitanNotificationListenerService.isAutoAnnounceEnabled.collectAsState()
    LaunchedEffect(Unit) {
        TitanSocialChatHandler.ensureLoaded(context)
        com.titan.ide.os.TitanCallAssistantEngine.init(context)
    }
    val callAiEnabled by com.titan.ide.os.TitanCallAssistantEngine.isEnabled.collectAsState()
    val callAutoSec by com.titan.ide.os.TitanCallAssistantEngine.autoAnswerSec.collectAsState()
    val callRules by com.titan.ide.os.TitanCallAssistantEngine.standingRules.collectAsState()
    val callBusySms by com.titan.ide.os.TitanCallAssistantEngine.busySmsText.collectAsState()
    val callLogs by com.titan.ide.os.TitanCallAssistantEngine.callLogs.collectAsState()
    var callRulesInput by remember(callRules) { mutableStateOf(callRules) }
    var callBusySmsInput by remember(callBusySms) { mutableStateOf(callBusySms) }
    val chatHandlerEnabled by TitanSocialChatHandler.isEnabled.collectAsState()
    val chatIgnoreGroups by TitanSocialChatHandler.ignoreGroups.collectAsState()
    val chatSelectedPkgs by TitanSocialChatHandler.selectedPackages.collectAsState()
    val chatCustomStyle by TitanSocialChatHandler.customStyle.collectAsState()
    val chatReplyLogs by TitanSocialChatHandler.replyLogs.collectAsState()
    val contactModes by TitanSocialChatHandler.contactModes.collectAsState()
    val activeAgents by TitanSocialChatHandler.activeAgentCount.collectAsState()
    val voiceNoteHybrid by TitanSocialChatHandler.voiceNoteHybridEnabled.collectAsState()
    var newContactName by remember { mutableStateOf("") }
    var newContactMode by remember { mutableStateOf(ContactRelationshipMode.GIRLFRIEND) }
    var styleDraft by remember(chatCustomStyle) { mutableStateOf(chatCustomStyle) }

    val telemetry by remember(refreshTick) {
        mutableStateOf(TitanSystemTelemetry.captureSnapshot(context))
    }
    val permissions by remember(refreshTick) {
        mutableStateOf(TitanSystemTelemetry.auditAllPermissions(context))
    }
    val grantedCount = remember(permissions) { permissions.count { it.isGranted } }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3500L)
            refreshTick++
        }
    }

    val runtimePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshTick++
    }

    val hudBg = Color(0xFF050A12)
    val hudCard = Color(0xFF0B1524)
    val hudCyan = Color(0xFF00E5FF)
    val hudAmber = Color(0xFFFFB300)
    val hudGreen = Color(0xFF00E676)
    val hudRed = Color(0xFFFF3D57)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = hudBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // TOP TITAN OS HEADER BAR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        JarvisArcReactorMini(
                            isActive = isExecuting,
                            accent = if (isExecuting) hudAmber else hudCyan,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "TITAN OS COMMAND",
                                color = hudCyan,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "$grantedCount/${permissions.size} SYSTEMS ONLINE • ${telemetry.networkLabel}",
                                color = if (grantedCount >= 9) hudGreen else hudAmber,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { refreshTick++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = hudCyan)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // LIVE HARDWARE TELEMETRY STRIP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HudMetricChip(
                        label = "PWR",
                        value = "${telemetry.batteryPercent}%",
                        sub = "${String.format("%.1f", telemetry.batteryTempC)}C",
                        accent = if (telemetry.batteryPercent > 25) hudCyan else hudRed,
                        modifier = Modifier.weight(1f)
                    )
                    HudMetricChip(
                        label = "RAM",
                        value = "${(telemetry.ramUsedMb * 100 / telemetry.ramTotalMb.coerceAtLeast(1))}%",
                        sub = "${telemetry.ramUsedMb / 1024}G/${telemetry.ramTotalMb / 1024}G",
                        accent = hudCyan,
                        modifier = Modifier.weight(1f)
                    )
                    HudMetricChip(
                        label = "DISK",
                        value = "${String.format("%.0f", telemetry.storageFreeGb)}G",
                        sub = "FREE",
                        accent = hudGreen,
                        modifier = Modifier.weight(1f)
                    )
                    HudMetricChip(
                        label = "VOL",
                        value = "${telemetry.mediaVolumePercent}%",
                        sub = telemetry.ringerModeLabel.take(4),
                        accent = hudAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 5-TAB TITAN OS SELECTOR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(hudCard)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        Triple(0, "HUD", Icons.Default.Terminal),
                        Triple(1, "CHAT", Icons.Default.Forum),
                        Triple(2, "NOTIFS", Icons.Default.NotificationsActive),
                        Triple(3, "PERMS", Icons.Default.Security),
                        Triple(4, "CALLS", Icons.Default.Call)
                    )
                    tabs.forEach { (idx, title, icon) ->
                        val active = selectedTab == idx
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (active) hudCyan.copy(alpha = 0.18f) else Color.Transparent)
                                .clickable { selectedTab = idx }
                                .padding(vertical = 7.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = title,
                                tint = if (active) hudCyan else Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = title,
                                color = if (active) hudCyan else Color(0xFF94A3B8),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // TAB CONTENT
                when (selectedTab) {
                    0 -> {
                        // TAB 0: TITAN AUTONOMOUS MISSION HUD
                        Column(modifier = Modifier.weight(1f)) {
                            // Mission Command Input Box
                            Surface(
                                color = hudCard,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, hudCyan.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "AUTONOMOUS MISSION DIRECTIVE",
                                        color = hudCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = missionInput,
                                        onValueChange = { missionInput = it },
                                        placeholder = {
                                            Text(
                                                "Enter a command (e.g. Open YouTube, Send WhatsApp)...",
                                                color = Color(0xFF64748B),
                                                fontSize = 12.sp
                                            )
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = hudCyan,
                                            unfocusedBorderColor = Color(0xFF1E293B),
                                            cursorColor = hudCyan
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 3
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val goal = missionInput.trim()
                                                if (goal.isNotBlank()) {
                                                    TitanDynamicAgentLoop.executeDynamicGoal(
                                                        context = context,
                                                        userGoal = goal,
                                                        speakCompletion = true
                                                    )
                                                    missionInput = ""
                                                }
                                            },
                                            enabled = !isExecuting && missionInput.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = hudCyan,
                                                contentColor = Color(0xFF04101C),
                                                disabledContainerColor = Color(0xFF132C42),
                                                disabledContentColor = Color(0xFF7DD3FC)
                                            ),
                                            border = BorderStroke(1.dp, hudCyan.copy(alpha = 0.45f)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("EXECUTE MISSION", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                        }
                                        if (isExecuting) {
                                            Button(
                                                onClick = { TitanDynamicAgentLoop.emergencyStop() },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = hudRed,
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("ABORT", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick 1-Tap Autonomous Missions
                            val quickMissions = listOf(
                                "System Health Report" to "Check full phone battery temperature, RAM, storage, and network status, then speak a concise Titan diagnostic report.",
                                "Read Notifications" to "Read all recent unread notifications and give me a quick summary of important messages or OTPs.",
                                "Night Stealth Mode" to "Enable Do Not Disturb, set brightness to 15 percent, and set media volume to 20 percent.",
                                "Flashlight ON" to "Turn on the flashlight right now and confirm.",
                                "Flashlight OFF" to "Turn off the flashlight right now and confirm."
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(quickMissions) { (label, prompt) ->
                                    Surface(
                                        color = hudCard,
                                        shape = RoundedCornerShape(20.dp),
                                        border = BorderStroke(1.dp, hudCyan.copy(alpha = 0.25f)),
                                        modifier = Modifier.clickable(enabled = !isExecuting) {
                                            TitanDynamicAgentLoop.executeDynamicGoal(
                                                context = context,
                                                userGoal = prompt,
                                                speakCompletion = true
                                            )
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Bolt, contentDescription = null, tint = hudAmber, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(label, color = Color(0xFFE2E8F0), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Live J.A.R.V.I.S. Execution Telemetry Console
                            Surface(
                                color = Color(0xFF060D18),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, if (isExecuting) hudAmber.copy(alpha = 0.5f) else Color(0xFF1E293B)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isExecuting) "LIVE EXECUTION • STEP $currentStep/30" else "TELEMETRY CONSOLE",
                                            color = if (isExecuting) hudAmber else hudCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = statusLog.take(36),
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (activeGoalText.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "GOAL: $activeGoalText",
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = Color(0xFF1E293B))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (missionLogs.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "TITAN STANDBY\nEnter a directive or tap a quick mission above.",
                                                color = Color(0xFF475569),
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(missionLogs.reversed()) { logItem ->
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(hudCard)
                                                        .padding(9.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = "STEP #${logItem.step} • ${logItem.action}",
                                                            color = hudCyan,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                        Text(
                                                            text = logItem.outcome.take(28),
                                                            color = hudGreen,
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                    if (logItem.thought.isNotBlank()) {
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Text(
                                                            text = logItem.thought,
                                                            color = Color(0xFFCBD5E1),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // TAB 1: SOCIAL MEDIA AUTO-CHAT HANDLER (PER-APP WHITELIST)
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            item {
                                Surface(
                                    color = hudCard,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, if (chatHandlerEnabled) hudGreen.copy(alpha = 0.6f) else hudCyan.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (activeAgents > 0) "AUTO CHAT • $activeAgents AGENTS LIVE" else "AUTO CHAT HANDLER (PARALLEL)",
                                                color = if (chatHandlerEnabled) hudGreen else hudCyan,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "Auto-replies in sender's language ONLY on apps you select below",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Switch(
                                            checked = chatHandlerEnabled,
                                            onCheckedChange = { on ->
                                                if (on && !TitanNotificationListenerService.isPermissionGranted(context)) {
                                                    Toast.makeText(context, "Enable Notification Access first in PERMS tab", Toast.LENGTH_LONG).show()
                                                    TitanSystemTelemetry.openPermissionSettings(context, "notifications")
                                                } else {
                                                    TitanSocialChatHandler.setEnabled(context, on)
                                                }
                                            },
                                            colors = SwitchDefaults.colors(checkedTrackColor = hudGreen)
                                        )
                                    }
                                }
                            }

                            item {
                                Surface(
                                    color = hudCard,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Ignore Group Chats", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("Only auto-reply to 1-on-1 personal chats (Recommended)", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                        }
                                        Switch(
                                            checked = chatIgnoreGroups,
                                            onCheckedChange = { TitanSocialChatHandler.setIgnoreGroups(context, it) },
                                            colors = SwitchDefaults.colors(checkedTrackColor = hudCyan)
                                        )
                                    }
                                }
                            }

                            item {
                                Text(
                                    text = "SELECT ALLOWED APPS (UNSELECTED APPS ARE IGNORED)",
                                    color = hudCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            items(TitanSocialChatHandler.SUPPORTED_APPS, key = { it.packageName }) { app ->
                                val isSelected = chatSelectedPkgs.contains(app.packageName)
                                Surface(
                                    color = if (isSelected) hudGreen.copy(alpha = 0.12f) else hudCard,
                                    shape = RoundedCornerShape(11.dp),
                                    border = BorderStroke(1.dp, if (isSelected) hudGreen.copy(alpha = 0.55f) else Color(0xFF1E293B)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { TitanSocialChatHandler.toggleAppSelection(context, app) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { TitanSocialChatHandler.toggleAppSelection(context, app) },
                                                colors = CheckboxDefaults.colors(checkedColor = hudGreen)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(app.displayName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text(app.packageName, color = Color(0xFF64748B), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                        Text(
                                            text = if (isSelected) "ALLOWED" else "OFF",
                                            color = if (isSelected) hudGreen else Color(0xFF64748B),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            item {
                                Surface(
                                    color = hudCard,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Hybrid Voice-Note Reader", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("Reads incoming WhatsApp voice notes & replies even when screen is off", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                        }
                                        Switch(
                                            checked = voiceNoteHybrid,
                                            onCheckedChange = { TitanSocialChatHandler.setVoiceNoteHybridEnabled(context, it) },
                                            colors = SwitchDefaults.colors(checkedTrackColor = hudCyan)
                                        )
                                    }
                                }
                            }

                            item {
                                Surface(
                                    color = hudCard,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, hudAmber.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "CONTACT MODES (GF / BF / FRIEND / WORK)",
                                            color = hudAmber,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        OutlinedTextField(
                                            value = newContactName,
                                            onValueChange = { newContactName = it },
                                            placeholder = { Text("Exact WhatsApp/Contact name (e.g. Riya, Shakil)", fontSize = 11.sp, color = Color(0xFF64748B)) },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = hudAmber,
                                                unfocusedBorderColor = Color(0xFF334155)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(ContactRelationshipMode.values().toList()) { mode ->
                                                val sel = newContactMode == mode
                                                Surface(
                                                    color = if (sel) hudAmber.copy(alpha = 0.25f) else Color(0xFF060D18),
                                                    shape = RoundedCornerShape(16.dp),
                                                    border = BorderStroke(1.dp, if (sel) hudAmber else Color(0xFF334155)),
                                                    modifier = Modifier.clickable { newContactMode = mode }
                                                ) {
                                                    Text(
                                                        text = mode.label,
                                                        color = if (sel) hudAmber else Color(0xFFCBD5E1),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                if (newContactName.isNotBlank()) {
                                                    TitanSocialChatHandler.setContactMode(context, newContactName, newContactMode)
                                                    Toast.makeText(context, "Mode set: ${newContactName.trim()} -> ${newContactMode.label}", Toast.LENGTH_SHORT).show()
                                                    newContactName = ""
                                                }
                                            },
                                            enabled = newContactName.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(containerColor = hudAmber, contentColor = Color(0xFF04101C)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("SAVE CONTACT MODE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                        }

                                        if (contactModes.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            contactModes.entries.forEach { (cname, cmode) ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 3.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFF060D18))
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "$cname  •  ${cmode.label}",
                                                        color = Color.White,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "REMOVE",
                                                        color = hudRed,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        modifier = Modifier.clickable {
                                                            TitanSocialChatHandler.removeContactMode(context, cname)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Surface(
                                    color = hudCard,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "REPLY STYLE / INSTRUCTION (OPTIONAL)",
                                            color = hudCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        OutlinedTextField(
                                            value = styleDraft,
                                            onValueChange = { styleDraft = it },
                                            placeholder = { Text("e.g. Reply politely like Tahir, say I'm busy if asked to meet", fontSize = 11.sp, color = Color(0xFF64748B)) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = hudCyan,
                                                unfocusedBorderColor = Color(0xFF334155)
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            maxLines = 2
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = {
                                                TitanSocialChatHandler.setCustomStyle(context, styleDraft)
                                                Toast.makeText(context, "Chat Handler style saved", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = hudCyan, contentColor = Color(0xFF04101C)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("SAVE STYLE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }

                            if (chatReplyLogs.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "RECENT AUTO-REPLIES (${chatReplyLogs.size})",
                                        color = hudAmber,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                items(chatReplyLogs) { lg ->
                                    Surface(
                                        color = Color(0xFF081220),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("${lg.appLabel} • ${lg.sender}", color = hudCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text("${lg.status} ${lg.timestamp}", color = if (lg.status == "SENT") hudGreen else hudAmber, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                            }
                                            Text("IN: ${lg.incomingText}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                            if (lg.sentReply != "-") {
                                                Text("OUT: ${lg.sentReply}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // TAB 2: LIVE NOTIFICATION BRAIN & DIRECT QUICK-REPLY
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                color = hudCard,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, hudCyan.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Titan Friend Voice Auto-Announce",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Auto-translate & speak WhatsApp/SMS alerts in your language",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                    Switch(
                                        checked = autoAnnounce,
                                        onCheckedChange = { TitanNotificationListenerService.setAutoAnnounce(it) },
                                        colors = SwitchDefaults.colors(checkedTrackColor = hudCyan)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (!TitanNotificationListenerService.isPermissionGranted(context)) {
                                Surface(
                                    color = hudAmber.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, hudAmber.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { TitanSystemTelemetry.openPermissionSettings(context, "notifications") }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = hudAmber)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Notification Access is OFF. Tap here to enable live reading & direct reply.",
                                            color = hudAmber,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            } else if (notifications.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "No incoming notifications captured yet.",
                                        color = Color(0xFF64748B),
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(notifications, key = { it.key }) { item ->
                                        Surface(
                                            color = hudCard,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${item.appLabel.uppercase()} • ${item.title}",
                                                        color = hudCyan,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (item.canDirectReply) {
                                                        Text(
                                                            text = "REPLY",
                                                            color = hudGreen,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontFamily = FontFamily.Monospace,
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(hudGreen.copy(alpha = 0.15f))
                                                                .clickable {
                                                                    replyTargetKey = if (replyTargetKey == item.key) null else item.key
                                                                    replyText = ""
                                                                }
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = item.text,
                                                    color = Color(0xFFE2E8F0),
                                                    fontSize = 12.sp
                                                )
                                                if (replyTargetKey == item.key) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        OutlinedTextField(
                                                            value = replyText,
                                                            onValueChange = { replyText = it },
                                                            placeholder = { Text("Direct quick reply...", fontSize = 12.sp) },
                                                            singleLine = true,
                                                            colors = OutlinedTextFieldDefaults.colors(
                                                                focusedTextColor = Color.White,
                                                                unfocusedTextColor = Color.White,
                                                                focusedBorderColor = hudGreen,
                                                                unfocusedBorderColor = Color(0xFF334155)
                                                            ),
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        IconButton(
                                                            onClick = {
                                                                val res = TitanNotificationListenerService.replyToNotification(
                                                                    context, item.key, replyText
                                                                )
                                                                Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                                                replyTargetKey = null
                                                                replyText = ""
                                                            }
                                                        ) {
                                                            Icon(Icons.Default.Send, contentDescription = "Send", tint = hudGreen)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        // TAB 3: ONE-TAP A-TO-Z PERMISSION MATRIX
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            item {
                                Surface(
                                    color = Color(0xFF102038),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, hudCyan.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { TitanSystemTelemetry.openAppInfoForRestrictedSettings(context) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null, tint = hudCyan, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Samsung / Android 13+ Restricted Settings Fix",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Tap to open App Info -> 3-dots -> Allow restricted settings before enabling Accessibility or Notification Access.",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            items(permissions, key = { it.id }) { perm ->
                                Surface(
                                    color = hudCard,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (perm.isGranted) hudGreen.copy(alpha = 0.35f) else hudAmber.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (perm.isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (perm.isGranted) hudGreen else hudAmber,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = perm.title,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = perm.subtitle,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (perm.isGranted) "ACTIVE" else "ENABLE",
                                            color = if (perm.isGranted) hudGreen else Color(0xFF04101C),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (perm.isGranted) hudGreen.copy(alpha = 0.15f) else hudAmber)
                                                .clickable {
                                                    if (perm.isSpecialSettings) {
                                                        TitanSystemTelemetry.openPermissionSettings(context, perm.id)
                                                    } else {
                                                        val reqs = when (perm.id) {
                                                            "mic" -> arrayOf(Manifest.permission.RECORD_AUDIO)
                                                            "phone_contacts" -> arrayOf(
                                                                Manifest.permission.CALL_PHONE,
                                                                Manifest.permission.READ_CONTACTS,
                                                                Manifest.permission.READ_PHONE_STATE
                                                            )
                                                            "sms" -> arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS)
                                                            "camera" -> arrayOf(Manifest.permission.CAMERA)
                                                            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                                                            } else emptyArray()
                                                        }
                                                        if (reqs.isNotEmpty()) runtimePermLauncher.launch(reqs)
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    4 -> {
                        // TAB 4: EQUAL-STYLE TITAN AI CALL ASSISTANT
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Surface(
                                    color = hudCard,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, if (callAiEnabled) hudCyan.copy(alpha = 0.55f) else Color(0xFF1E293B)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "TITAN AI CALL ASSISTANT",
                                                    color = hudCyan,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = "Live voice answering, quick chips & busy SMS",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Switch(
                                                checked = callAiEnabled,
                                                onCheckedChange = { com.titan.ide.os.TitanCallAssistantEngine.setEnabled(context, it) }
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            Button(
                                                onClick = {
                                                    val perms = buildList {
                                                        add(Manifest.permission.READ_PHONE_STATE)
                                                        add(Manifest.permission.CALL_PHONE)
                                                        add(Manifest.permission.SEND_SMS)
                                                        add(Manifest.permission.RECORD_AUDIO)
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                            add(Manifest.permission.ANSWER_PHONE_CALLS)
                                                        }
                                                    }.toTypedArray()
                                                    runtimePermLauncher.launch(perms)
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
                                                        TitanSystemTelemetry.openPermissionSettings(context, "overlay")
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A5F), contentColor = Color.White),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Grant Call Perms", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Button(
                                                onClick = {
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
                                                        TitanSystemTelemetry.openPermissionSettings(context, "overlay")
                                                    } else {
                                                        com.example.telecom.TitanCallOverlayService.show(context, "+91 98765 43210 (Test)")
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = hudCyan, contentColor = Color(0xFF04101C)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Test Call HUD", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                            }
                                        }

                                        Text("AUTO-ANSWER DELAY", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                            listOf(0 to "Manual", 6 to "6 sec", 10 to "10 sec", 15 to "15 sec").forEach { (sec, lbl) ->
                                                val sel = callAutoSec == sec
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (sel) hudCyan.copy(alpha = 0.22f) else Color(0xFF0D1628))
                                                        .border(1.dp, if (sel) hudCyan else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                                        .clickable { com.titan.ide.os.TitanCallAssistantEngine.setAutoAnswerSec(context, sec) }
                                                        .padding(vertical = 7.dp)
                                                ) {
                                                    Text(lbl, color = if (sel) hudCyan else Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = callRulesInput,
                                            onValueChange = {
                                                callRulesInput = it
                                                com.titan.ide.os.TitanCallAssistantEngine.setStandingRules(context, it)
                                            },
                                            label = { Text("Standing Rules (Delivery / Unknown / Spam)", color = hudCyan, fontSize = 11.sp) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = hudCyan,
                                                unfocusedBorderColor = Color(0xFF1E293B)
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            maxLines = 3
                                        )

                                        OutlinedTextField(
                                            value = callBusySmsInput,
                                            onValueChange = {
                                                callBusySmsInput = it
                                                com.titan.ide.os.TitanCallAssistantEngine.setBusySmsText(context, it)
                                            },
                                            label = { Text("Busy Auto-SMS Message", color = hudAmber, fontSize = 11.sp) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = hudAmber,
                                                unfocusedBorderColor = Color(0xFF1E293B)
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            maxLines = 2
                                        )
                                    }
                                }
                            }

                            item {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "AI CALL SUMMARIES (${callLogs.size})",
                                        color = hudCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (callLogs.isNotEmpty()) {
                                        Text(
                                            text = "CLEAR",
                                            color = hudRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable { com.titan.ide.os.TitanCallAssistantEngine.clearLogs(context) }
                                        )
                                    }
                                }
                            }

                            if (callLogs.isEmpty()) {
                                item {
                                    Surface(color = hudCard, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "No AI calls recorded yet.",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    }
                                }
                            } else {
                                items(callLogs) { log ->
                                    Surface(
                                        color = hudCard,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(log.callerNumber, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                                Text(log.actionTaken, color = hudGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Text(log.timestamp, color = Color(0xFF64748B), fontSize = 10.sp)
                                            Text(log.summary, color = Color(0xFFE2E8F0), fontSize = 12.sp)
                                            if (log.transcript.isNotBlank()) {
                                                Text(log.transcript, color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudMetricChip(
    label: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF0B1524),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = Color(0xFF64748B), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(value, color = accent, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold)
            Text(sub, color = Color(0xFF94A3B8), fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
    }
}

@Composable
private fun JarvisArcReactorMini(
    isActive: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "arc")
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 1800 else 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.45f), Color.Transparent),
                center = c,
                radius = r
            ),
            radius = r,
            center = c
        )
        rotate(angle, pivot = c) {
            drawArc(
                color = accent,
                startAngle = 0f,
                sweepAngle = 210f,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.85f, c.y - r * 0.85f),
                size = Size(r * 1.7f, r * 1.7f),
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )
        }
        rotate(-angle * 1.4f, pivot = c) {
            drawArc(
                color = accent.copy(alpha = 0.6f),
                startAngle = 90f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.55f, c.y - r * 0.55f),
                size = Size(r * 1.1f, r * 1.1f),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round)
            )
        }
        drawCircle(color = accent, radius = r * 0.22f, center = c)
    }
}
