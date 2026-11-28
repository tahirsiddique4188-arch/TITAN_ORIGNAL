package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voice.TitanAssistantConfig
import com.titan.ide.brain.TitanBrainMemoryEngine
import com.titan.ide.brain.TitanBrainMemoryEngine.CharacterArchetype
import com.titan.ide.brain.TitanFirebaseBrainSync
import kotlinx.coroutines.launch
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitanBrainStudioScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = 3D Brain, 1 = Assistant & Gender, 2 = Firebase Cloud
    var facts by remember { mutableStateOf(TitanBrainMemoryEngine.getAllFacts(context)) }
    var focusedNodeIdx by remember { mutableStateOf<Int?>(null) }

    // Character Lock & Male/Female Hologram State
    var assistantName by remember { mutableStateOf(TitanAssistantConfig.getAssistantName(context)) }
    var userCallName by remember { mutableStateOf(TitanBrainMemoryEngine.getUserCallName(context)) }
    var selectedArchetype by remember { mutableStateOf(TitanBrainMemoryEngine.getCharacterArchetype(context)) }
    var customCharacterPrompt by remember { mutableStateOf(TitanBrainMemoryEngine.getCustomCharacterPrompt(context)) }
    var isMaleAvatar by remember {
        mutableStateOf(
            com.titan.ide.voice.TitanInstantTtsEngine.getSelectedVoiceId(context)
                .let { id -> com.example.data.model.VoicePersonas.getById(id).gender.equals("Male", ignoreCase = true) }
        )
    }

    // Firebase Cloud Brain Sync State
    var firebaseUrl by remember { mutableStateOf(TitanFirebaseBrainSync.getFirebaseUrl(context)) }
    var syncCode by remember { mutableStateOf(TitanFirebaseBrainSync.getOrCreateSyncCode(context)) }
    var authSecret by remember { mutableStateOf(TitanFirebaseBrainSync.getAuthSecret(context)) }
    var autoSync by remember { mutableStateOf(TitanFirebaseBrainSync.isAutoSyncEnabled(context)) }
    var cloudStatus by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF03060D)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Sleek Holographic Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF070D1B))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF6366F1)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${if (assistantName.equals("Jarvis", ignoreCase = true) || assistantName.isBlank()) "Titan" else assistantName} Neural Brain",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${facts.size} Memories • 3D Neural Hologram",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // 3-Tab Mode Selector
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = Color(0xFF070D1B),
                contentColor = Color(0xFF38BDF8)
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("3D Brain", fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Assistant", fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Cloud Sync", fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
                )
            }

            when (activeTab) {
                0 -> {
                    // TAB 1: FULL-SCREEN INTERACTIVE 3D POINT-CLOUD BRAIN!
                    Titan3DBrainCanvas(
                        facts = facts,
                        focusFactIndex = focusedNodeIdx,
                        onSelectFact = { focusedNodeIdx = it },
                        onDeleteFact = { idx ->
                            TitanBrainMemoryEngine.deleteFactAt(context, idx)
                            facts = TitanBrainMemoryEngine.getAllFacts(context)
                            focusedNodeIdx = null
                            TitanFirebaseBrainSync.triggerAutoSyncIfConfigured(context)
                            Toast.makeText(context, "Memory removed from Brain", Toast.LENGTH_SHORT).show()
                        },
                        onQuickSaveMemory = { text ->
                            TitanBrainMemoryEngine.saveFact(context, text, "Manual / Saved")
                            facts = TitanBrainMemoryEngine.getAllFacts(context)
                            focusedNodeIdx = 0 // Fly straight into the brand-new Memory Node!
                            TitanFirebaseBrainSync.triggerAutoSyncIfConfigured(context)
                            Toast.makeText(context, "Saved & Flying to new Memory Node!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                1 -> {
                    // TAB 2: HOLOGRAPHIC MALE / FEMALE ASSISTANT + CHARACTER LOCK!
                    val inf = rememberInfiniteTransition(label = "holo")
                    val holoPulse by inf.animateFloat(
                        initialValue = 0f,
                        targetValue = 6.283f,
                        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
                        label = "hp"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Holographic Male / Female Switch Card
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF0A1224),
                            border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    FilterChip(
                                        selected = isMaleAvatar,
                                        onClick = {
                                            isMaleAvatar = true
                                            context.getSharedPreferences("titan_prefs", Context.MODE_PRIVATE).edit()
                                                .putString("selected_persona_id", "aryan")
                                                .putString("selected_voice_persona", "aryan")
                                                .apply()
                                            Toast.makeText(context, "Switched to Male Assistant (Aryan Neural Voice)", Toast.LENGTH_SHORT).show()
                                        },
                                        label = { Text("MALE ASSISTANT", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                    )
                                    FilterChip(
                                        selected = !isMaleAvatar,
                                        onClick = {
                                            isMaleAvatar = false
                                            context.getSharedPreferences("titan_prefs", Context.MODE_PRIVATE).edit()
                                                .putString("selected_persona_id", "zara")
                                                .putString("selected_voice_persona", "zara")
                                                .apply()
                                            Toast.makeText(context, "Switched to Female Assistant (Zara Neural Voice)", Toast.LENGTH_SHORT).show()
                                        },
                                        label = { Text("FEMALE ASSISTANT", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                    )
                                }

                                // Animated Cyan/Violet Hologram Silhouette
                                Box(
                                    modifier = Modifier.size(132.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val c = Offset(size.width / 2f, size.height / 2f)
                                        val glowCol = if (isMaleAvatar) Color(0xFF06B6D4) else Color(0xFFEC4899)
                                        val rPulse = size.minDimension * (0.44f + 0.04f * sin(holoPulse))
                                        drawCircle(
                                            brush = Brush.radialGradient(listOf(glowCol.copy(alpha = 0.45f), Color.Transparent), c, rPulse),
                                            radius = rPulse,
                                            center = c
                                        )
                                        // Hologram head & shoulders
                                        drawCircle(color = glowCol.copy(alpha = 0.9f), radius = 22f, center = Offset(c.x, c.y - 20f))
                                        drawArc(
                                            color = glowCol.copy(alpha = 0.85f),
                                            startAngle = 180f,
                                            sweepAngle = 180f,
                                            useCenter = true,
                                            topLeft = Offset(c.x - 42f, c.y + 8f),
                                            size = androidx.compose.ui.geometry.Size(84f, 64f)
                                        )
                                    }
                                }

                                Text(
                                    text = "$assistantName • ${if (isMaleAvatar) "Male Neural Core" else "Female Neural Core"}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMaleAvatar) Color(0xFF38BDF8) else Color(0xFFF472B6)
                                )
                            }
                        }

                        // Character Identity & Personality Lock
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF0A1224),
                            border = BorderStroke(1.dp, Color(0x4438BDF8)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("LOCKED ASSISTANT CHARACTER", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = assistantName,
                                        onValueChange = { assistantName = it },
                                        label = { Text("Assistant Name", fontSize = 11.sp) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = userCallName,
                                        onValueChange = { userCallName = it },
                                        label = { Text("Calls You", fontSize = 11.sp) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CharacterArchetype.values().forEach { arch ->
                                        FilterChip(
                                            selected = selectedArchetype == arch,
                                            onClick = { selectedArchetype = arch },
                                            label = { Text(arch.label, fontSize = 12.sp) }
                                        )
                                    }
                                }

                                if (selectedArchetype == CharacterArchetype.CUSTOM) {
                                    OutlinedTextField(
                                        value = customCharacterPrompt,
                                        onValueChange = { customCharacterPrompt = it },
                                        label = { Text("Custom Character Rules", fontSize = 11.5.sp) },
                                        minLines = 3,
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Button(
                                    onClick = {
                                        TitanAssistantConfig.setAssistantName(context, assistantName.ifBlank { "Titan" })
                                        TitanBrainMemoryEngine.saveCharacterLock(context, selectedArchetype, userCallName.ifBlank { "Bhai" }, customCharacterPrompt)
                                        TitanFirebaseBrainSync.triggerAutoSyncIfConfigured(context)
                                        Toast.makeText(context, "Assistant Character Locked!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Lock Character & Sync", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                }
                            }
                        }
                    }
                }

                else -> {
                    // TAB 3: FIREBASE CLOUD BRAIN SYNC & 1-TAP PHONE RESTORE
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF0A1224),
                            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("FIREBASE CLOUD BRAIN", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA78BFA))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Auto-Sync", fontSize = 11.5.sp, color = Color(0xFF94A3B8))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Switch(checked = autoSync, onCheckedChange = { autoSync = it })
                                    }
                                }

                                Text(
                                    text = "Phone haraile ba notun phone-e ai Firebase URL + Brain Sync Code dilei tomar exact Assistant ar sob 3D Brain memory fire asbe!",
                                    fontSize = 12.sp,
                                    color = Color(0xFFCBD5E1)
                                )

                                OutlinedTextField(
                                    value = firebaseUrl,
                                    onValueChange = { firebaseUrl = it },
                                    label = { Text("Firebase Realtime DB URL", fontSize = 11.5.sp) },
                                    placeholder = { Text("https://your-project-default-rtdb.firebaseio.com", fontSize = 12.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = syncCode,
                                        onValueChange = { syncCode = it },
                                        label = { Text("Brain Sync Code", fontSize = 11.5.sp) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("Titan Brain Code", syncCode))
                                            Toast.makeText(context, "Brain Code copied!", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF38BDF8))
                                    }
                                }

                                OutlinedTextField(
                                    value = authSecret,
                                    onValueChange = { authSecret = it },
                                    label = { Text("Database Auth Secret (Optional)", fontSize = 11.5.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = {
                                            TitanFirebaseBrainSync.saveCloudConfig(context, firebaseUrl, syncCode, authSecret, autoSync)
                                            isSyncing = true
                                            cloudStatus = "Syncing 3D Brain to Firebase..."
                                            scope.launch {
                                                val (_, msg) = TitanFirebaseBrainSync.backupBrainToFirebase(context)
                                                cloudStatus = msg
                                                isSyncing = false
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = !isSyncing,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Backup Now", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    Button(
                                        onClick = {
                                            isSyncing = true
                                            cloudStatus = "Restoring Assistant Brain..."
                                            scope.launch {
                                                val (ok, msg) = TitanFirebaseBrainSync.restoreBrainFromFirebase(context, firebaseUrl, syncCode, authSecret)
                                                cloudStatus = msg
                                                isSyncing = false
                                                if (ok) {
                                                    assistantName = TitanAssistantConfig.getAssistantName(context)
                                                    userCallName = TitanBrainMemoryEngine.getUserCallName(context)
                                                    selectedArchetype = TitanBrainMemoryEngine.getCharacterArchetype(context)
                                                    customCharacterPrompt = TitanBrainMemoryEngine.getCustomCharacterPrompt(context)
                                                    facts = TitanBrainMemoryEngine.getAllFacts(context)
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        enabled = !isSyncing,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Restore Brain", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }

                                if (cloudStatus.isNotBlank()) {
                                    Text(cloudStatus, fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
