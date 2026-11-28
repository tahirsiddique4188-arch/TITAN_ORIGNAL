package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titan.ide.brain.TitanBrainMemoryEngine
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private data class BrainParticle(val x: Float, val y: Float, val z: Float, val bright: Float)

private data class BrainMemNode(
    val factIndex: Int,
    val fact: TitanBrainMemoryEngine.MemoryFact,
    val x: Float,
    val y: Float,
    val z: Float,
    val lobeIdx: Int
)

private val LOBE_COLORS = listOf(
    Color(0xFF06B6D4), // 0: Projects & Code (Cyan)
    Color(0xFFA855F7), // 1: Preferences (Violet)
    Color(0xFFEC4899), // 2: Personal & People (Pink)
    Color(0xFFFACC15), // 3: Manual / Saved (Gold)
    Color(0xFF10B981)  // 4: System & Routines (Emerald)
)

private val LOBE_LABELS = listOf(
    "Projects & Code",
    "Preferences",
    "Personal & People",
    "Manual / Saved",
    "System & Routines"
)

private fun categoryToLobe(cat: String): Int {
    val c = cat.lowercase()
    return when {
        c.contains("project") || c.contains("code") -> 0
        c.contains("pref") || c.contains("rule") -> 1
        c.contains("people") || c.contains("identity") || c.contains("about") -> 2
        c.contains("routine") || c.contains("system") -> 4
        else -> 3
    }
}

/**
 * Titan3DBrainCanvas — Real-Time 60 FPS Interactive 3D Point-Cloud Human Brain
 *
 * Features:
 * - 1,450+ 3D anatomical point-cloud particles (Cerebrum + Cerebellum + Brainstem)
 * - 360-degree touch drag rotation + pinch/button zoom right INSIDE the neural network
 * - Automatic Fly-In Camera Zoom to any newly saved or tapped Memory Node
 * - Glowing color-coded Synaptic Axons with animated electric pulses
 * - Tap-to-Inspect Memory Card + 1-tap Reset View
 */
