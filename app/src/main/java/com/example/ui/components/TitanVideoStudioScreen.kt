package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.VoicePersona
import com.example.data.model.VoicePersonas
import com.titan.ide.media.video.Titan2DCartoonVideoEngine
import com.titan.ide.media.video.TitanRemotionExporter
import com.titan.ide.media.video.TitanStoryDirectorEngine
import com.titan.ide.media.video.TitanStoryDirectorEngine.StoryScene
import com.titan.ide.media.video.TitanStoryDirectorEngine.VideoAspect
import com.titan.ide.media.video.TitanStoryDirectorEngine.VideoTheme
import kotlinx.coroutines.launch
import java.io.File

private enum class StudioVideoMode(val label: String) {
    CARTOON_2D("2D Cartoon Story"),
    ROBOT_DIAGRAM("Robot Diagram")
}

private data class UnifiedStudioResult(
    val videoFile: File,
    val totalDurationMs: Long,
    val fps: Int,
    val aspect: VideoAspect,
    val theme: VideoTheme,
    val sceneSummaries: List<Triple<String, String, String>>, // (title, tag, narration)
    val exportScenes: List<StoryScene>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitanVideoStudioScreen(
    defaultVoicePersona: VoicePersona = VoicePersonas.DEFAULT,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var storyInput by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(StudioVideoMode.CARTOON_2D) }
    var selectedFps by remember { mutableIntStateOf(60) }
    var selectedAspect by remember { mutableStateOf(VideoAspect.PORTRAIT_9_16) }
    var selectedTheme by remember { mutableStateOf(VideoTheme.OBSIDIAN) }
    var selectedVoice by remember { mutableStateOf(defaultVoicePersona) }
    var showVoiceDropdown by remember { mutableStateOf(false) }

    var isGenerating by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var studioResult by remember { mutableStateOf<UnifiedStudioResult?>(null) }
    var isExportingRemotion by remember { mutableStateOf(false) }
    var remotionSavedPath by remember { mutableStateOf<String?>(null) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Make Storytelling Video",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "2D Cartoon (60 FPS) & Robot Diagrams • Auto Cast & Voice",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Prompt / Script Paste Box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "STORY OR PROMPT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                        if (clipText.isNotBlank()) {
                                            storyInput = clipText
                                        } else {
                                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Paste", fontSize = 12.sp)
                                }
                                TextButton(
                                    onClick = {
                                        storyInput = if (selectedMode == StudioVideoMode.CARTOON_2D) {
                                            "স্ত্রী: হ্যালো, আয়ানের স্কুলের ভর্তির ২৫ হাজার টাকা কালকেই জমা দিতে হবে!\nস্বামী: এত চিন্তার কিছু নেই, আমি অফিস থেকে ফিরেই ব্যবস্থা করে দিচ্ছি।\nস্ত্রী: কিন্তু তোমার অফিসের চাকরিটা তো ঠিক আছে?\nস্বামী: হ্যাঁ, আমি নতুন AI স্কিল শিখে প্রোমোশন পেয়ে গেছি—আর কোনো চিন্তা নেই!"
                                        } else {
                                            "আপনি কি জানেন গুগল ম্যাপ কীভাবে ২ কিমি আগেই ট্র্যাফিক জ্যাম বুঝে ফেলে? প্রথমে হাজার হাজার ফোনের GPS সিগন্যাল সার্ভারে যায়। তারপর AI গাড়ির স্পিড তুলনা করে রাস্তা সবুজ, হলুদ নাকি লাল তা হিসাব করে!"
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Sample", fontSize = 12.sp)
                                }
                                if (storyInput.isNotBlank()) {
                                    TextButton(
                                        onClick = { storyInput = "" },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Clear", fontSize = 12.sp, color = Color(0xFFEF4444))
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = storyInput,
                            onValueChange = { storyInput = it },
                            placeholder = {
                                Text(
                                    "ChatGPT বা Gemini থেকে গল্প বা ডায়ালগ পেস্ট করে Send চাপুন—ক্যারেক্টার, মুড আর ভয়েস অটো সেট হবে...",
                                    fontSize = 13.5.sp
                                )
                            },
                            minLines = 5,
                            maxLines = 10,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 2. Mode, FPS, Aspect & Voice Controls
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "VIDEO STYLE & FRAME RATE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StudioVideoMode.values().forEach { mode ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = { selectedMode = mode },
                                label = { Text(mode.label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFps == 30,
                            onClick = { selectedFps = 30 },
                            label = { Text("30 FPS (Fast)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedFps == 60,
                            onClick = { selectedFps = 60 },
                            label = { Text("60 FPS (Smooth)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoAspect.values().forEach { aspect ->
                            FilterChip(
                                selected = selectedAspect == aspect,
                                onClick = { selectedAspect = aspect },
                                label = { Text(aspect.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (selectedMode == StudioVideoMode.ROBOT_DIAGRAM) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VideoTheme.values().forEach { theme ->
                                FilterChip(
                                    selected = selectedTheme == theme,
                                    onClick = { selectedTheme = theme },
                                    label = { Text(theme.label, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    // Voice Persona Selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showVoiceDropdown = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedMode == StudioVideoMode.CARTOON_2D)
                                    "Default Voice: ${selectedVoice.name} (Auto Male/Female Cast)"
                                else
                                    "Voice: ${selectedVoice.name} (${selectedVoice.gender})",
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showVoiceDropdown,
                            onDismissRequest = { showVoiceDropdown = false }
                        ) {
                            VoicePersonas.ALL_PERSONAS.forEach { persona ->
                                DropdownMenuItem(
                                    text = { Text("${persona.name} • ${persona.characterTag}", fontSize = 13.sp) },
                                    onClick = {
                                        selectedVoice = persona
                                        showVoiceDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. Primary Send & Generate Button
                Button(
                    onClick = {
                        val trimmed = storyInput.trim()
                        if (trimmed.isBlank()) {
                            Toast.makeText(context, "আগে গল্প বা প্রম্পট পেস্ট করুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isGenerating = true
                        errorText = null
                        remotionSavedPath = null
                        studioResult = null
                        progressText = "Starting Titan Video Director..."
                        scope.launch {
                            try {
                                if (selectedMode == StudioVideoMode.CARTOON_2D) {
                                    val res = Titan2DCartoonVideoEngine.generate2DCartoonVideo(
                                        context = context,
                                        rawStory = trimmed,
                                        aspect = selectedAspect,
                                        fps = selectedFps,
                                        defaultVoiceId = selectedVoice.id,
                                        onProgress = { msg -> progressText = msg }
                                    )
                                    val exportScenes = res.scenes.map { c ->
                                        StoryScene(
                                            index = c.index,
                                            badge = c.speakerName,
                                            title = c.title,
                                            narration = c.narration,
                                            words = c.narration.split(Regex("\\s+")),
                                            mood = TitanStoryDirectorEngine.CharacterMood.EXPLAINING,
                                            diagramType = TitanStoryDirectorEngine.DiagramType.STORY_CARD,
                                            diagramItems = c.actors.map { it.preset }.ifEmpty { listOf("Scene") },
                                            statValue = "",
                                            accentColor = 0xFFEC4899.toInt(),
                                            durationMs = c.durationMs
                                        )
                                    }
                                    studioResult = UnifiedStudioResult(
                                        videoFile = res.videoFile,
                                        totalDurationMs = res.totalDurationMs,
                                        fps = res.fps,
                                        aspect = res.aspect,
                                        theme = selectedTheme,
                                        sceneSummaries = res.scenes.map { c ->
                                            Triple(
                                                "${c.index + 1}. ${c.title}",
                                                "${c.speakerName} • ${c.actors.joinToString("+") { it.preset }}",
                                                c.narration
                                            )
                                        },
                                        exportScenes = exportScenes
                                    )
                                } else {
                                    val res = TitanStoryDirectorEngine.generateStoryExplainerVideo(
                                        context = context,
                                        rawInput = trimmed,
                                        aspect = selectedAspect,
                                        theme = selectedTheme,
                                        voiceId = selectedVoice.id,
                                        onProgress = { msg -> progressText = msg }
                                    )
                                    studioResult = UnifiedStudioResult(
                                        videoFile = res.videoFile,
                                        totalDurationMs = res.totalDurationMs,
                                        fps = 30,
                                        aspect = res.aspect,
                                        theme = res.theme,
                                        sceneSummaries = res.scenes.map { s ->
                                            Triple(
                                                "${s.index + 1}. ${s.title}",
                                                "${s.mood.name} • ${s.diagramType.name}",
                                                s.narration
                                            )
                                        },
                                        exportScenes = res.scenes
                                    )
                                }
                                progressText = "Video Ready"
                            } catch (e: Exception) {
                                errorText = "Video generation failed: ${e.message}"
                            } finally {
                                isGenerating = false
                            }
                        }
                    },
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(progressText.ifBlank { "Generating..." }, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send & Make Video (${selectedFps} FPS)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                if (errorText != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorText ?: "",
                            color = Color(0xFFEF4444),
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // 4. Built-in Video Player + Save MP4 + Remotion ZIP
                studioResult?.let { result ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "PREVIEW (${result.sceneSummaries.size} SCENES)",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8B5CF6)
                                )
                                Text(
                                    text = "${result.totalDurationMs / 1000}s • ${result.fps} FPS • ${result.aspect.label}",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val playerHeight = if (result.aspect == VideoAspect.LANDSCAPE_16_9) 225.dp else 430.dp
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(playerHeight)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            val mc = MediaController(ctx)
                                            mc.setAnchorView(this)
                                            setMediaController(mc)
                                            setVideoURI(Uri.fromFile(result.videoFile))
                                            setOnPreparedListener { mp ->
                                                mp.isLooping = false
                                                start()
                                                isPlaying = true
                                            }
                                            setOnCompletionListener { isPlaying = false }
                                            videoViewRef = this
                                        }
                                    },
                                    update = { vv -> videoViewRef = vv },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        videoViewRef?.let { vv ->
                                            if (vv.isPlaying) { vv.pause(); isPlaying = false }
                                            else { vv.start(); isPlaying = true }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isPlaying) "Pause" else "Play", fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        videoViewRef?.let { vv -> vv.seekTo(0); vv.start(); isPlaying = true }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Replay", fontSize = 13.sp)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val saved = TitanStoryDirectorEngine.saveVideoToGallery(context, result.videoFile.absolutePath)
                                        if (saved != null) {
                                            Toast.makeText(context, "Saved to Movies/TitanIDE/$saved", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save MP4", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Button(
                                    onClick = {
                                        isExportingRemotion = true
                                        scope.launch {
                                            try {
                                                val exp = TitanRemotionExporter.exportRemotionProjectZip(
                                                    context = context,
                                                    scenes = result.exportScenes,
                                                    aspect = result.aspect,
                                                    theme = result.theme
                                                )
                                                remotionSavedPath = exp.savedDisplayName
                                                Toast.makeText(context, "Remotion ZIP saved: ${exp.savedDisplayName}", Toast.LENGTH_LONG).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isExportingRemotion = false
                                            }
                                        }
                                    },
                                    enabled = !isExportingRemotion,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Code, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isExportingRemotion) "Exporting..." else "Remotion ZIP", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            if (remotionSavedPath != null) {
                                Text(
                                    text = "Remotion Project Saved: $remotionSavedPath",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            Text(
                                text = "AUTO-DIRECTED CAST & SCENES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            result.sceneSummaries.forEach { (title, tag, narration) ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.background,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = title,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = tag,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFEC4899)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = narration,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
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
}
