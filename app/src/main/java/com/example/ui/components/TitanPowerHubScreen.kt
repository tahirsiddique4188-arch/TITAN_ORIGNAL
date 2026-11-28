package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private data class LoanRecord(
    val id: String,
    val person: String,
    val amount: Double,
    val type: String, // "GIVEN" or "TAKEN"
    val note: String,
    val date: String,
    val settled: Boolean = false
)

private data class ScheduledTaskItem(
    val id: String,
    val title: String,
    val timeLabel: String
)

private data class InstalledAppItem(
    val name: String,
    val packageName: String,
    val versionName: String
)

/**
 * TitanPowerHubScreen v4.1 — All-in-One Utility, Mobile Control, Defensive Net/Sec,
 * Loan/Udhar Tracker, Voice Recorder, Task Scheduler, WhatsApp Status Saver & Termux Launcher.
 */
@Composable
fun TitanPowerHubScreen(
    onOpenAppBuilder: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("titan_power_hub", Context.MODE_PRIVATE) }

    val bg = Color(0xFF040812)
    val card = Color(0xFF0B1324)
    val cyan = Color(0xFF22D3EE)
    val green = Color(0xFF10B981)
    val amber = Color(0xFFF59E0B)
    val red = Color(0xFFEF4444)

    var selectedTab by remember { mutableIntStateOf(0) }

    // Tab 0: App Manager
    var appSearch by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0 && installedApps.isEmpty()) {
            installedApps = withContext(Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    pm.getInstalledPackages(0)
                        .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                        .map { pkg ->
                            InstalledAppItem(
                                name = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName,
                                packageName = pkg.packageName,
                                versionName = pkg.versionName ?: "1.0"
                            )
                        }
                        .sortedBy { it.name.lowercase() }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
    }

    // Tab 1: Defensive Network & Security Tools
    var speedStatus by remember { mutableStateOf("Tap Run Speed Test") }
    var isTestingSpeed by remember { mutableStateOf(false) }
    var ipInfoText by remember { mutableStateOf("Tap Check My IP & Network") }
    var generatedPassword by remember { mutableStateOf(generateStrongPassword(16)) }
    var sslTargetUrl by remember { mutableStateOf("https://google.com") }
    var sslResult by remember { mutableStateOf("") }

    // Tab 2: Loan / Udhar Tracker
    var loans by remember { mutableStateOf(loadLoans(prefs)) }
    var loanPerson by remember { mutableStateOf("") }
    var loanAmount by remember { mutableStateOf("") }
    var loanNote by remember { mutableStateOf("") }
    var loanType by remember { mutableStateOf("GIVEN") }

    // Tab 3: Voice Recorder, Scheduler & WhatsApp Status Saver
    var isRecordingMemo by remember { mutableStateOf(false) }
    var activeRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var activePlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var voiceMemos by remember { mutableStateOf(listVoiceMemos(context)) }
    var taskTitle by remember { mutableStateOf("") }
    var taskTime by remember { mutableStateOf("08:00 AM") }
    var scheduledTasks by remember { mutableStateOf(loadTasks(prefs)) }
    var statusSaveReport by remember { mutableStateOf("") }

    // Tab 4: Termux & Local Shell Diagnostics
    var shellOutput by remember { mutableStateOf("Ready for local device diagnostics or Termux launch.") }

    DisposableEffect(Unit) {
        onDispose {
            try { activeRecorder?.stop(); activeRecorder?.release() } catch (_: Exception) {}
            try { activePlayer?.stop(); activePlayer?.release() } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = bg,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = cyan, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TITAN POWER TOOLBOX",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "All-in-One Controls, Net/Sec, Udhar, Recorder & Termux",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5-Tab Bar
                val tabs = listOf(
                    Triple(0, "CTRL", Icons.Default.Settings),
                    Triple(1, "NET", Icons.Default.Security),
                    Triple(2, "LOAN", Icons.Default.Speed),
                    Triple(3, "TOOLS", Icons.Default.Mic),
                    Triple(4, "TERMUX", Icons.Default.Terminal)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(card)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEach { (idx, label, _) ->
                        val active = selectedTab == idx
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (active) cyan.copy(alpha = 0.20f) else Color.Transparent)
                                .clickable { selectedTab = idx }
                                .padding(vertical = 9.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (active) cyan else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTab) {
                    // ==========================================================
                    // TAB 0: MOBILE CONTROL & APP MANAGER
                    // ==========================================================
                    0 -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Text("QUICK SETTINGS CONTROL", color = cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.height(8.dp))
                                val quickActions = listOf(
                                    "WiFi" to Settings.ACTION_WIFI_SETTINGS,
                                    "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
                                    "Airplane" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
                                    "Display" to Settings.ACTION_DISPLAY_SETTINGS,
                                    "Sound" to Settings.ACTION_SOUND_SETTINGS,
                                    "Battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
                                    "All Settings" to Settings.ACTION_SETTINGS,
                                    "Apps" to Settings.ACTION_APPLICATION_SETTINGS
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    quickActions.chunked(2).forEach { pair ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            pair.forEach { (title, action) ->
                                                Surface(
                                                    color = card,
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable {
                                                            try {
                                                                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                                            } catch (_: Exception) {
                                                                Toast.makeText(context, "Unavailable on this device", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                ) {
                                                    Box(modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                                        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = {
                                            try {
                                                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                                am.ringerMode = if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
                                                Toast.makeText(context, "Sound/Vibrate toggled", Toast.LENGTH_SHORT).show()
                                            } catch (_: Exception) {}
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A5F)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Toggle Vibrate", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            com.titan.ide.agent.TitanInstantReflexExecutor.tryExecuteInstant(context, "flashlight on")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = amber, contentColor = Color(0xFF1C1004)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Flashlight ON", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("APP MANAGER (${installedApps.size})", color = cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = appSearch,
                                    onValueChange = { appSearch = it },
                                    placeholder = { Text("Search installed apps...", color = Color(0xFF64748B), fontSize = 12.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = cyan,
                                        unfocusedBorderColor = Color(0xFF1E293B)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            val filteredApps = installedApps.filter {
                                appSearch.isBlank() || it.name.contains(appSearch, true) || it.packageName.contains(appSearch, true)
                            }.take(40)

                            items(filteredApps) { app ->
                                Surface(
                                    color = card,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(app.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${app.packageName} • v${app.versionName}", color = Color(0xFF64748B), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(
                                            text = "OPEN",
                                            color = cyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(cyan.copy(alpha = 0.14f))
                                                .clickable {
                                                    context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                                                        context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "INFO",
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF1E293B))
                                                .clickable {
                                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    context.startActivity(intent)
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================================
                    // TAB 1: DEFENSIVE NETWORK & SECURITY TOOLS
                    // ==========================================================
                    1 -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Internet Speed Test
                            item {
                                Surface(color = card, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, cyan.copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("INTERNET SPEED TEST", color = cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        Text(speedStatus, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Button(
                                            enabled = !isTestingSpeed,
                                            onClick = {
                                                isTestingSpeed = true
                                                speedStatus = "Testing ping & download speed..."
                                                scope.launch {
                                                    speedStatus = runSpeedTest()
                                                    isTestingSpeed = false
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = cyan, contentColor = Color(0xFF04101C)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(if (isTestingSpeed) "Testing..." else "Run Speed Test", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // IP & Network Lookup
                            item {
                                Surface(color = card, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("IP & NETWORK DIAGNOSTICS", color = green, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        Text(ipInfoText, color = Color(0xFFE2E8F0), fontSize = 12.sp)
                                        Button(
                                            onClick = {
                                                ipInfoText = "Fetching public IP & ISP..."
                                                scope.launch { ipInfoText = fetchPublicIpInfo() }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A5F)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Check My IP & ISP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // Strong Password Generator
                            item {
                                Surface(color = card, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("STRONG PASSWORD GENERATOR", color = amber, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = generatedPassword,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("Password", generatedPassword))
                                                Toast.makeText(context, "Password copied", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = cyan)
                                            }
                                        }
                                        Button(
                                            onClick = { generatedPassword = generateStrongPassword(16) },
                                            colors = ButtonDefaults.buttonColors(containerColor = amber, contentColor = Color(0xFF1C1004)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Generate New Password", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // Defensive Website HTTPS / Security Header Inspector
                            item {
                                Surface(color = card, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("WEBSITE SSL / SECURITY HEALTH CHECK", color = cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        OutlinedTextField(
                                            value = sslTargetUrl,
                                            onValueChange = { sslTargetUrl = it },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = cyan, unfocusedBorderColor = Color(0xFF1E293B)),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Button(
                                            onClick = {
                                                sslResult = "Checking HTTPS & security headers..."
                                                scope.launch { sslResult = inspectWebsiteHealth(sslTargetUrl) }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A5F)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Inspect Website Health", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        if (sslResult.isNotBlank()) {
                                            Text(sslResult, color = Color(0xFFCBD5E1), fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================================
                    // TAB 2: LOAN / UDHAR TRACKER (HISAB KHATA)
                    // ==========================================================
                    2 -> {
                        val totalGiven = loans.filter { it.type == "GIVEN" && !it.settled }.sumOf { it.amount }
                        val totalTaken = loans.filter { it.type == "TAKEN" && !it.settled }.sumOf { it.amount }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Surface(color = card, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, green.copy(alpha = 0.4f)), modifier = Modifier.weight(1f)) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text("YOU GAVE (PAONA)", color = green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text("₹${"%.0f".format(totalGiven)}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                    Surface(color = card, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, red.copy(alpha = 0.4f)), modifier = Modifier.weight(1f)) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text("YOU TOOK (DENA)", color = red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text("₹${"%.0f".format(totalTaken)}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }

                            item {
                                Surface(color = card, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            listOf("GIVEN" to "Gave Money", "TAKEN" to "Took Money").forEach { (key, label) ->
                                                val sel = loanType == key
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (sel) (if (key == "GIVEN") green else red).copy(alpha = 0.22f) else Color(0xFF0D1628))
                                                        .border(1.dp, if (sel) (if (key == "GIVEN") green else red) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                                        .clickable { loanType = key }
                                                        .padding(vertical = 8.dp)
                                                ) {
                                                    Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        OutlinedTextField(
                                            value = loanPerson,
                                            onValueChange = { loanPerson = it },
                                            label = { Text("Person Name", fontSize = 11.sp) },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = cyan, unfocusedBorderColor = Color(0xFF1E293B)),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = loanAmount,
                                                onValueChange = { loanAmount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                                label = { Text("Amount (₹)", fontSize = 11.sp) },
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = cyan, unfocusedBorderColor = Color(0xFF1E293B)),
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = loanNote,
                                                onValueChange = { loanNote = it },
                                                label = { Text("Note", fontSize = 11.sp) },
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = cyan, unfocusedBorderColor = Color(0xFF1E293B)),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                val amt = loanAmount.toDoubleOrNull() ?: 0.0
                                                if (loanPerson.isNotBlank() && amt > 0) {
                                                    val entry = LoanRecord(
                                                        id = System.currentTimeMillis().toString(),
                                                        person = loanPerson.trim(),
                                                        amount = amt,
                                                        type = loanType,
                                                        note = loanNote.trim(),
                                                        date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                                                    )
                                                    val updated = listOf(entry) + loans
                                                    loans = updated
                                                    saveLoans(prefs, updated)
                                                    loanPerson = ""; loanAmount = ""; loanNote = ""
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = cyan, contentColor = Color(0xFF04101C)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Add Entry", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            items(loans, key = { it.id }) { item ->
                                Surface(
                                    color = card,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (item.settled) Color(0xFF1E293B) else if (item.type == "GIVEN") green.copy(alpha = 0.4f) else red.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${item.person} • ₹${"%.0f".format(item.amount)}",
                                                color = if (item.settled) Color(0xFF64748B) else Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${if (item.type == "GIVEN") "Gave" else "Took"} • ${item.date}${if (item.note.isNotBlank()) " • ${item.note}" else ""}${if (item.settled) " • PAID" else ""}",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                        if (!item.settled) {
                                            Text(
                                                text = "PAID",
                                                color = green,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(green.copy(alpha = 0.15f))
                                                    .clickable {
                                                        val updated = loans.map { if (it.id == item.id) it.copy(settled = true) else it }
                                                        loans = updated; saveLoans(prefs, updated)
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                            )
                                        }
                                        IconButton(onClick = {
                                            val updated = loans.filterNot { it.id == item.id }
                                            loans = updated; saveLoans(prefs, updated)
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = red, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================================
                    // TAB 3: VOICE RECORDER, TASK SCHEDULER & WHATSAPP STATUS SAVER
                    // ==========================================================
                    3 -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Voice Recorder
                            item {
                                Surface(color = card, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, if (isRecordingMemo) red else Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("VOICE RECORDER (${voiceMemos.size} MEMOS)", color = cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        Button(
                                            onClick = {
                                                if (!isRecordingMemo) {
                                                    try {
                                                        val dir = File(context.filesDir, "titan_voice_memos").apply { mkdirs() }
                                                        val out = File(dir, "Memo_${SimpleDateFormat("ddMMM_HHmmss", Locale.US).format(Date())}.m4a")
                                                        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
                                                        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                                                        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                                        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                                        rec.setAudioSamplingRate(44100)
                                                        rec.setAudioEncodingBitRate(128000)
                                                        rec.setOutputFile(out.absolutePath)
                                                        rec.prepare()
                                                        rec.start()
                                                        activeRecorder = rec
                                                        isRecordingMemo = true
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Mic permission required", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    try { activeRecorder?.stop(); activeRecorder?.release() } catch (_: Exception) {}
                                                    activeRecorder = null
                                                    isRecordingMemo = false
                                                    voiceMemos = listVoiceMemos(context)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = if (isRecordingMemo) red else cyan, contentColor = if (isRecordingMemo) Color.White else Color(0xFF04101C)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(if (isRecordingMemo) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (isRecordingMemo) "Stop & Save Recording" else "Start Voice Recording", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                        }
                                        voiceMemos.take(5).forEach { memo ->
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                Text(memo.name, color = Color(0xFFE2E8F0), fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                IconButton(onClick = {
                                                    try {
                                                        activePlayer?.stop(); activePlayer?.release()
                                                        activePlayer = MediaPlayer().apply {
                                                            setDataSource(memo.absolutePath)
                                                            prepare()
                                                            start()
                                                        }
                                                    } catch (_: Exception) {}
                                                }) { Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = green) }
                                                IconButton(onClick = {
                                                    memo.delete()
                                                    voiceMemos = listVoiceMemos(context)
                                                }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = red, modifier = Modifier.size(18.dp)) }
                                            }
                                        }
                                    }
                                }
                            }

                            // WhatsApp Status Saver
                            item {
                                Surface(color = card, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, green.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("WHATSAPP STATUS SAVER", color = green, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    statusSaveReport = saveWhatsAppStatusesToGallery(context)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = green, contentColor = Color(0xFF04101C)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Scan & Save Viewed Statuses to Gallery", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                        }
                                        if (statusSaveReport.isNotBlank()) {
                                            Text(statusSaveReport, color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
                                        }
                                    }
                                }
                            }

                            // Task Scheduler
                            item {
                                Surface(color = card, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("TASK SCHEDULER (${scheduledTasks.size})", color = amber, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = taskTitle,
                                                onValueChange = { taskTitle = it },
                                                label = { Text("Task / Routine", fontSize = 11.sp) },
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = cyan, unfocusedBorderColor = Color(0xFF1E293B)),
                                                modifier = Modifier.weight(1.4f)
                                            )
                                            OutlinedTextField(
                                                value = taskTime,
                                                onValueChange = { taskTime = it },
                                                label = { Text("Time", fontSize = 11.sp) },
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = cyan, unfocusedBorderColor = Color(0xFF1E293B)),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                if (taskTitle.isNotBlank()) {
                                                    val updated = listOf(ScheduledTaskItem(System.currentTimeMillis().toString(), taskTitle.trim(), taskTime.trim())) + scheduledTasks
                                                    scheduledTasks = updated
                                                    saveTasks(prefs, updated)
                                                    taskTitle = ""
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = amber, contentColor = Color(0xFF1C1004)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Schedule Task", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                        }
                                        scheduledTasks.forEach { t ->
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                Text("${t.timeLabel} — ${t.title}", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                                IconButton(onClick = {
                                                    val updated = scheduledTasks.filterNot { it.id == t.id }
                                                    scheduledTasks = updated; saveTasks(prefs, updated)
                                                }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = red, modifier = Modifier.size(18.dp)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================================
                    // TAB 4: TERMUX POWER & SCRIPT TOOLS
                    // ==========================================================
                    4 -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Surface(color = card, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, cyan.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("TERMUX & DEVELOPER POWER", color = cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            Button(
                                                onClick = {
                                                    val launch = context.packageManager.getLaunchIntentForPackage("com.termux")
                                                    if (launch != null) {
                                                        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                                    } else {
                                                        Toast.makeText(context, "Termux not installed", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = green, contentColor = Color(0xFF04101C)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Open Termux", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                            }
                                            Button(
                                                onClick = {
                                                    onDismiss()
                                                    onOpenAppBuilder()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = cyan, contentColor = Color(0xFF04101C)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("App & Web Builder", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                            }
                                        }
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    shellOutput = withContext(Dispatchers.IO) {
                                                        try {
                                                            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", "uname -a && getprop ro.product.model && df -h /data | tail -n 1"))
                                                            p.inputStream.bufferedReader().readText().trim().ifBlank { "Done." }
                                                        } catch (e: Exception) {
                                                            "Info: ${e.message}"
                                                        }
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A5F)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Run System Shell Diagnostics", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Text(shellOutput, color = Color(0xFF34D399), fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
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

private fun generateStrongPassword(length: Int): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#\$%&*!?"
    val rng = SecureRandom()
    return (1..length).map { chars[rng.nextInt(chars.length)] }.joinToString("")
}

private suspend fun runSpeedTest(): String = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
        val t0 = System.nanoTime()
        val pingReq = Request.Builder().url("https://www.google.com/generate_204").build()
        client.newCall(pingReq).execute().close()
        val pingMs = (System.nanoTime() - t0) / 1_000_000L

        val d0 = System.nanoTime()
        val dlReq = Request.Builder().url("https://speed.cloudflare.com/__down?bytes=2500000").build()
        var totalBytes = 0L
        client.newCall(dlReq).execute().use { resp ->
            val buf = ByteArray(16384)
            resp.body?.byteStream()?.use { ins ->
                while (true) {
                    val r = ins.read(buf)
                    if (r <= 0) break
                    totalBytes += r
                }
            }
        }
        val sec = ((System.nanoTime() - d0) / 1_000_000_000.0).coerceAtLeast(0.05)
        val mbps = (totalBytes * 8.0) / (sec * 1_000_000.0)
        "Download: ${"%.1f".format(mbps)} Mbps  •  Ping: ${pingMs} ms"
    } catch (e: Exception) {
        "Speed test failed: ${e.message}"
    }
}

private suspend fun fetchPublicIpInfo(): String = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
        val req = Request.Builder().url("https://ipwho.is/").build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            val j = JSONObject(body)
            "IP: ${j.optString("ip")}\nLocation: ${j.optString("city")}, ${j.optString("region")}, ${j.optString("country")}\nISP: ${j.optJSONObject("connection")?.optString("isp") ?: "Unknown"}"
        }
    } catch (e: Exception) {
        "IP lookup failed: ${e.message}"
    }
}

private suspend fun inspectWebsiteHealth(rawUrl: String): String = withContext(Dispatchers.IO) {
    try {
        val url = if (rawUrl.startsWith("http")) rawUrl.trim() else "https://${rawUrl.trim()}"
        val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
        val t0 = System.nanoTime()
        client.newCall(Request.Builder().url(url).head().build()).execute().use { resp ->
            val ms = (System.nanoTime() - t0) / 1_000_000L
            val hsts = if (resp.header("Strict-Transport-Security") != null) "YES" else "NO"
            val csp = if (resp.header("Content-Security-Policy") != null) "YES" else "NO"
            "Status: HTTP ${resp.code} (${ms}ms)\nHTTPS: ${if (url.startsWith("https")) "Active" else "Not HTTPS"}\nHSTS: $hsts  •  CSP: $csp"
        }
    } catch (e: Exception) {
        "Check failed: ${e.message}"
    }
}

private fun listVoiceMemos(context: Context): List<File> {
    val dir = File(context.filesDir, "titan_voice_memos")
    return dir.listFiles()?.filter { it.isFile && it.name.endsWith(".m4a") }?.sortedByDescending { it.lastModified() } ?: emptyList()
}

private suspend fun saveWhatsAppStatusesToGallery(context: Context): String = withContext(Dispatchers.IO) {
    val ext = Environment.getExternalStorageDirectory()
    val candidates = listOf(
        File(ext, "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"),
        File(ext, "WhatsApp/Media/.Statuses"),
        File(ext, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses")
    )
    val files = candidates.flatMap { dir ->
        dir.listFiles()?.filter { it.isFile && (it.name.endsWith(".jpg", true) || it.name.endsWith(".mp4", true)) } ?: emptyList()
    }
    if (files.isEmpty()) {
        return@withContext "No viewed WhatsApp statuses found (view a status in WhatsApp first & allow All Files access)."
    }
    var saved = 0
    val resolver = context.contentResolver
    files.take(20).forEach { f ->
        try {
            val isVid = f.name.endsWith(".mp4", true)
            val cv = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "TitanStatus_${f.name}")
                put(MediaStore.MediaColumns.MIME_TYPE, if (isVid) "video/mp4" else "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVid) "Movies/TitanStatus" else "Pictures/TitanStatus")
                }
            }
            val uri = resolver.insert(
                if (isVid) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                cv
            )
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                saved++
            }
        } catch (_: Exception) {}
    }
    "Saved $saved WhatsApp status file(s) to Gallery (TitanStatus)."
}

private fun saveLoans(prefs: android.content.SharedPreferences, list: List<LoanRecord>) {
    val arr = JSONArray()
    list.forEach {
        arr.put(JSONObject().apply {
            put("id", it.id); put("p", it.person); put("a", it.amount); put("t", it.type)
            put("n", it.note); put("d", it.date); put("s", it.settled)
        })
    }
    prefs.edit().putString("loans_json", arr.toString()).apply()
}

private fun loadLoans(prefs: android.content.SharedPreferences): List<LoanRecord> {
    val raw = prefs.getString("loans_json", null) ?: return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            LoanRecord(o.optString("id"), o.optString("p"), o.optDouble("a"), o.optString("t", "GIVEN"), o.optString("n"), o.optString("d"), o.optBoolean("s"))
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveTasks(prefs: android.content.SharedPreferences, list: List<ScheduledTaskItem>) {
    val arr = JSONArray()
    list.forEach { arr.put(JSONObject().apply { put("id", it.id); put("t", it.title); put("tm", it.timeLabel) }) }
    prefs.edit().putString("tasks_json", arr.toString()).apply()
}

private fun loadTasks(prefs: android.content.SharedPreferences): List<ScheduledTaskItem> {
    val raw = prefs.getString("tasks_json", null) ?: return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            ScheduledTaskItem(o.optString("id"), o.optString("t"), o.optString("tm"))
        }
    } catch (_: Exception) { emptyList() }
}