@Composable
fun Titan3DBrainCanvas(
    facts: List<TitanBrainMemoryEngine.MemoryFact>,
    focusFactIndex: Int?,
    onSelectFact: (Int?) -> Unit,
    onDeleteFact: (Int) -> Unit,
    onQuickSaveMemory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Generate deterministic 3D anatomical Human Brain point cloud once
    val cloudParticles = remember {
        val rng = Random(77)
        val list = ArrayList<BrainParticle>(1450)
        // Cerebrum Left & Right Hemispheres
        repeat(1100) {
            val side = if (rng.nextFloat() < 0.5f) -1f else 1f
            val u = rng.nextFloat() * 2f * PI.toFloat()
            val v = acos((rng.nextFloat() * 2f - 1f).toDouble()).toFloat()
            val r = Math.pow(rng.nextDouble() * 0.22 + 0.78, 0.45).toFloat()
            val sx = kotlin.math.abs(sin(v) * cos(u)) * r
            val sy = cos(v) * r
            val sz = sin(v) * sin(u) * r
            val px = side * (0.08f + sx * 0.78f)
            val py = -0.14f + sy * 0.68f - (if (sz > 0.2f && sy > 0f) 0.12f else 0f)
            val pz = sz * 1.12f
            val ripple = 0.045f * sin(px * 12f + pz * 9f) * cos(py * 10f)
            list.add(BrainParticle(px + ripple, py + ripple * 0.6f, pz, 0.55f + rng.nextFloat() * 0.45f))
        }
        // Cerebellum
        repeat(220) {
            val u = rng.nextFloat() * 2f * PI.toFloat()
            val v = acos((rng.nextFloat() * 2f - 1f).toDouble()).toFloat()
            val r = Math.pow(rng.nextDouble() * 0.25 + 0.75, 0.5).toFloat()
            list.add(
                BrainParticle(
                    sin(v) * cos(u) * 0.52f * r,
                    0.42f + cos(v) * 0.26f * r,
                    -0.58f + sin(v) * sin(u) * 0.36f * r,
                    0.65f + rng.nextFloat() * 0.35f
                )
            )
        }
        // Brainstem
        repeat(130) {
            val ang = rng.nextFloat() * 2f * PI.toFloat()
            val hs = rng.nextFloat()
            val rad = (0.18f - 0.04f * hs) * (0.82f + rng.nextFloat() * 0.18f)
            list.add(
                BrainParticle(
                    cos(ang) * rad,
                    0.32f + hs * 0.62f,
                    -0.12f - hs * 0.14f + sin(ang) * rad,
                    0.60f + rng.nextFloat() * 0.40f
                )
            )
        }
        list
    }

    // Place user's real saved memories deterministically inside the 3D Brain!
    val memNodes = remember(facts) {
        val seedList = facts.ifEmpty {
            listOf(
                TitanBrainMemoryEngine.MemoryFact("Titan Core Identity Active", "System & Routines", 0L),
                TitanBrainMemoryEngine.MemoryFact("Personal AI Assistant Mode", "Preferences", 0L),
                TitanBrainMemoryEngine.MemoryFact("Ready to learn your memories", "Manual / Saved", 0L)
            )
        }
        seedList.take(36).mapIndexed { idx, f ->
            val r = Random(idx * 9973 + 13)
            val lobe = categoryToLobe(f.category)
            val x = (r.nextFloat() * 0.9f - 0.45f)
            val y = (r.nextFloat() * 0.66f - 0.34f)
            val z = (r.nextFloat() * 1.1f - 0.55f)
            BrainMemNode(idx, f, x, y, z, lobe)
        }
    }

    var userYaw by remember { mutableFloatStateOf(0.4f) }
    var userPitch by remember { mutableFloatStateOf(0.14f) }
    var manualZoom by remember { mutableFloatStateOf(0f) } // 0 = full brain, 1 = deep inside
    var quickInput by remember { mutableStateOf("") }

    // Smooth animated fly-in zoom when a memory node is focused!
    val targetZoom = if (focusFactIndex != null) 0.92f else manualZoom
    val animZoom by animateFloatAsState(
        targetValue = targetZoom,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "brain_zoom"
    )

    // Continuous 60 FPS ambient orbit + synaptic pulse timer
    val infinite = rememberInfiniteTransition(label = "brain_orbit")
    val animPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_phase"
    )
    val pulseT by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "axon_pulse"
    )

    // Track last projected 2D node screen coords for tap hit-testing
    val projectedHits = remember { java.util.concurrent.ConcurrentHashMap<Int, Pair<Float, Float>>() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF03060D))
    ) {
        // 3D Point-Cloud + Synaptic Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        userYaw += dragAmount.x * 0.0085f
                        userPitch = (userPitch + dragAmount.y * 0.0065f).coerceIn(-1.1f, 1.1f)
                    }
                }
                .pointerInput(memNodes) {
                    detectTapGestures { tapOffset ->
                        var closestIdx: Int? = null
                        var bestDist = 68f
                        for ((idx, pos) in projectedHits) {
                            val d = hypot(tapOffset.x - pos.first, tapOffset.y - pos.second)
                            if (d < bestDist) {
                                bestDist = d
                                closestIdx = idx
                            }
                        }
                        if (closestIdx != null && facts.isNotEmpty()) {
                            onSelectFact(closestIdx)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.45f
            val yaw = userYaw + (if (focusFactIndex == null) animPhase else animPhase * 0.15f)
            val pitch = userPitch
            val cosY = cos(yaw); val sinY = sin(yaw)
            val cosP = cos(pitch); val sinP = sin(pitch)

            // Focus target 3D point (smoothly flies camera straight to the focused memory node!)
            val focusNode = focusFactIndex?.let { idx -> memNodes.getOrNull(idx) }
            var camX = 0f
            var camY = 0f
            var camZ = -3.35f + animZoom * 2.15f

            if (focusNode != null) {
                val fx1 = focusNode.x * cosY + focusNode.z * sinY
                val fz1 = -focusNode.x * sinY + focusNode.z * cosY
                val fy2 = focusNode.y * cosP - fz1 * sinP
                val fz2 = focusNode.y * sinP + fz1 * cosP
                camX = fx1 * animZoom
                camY = fy2 * animZoom
                camZ = -3.35f + ((fz2 - 1.12f) - (-3.35f)) * animZoom
            }

            val fov = minOf(w, h) * 1.05f

            // Colorful multi-lobe holographic core aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x30A855F7), Color(0x2006B6D4), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = minOf(w, h) * 0.50f
                ),
                center = Offset(cx, cy),
                radius = minOf(w, h) * 0.50f
            )

            fun proj(x: Float, y: Float, z: Float): Triple<Float, Float, Float>? {
                val x1 = x * cosY + z * sinY
                val z1 = -x * sinY + z * cosY
                val y2 = y * cosP - z1 * sinP
                val z2 = y * sinP + z1 * cosP
                val rz = z2 - camZ
                if (rz <= 0.22f) return null
                val s = fov / rz
                return Triple(cx + (x1 - camX) * s, cy + (y2 - camY) * s, s)
            }

            // 1. Draw Colorful 5-Lobe 3D Point-Cloud Human Brain Particles
            for (pt in cloudParticles) {
                val p = proj(pt.x, pt.y, pt.z) ?: continue
                val sx = p.first; val sy = p.second; val sc = p.third
                if (sx < -10f || sx > w + 10f || sy < -10f || sy > h + 10f) continue
                val dotR = (sc / 185f).coerceIn(1.2f, 3.5f)
                val alpha = (pt.bright * (sc / 260f)).coerceIn(0.24f, 0.92f)
                val lobeBaseColor = when {
                    pt.y < -0.32f -> Color(0xFFA855F7) // Crown: Neon Violet
                    pt.x < -0.25f && pt.z > 0f -> Color(0xFF00E5FF) // Front-Left: Electric Cyan
                    pt.x > 0.25f && pt.z > 0f -> Color(0xFFEC4899) // Front-Right: Neon Magenta
                    pt.z < -0.25f -> Color(0xFF10B981) // Back: Emerald Green
                    else -> Color(0xFFFACC15) // Inner Core: Solar Gold
                }
                val col = if (pt.bright > 0.80f) Color.White.copy(alpha = alpha * 0.95f) else lobeBaseColor.copy(alpha = alpha)
                drawCircle(color = col, radius = dotR, center = Offset(sx, sy))
            }

            // 2. Project Memory Nodes
            val projList = memNodes.map { n -> proj(n.x, n.y, n.z) }
            projectedHits.clear()
            projList.forEachIndexed { idx, p ->
                if (p != null) projectedHits[idx] = p.first to p.second
            }

            // 3. Draw Organic Curved ("Tra-Baka") Cubic Bezier Dendrite Pathways + Traveling Light Sparks
            val curvePath = Path()
            for (i in memNodes.indices) {
                val p1 = projList[i] ?: continue
                val next1 = (i + 1) % memNodes.size
                val next2 = (i + 3) % memNodes.size
                for ((edgeIdx, j) in listOf(next1, next2).withIndex()) {
                    if (j == i) continue
                    val p2 = projList[j] ?: continue
                    val col = LOBE_COLORS[(memNodes[i].lobeIdx + edgeIdx) % LOBE_COLORS.size]
                    val isHighlighted = (focusFactIndex == i || focusFactIndex == j)

                    val dx = p2.first - p1.first
                    val dy = p2.second - p1.second
                    val dist = hypot(dx, dy).coerceAtLeast(1f)
                    val nx = -dy / dist
                    val ny = dx / dist
                    val wave = sin(pulseT * 2f * PI.toFloat() + i * 0.9f) * 14f
                    val bendMag = (dist * 0.34f).coerceIn(18f, 95f)
                    val dirSign = if ((i + edgeIdx) % 2 == 0) 1f else -1f

                    val c1x = p1.first + dx * 0.30f + nx * (bendMag * dirSign + wave)
                    val c1y = p1.second + dy * 0.30f + ny * (bendMag * dirSign + wave)
                    val c2x = p1.first + dx * 0.72f - nx * (bendMag * 0.85f * dirSign - wave * 0.7f)
                    val c2y = p1.second + dy * 0.72f - ny * (bendMag * 0.85f * dirSign - wave * 0.7f)

                    curvePath.reset()
                    curvePath.moveTo(p1.first, p1.second)
                    curvePath.cubicTo(c1x, c1y, c2x, c2y, p2.first, p2.second)

                    drawPath(
                        path = curvePath,
                        color = col.copy(alpha = if (isHighlighted) 0.94f else if (animZoom > 0.3f) 0.72f else 0.48f),
                        style = Stroke(width = if (isHighlighted) 4.4f else 2.5f, cap = StrokeCap.Round)
                    )

                    // Glowing impulse traveling smoothly along the exact curved Cubic Bezier neuron!
                    val ep = (pulseT + i * 0.17f + edgeIdx * 0.35f) % 1f
                    val u = 1f - ep
                    val ex = u * u * u * p1.first + 3f * u * u * ep * c1x + 3f * u * ep * ep * c2x + ep * ep * ep * p2.first
                    val ey = u * u * u * p1.second + 3f * u * u * ep * c1y + 3f * u * ep * ep * c2y + ep * ep * ep * p2.second
                    drawCircle(color = col.copy(alpha = 0.55f), radius = 6.2f, center = Offset(ex, ey))
                    drawCircle(color = Color.White.copy(alpha = 0.95f), radius = 2.8f, center = Offset(ex, ey))
                }
            }

            // 4. Draw Glowing Memory Nodes
            for (i in memNodes.indices) {
                val p = projList[i] ?: continue
                val col = LOBE_COLORS[memNodes[i].lobeIdx % LOBE_COLORS.size]
                val r = (p.third / 34f).coerceIn(5.5f, 16f)
                val center = Offset(p.first, p.second)
                if (focusFactIndex == i) {
                    val ringR = r * (2.0f + 0.35f * sin(pulseT * 2f * PI.toFloat()))
                    drawCircle(color = Color.White.copy(alpha = 0.92f), radius = ringR, center = center, style = Stroke(width = 3.5f))
                }
                drawCircle(color = col.copy(alpha = 0.32f), radius = r * 1.9f, center = center)
                drawCircle(color = col, radius = r, center = center)
                drawCircle(color = Color.White.copy(alpha = 0.9f), radius = r * 0.42f, center = center)
            }
        }

        // Top-Left Live Neural Point Counter, Training Status & Colorful Lobes HUD
        val trainedSkillCount = remember(facts) {
            facts.count { it.category == "skill" || it.fact.startsWith("SKILL [") }
        }
        val totalNeuronPoints = remember(cloudParticles.size, facts.size) {
            cloudParticles.size + (facts.size * 18)
        }
        val synapseCount = remember(memNodes.size) { memNodes.size * 2 }
        val latestTrainedLabel = remember(facts) {
            facts.lastOrNull()?.fact
                ?.removePrefix("SKILL [")
                ?.substringBefore("]")
                ?.take(28)
                .orEmpty()
        }

        // Compact Top Telemetry Pill (keeps 3D Brain 100% unobstructed!)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xBF070E1D),
            border = BorderStroke(1.dp, Color(0x5522D3EE)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 10.dp, end = 64.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "$totalNeuronPoints PTS • ${facts.size} NODES • $synapseCount SYN • $trainedSkillCount SKILLS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF22D3EE),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (latestTrainedLabel.length >= 4) {
                    Text(
                        text = "LATEST SKILL: $latestTrainedLabel",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF34D399),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Sleek Single-Row Bottom Legend Strip (zero overlap with 3D Brain)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xBF070E1D),
            border = BorderStroke(1.dp, Color(0x44A855F7)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            val shortLabels = listOf("Code", "Prefs", "People", "Saved", "System")
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                shortLabels.forEachIndexed { idx, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(LOBE_COLORS[idx])
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(label, fontSize = 11.sp, color = Color(0xFFE2E8F0), maxLines = 1)
                    }
                }
            }
        }

        // Top-Right Zoom +/- & Reset Controls
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SmallBrainIconButton(Icons.Default.Add, "Zoom In") {
                onSelectFact(null)
                manualZoom = (manualZoom + 0.28f).coerceAtMost(0.95f)
            }
            SmallBrainIconButton(Icons.Default.Remove, "Zoom Out") {
                onSelectFact(null)
                manualZoom = (manualZoom - 0.28f).coerceAtLeast(0f)
            }
            SmallBrainIconButton(Icons.Default.Refresh, "Reset View") {
                onSelectFact(null)
                manualZoom = 0f
                userPitch = 0.14f
            }
        }

        // Focused Memory Node Inspector Card (pops up automatically on Save or Node Tap!)
        val inspectedFact = focusFactIndex?.let { facts.getOrNull(it) }
        if (inspectedFact != null && focusFactIndex != null) {
            val col = LOBE_COLORS[categoryToLobe(inspectedFact.category)]
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xE60C162C),
                border = BorderStroke(1.5.dp, col),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 78.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MEMORY NODE #${focusFactIndex + 1} • ${inspectedFact.category.uppercase()}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = col
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = inspectedFact.fact,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onDeleteFact(focusFactIndex) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFEF4444))
                    }
                    IconButton(onClick = { onSelectFact(null) }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }
            }
        }

        // Bottom Interactive Save & Fly-To Bar (Archer AI style!)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xE60C162C),
            border = BorderStroke(1.dp, Color(0x6638BDF8)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextButton(
                    onClick = {
                        onSelectFact(null)
                        manualZoom = 0f
                        userPitch = 0.14f
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Reset", fontSize = 12.sp, color = Color(0xFFCBD5E1), fontWeight = FontWeight.SemiBold)
                }

                OutlinedTextField(
                    value = quickInput,
                    onValueChange = { quickInput = it },
                    placeholder = { Text("Save a new memory...", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0x3338BDF8)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        val txt = quickInput.trim()
                        if (txt.isNotBlank()) {
                            onQuickSaveMemory(txt)
                            quickInput = ""
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Save", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                }
            }
        }
    }
}

@Composable
private fun SmallBrainIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0xCC0A1224),
        border = BorderStroke(1.dp, Color(0x5538BDF8)),
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = desc, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
        }
    }
}
