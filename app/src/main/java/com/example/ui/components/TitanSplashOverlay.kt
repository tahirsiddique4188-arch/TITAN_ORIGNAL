package com.example.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * TitanSplashOverlay v3.5 — Ultra-Clean Cinema-Grade Cyber-Heart "T" Opening Experience
 *
 * - Pure transparent Cyber-Heart "T" logo (ZERO black box/background behind logo!)
 * - Clean deep midnight-obsidian radial backdrop with breathing cyan-violet ambient nebula
 * - Silky-smooth precision dual-arc orbital ring + synchronized soft heartbeat pulse ripple
 * - Crisp "TITAN" metallic title reveal + refined 3.2s cinematic sci-fi boot chord SFX
 */
@Composable
fun TitanSplashOverlay() {
    var visible by remember { mutableStateOf(true) }
    if (!visible) return

    val context = LocalContext.current

    val logoScale = remember { Animatable(0.76f) }
    val logoAlpha = remember { Animatable(0f) }
    val auraAlpha = remember { Animatable(0f) }
    val ringRotation = remember { Animatable(0f) }
    val rippleProgress = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textSlideY = remember { Animatable(18f) }
    val overallAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Play clean cinematic boot chord SFX in background (respects silent/vibrate mode)
        launch(Dispatchers.IO) {
            playCinematicTitanBootSfx(context)
        }

        // Smooth continuous orbital rotation across the full 3.6s splash
        launch {
            ringRotation.animateTo(
                targetValue = 300f,
                animationSpec = tween(durationMillis = 3600, easing = LinearEasing)
            )
        }

        // Phase 1 (0 - 650ms): Ambient aura blooms + transparent Cyber-Heart fades & glides in
        launch {
            auraAlpha.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        }
        launch {
            logoAlpha.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
        logoScale.animateTo(1.0f, tween(750, easing = FastOutSlowInEasing))

        // Phase 2 (750 - 1550ms): Gentle realistic double heartbeat (lub-dub) + clean expanding ripple
        launch {
            rippleProgress.snapTo(0f)
            rippleProgress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
        logoScale.animateTo(1.07f, tween(170, easing = FastOutSlowInEasing))
        logoScale.animateTo(0.99f, tween(150, easing = FastOutSlowInEasing))
        delay(90)
        logoScale.animateTo(1.10f, tween(180, easing = FastOutSlowInEasing))
        logoScale.animateTo(1.0f, tween(280, easing = FastOutSlowInEasing))

        // Phase 3 (1550 - 2950ms): Crisp TITAN title reveal + calm breathing hold
        launch {
            textAlpha.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        }
        launch {
            textSlideY.animateTo(0f, tween(550, easing = FastOutSlowInEasing))
        }
        delay(1400)

        // Phase 4 (2950 - 3550ms): Silky fade-out into Titan IDE
        launch {
            logoScale.animateTo(1.06f, tween(520, easing = FastOutSlowInEasing))
        }
        overallAlpha.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
        visible = false
    }

    val cyan = Color(0xFF22D3EE)
    val violet = Color(0xFFA855F7)
    val deepBgCenter = Color(0xFF0A1026)
    val deepBgEdge = Color(0xFF03050C)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(999f)
            .graphicsLayer { alpha = overallAlpha.value }
            .background(
                Brush.radialGradient(
                    colors = listOf(deepBgCenter, Color(0xFF050816), deepBgEdge)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Clean precision Canvas: ONLY smooth ambient glow + single sleek orbital arc + soft pulse ripple
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f - 28.dp.toPx()
            val center = Offset(cx, cy)
            val baseR = minOf(size.width, size.height) * 0.29f

            // 1. Soft cyan-violet ambient nebula behind transparent logo (zero hard edge)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cyan.copy(alpha = 0.20f * auraAlpha.value),
                        violet.copy(alpha = 0.12f * auraAlpha.value),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseR * 1.9f
                ),
                radius = baseR * 1.9f,
                center = center
            )

            // 2. Smooth expanding heartbeat pulse ring
            val rp = rippleProgress.value
            if (rp > 0.01f && rp < 0.99f) {
                val r = baseR * (0.85f + rp * 1.15f)
                val a = (1f - rp) * 0.42f * auraAlpha.value
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(cyan.copy(alpha = a), violet.copy(alpha = a), cyan.copy(alpha = a)),
                        center = center
                    ),
                    radius = r,
                    center = center,
                    style = Stroke(width = (2.6f * (1f - rp * 0.6f)).dp.toPx())
                )
            }

            // 3. Single precision dual-arc orbital energy ring
            val ringRadius = baseR * 1.08f
            val arcTopLeft = Offset(cx - ringRadius, cy - ringRadius)
            val arcSize = Size(ringRadius * 2f, ringRadius * 2f)
            val rot = ringRotation.value
            val ringA = 0.62f * auraAlpha.value

            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        cyan.copy(alpha = ringA),
                        violet.copy(alpha = ringA * 0.85f),
                        Color.Transparent
                    ),
                    center = center
                ),
                startAngle = rot,
                sweepAngle = 125f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        violet.copy(alpha = ringA),
                        cyan.copy(alpha = ringA * 0.8f),
                        Color.Transparent
                    ),
                    center = center
                ),
                startAngle = rot + 180f,
                sweepAngle = 125f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Centerpiece: Transparent Cyber-Heart "T" Logo + Crisp TITAN Title
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.offset(y = (-8).dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.titan_cyber_heart_logo),
                contentDescription = "Titan",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(216.dp)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    }
            )

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "T I T A N",
                color = Color(0xFFF1F5F9),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 9.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha.value
                    translationY = textSlideY.value
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "AI ASSISTANT",
                color = cyan.copy(alpha = 0.80f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 5.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha.value * 0.9f
                    translationY = textSlideY.value
                }
            )
        }
    }
}

/**
 * Refined 3.2-second cinema-grade sci-fi boot soundscape:
 * Warm sub-bass ignition swell + soft double heartbeat pulse + lush futuristic harmonic chord.
 * Respects phone silent/vibrate mode and uses soft envelope shaping to avoid clicks.
 */
private suspend fun playCinematicTitanBootSfx(context: Context) = withContext(Dispatchers.IO) {
    var track: AudioTrack? = null
    try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am != null && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return@withContext

        val sampleRate = 24000
        val durationSec = 3.2
        val totalSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(totalSamples)

        var phaseBass = 0.0
        var phasePad1 = 0.0
        var phasePad2 = 0.0
        var phasePad3 = 0.0
        var phaseShimmer = 0.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate

            // 1. Warm sub-bass ignition swell (55Hz -> 82.4Hz)
            val bassFreq = 55.0 + 27.4 * (1.0 - exp(-t * 1.4))
            phaseBass += 2.0 * PI * bassFreq / sampleRate
            val bassEnv = (t / 0.45).coerceIn(0.0, 1.0) * exp(-((t - 1.3).coerceAtLeast(0.0)) * 1.1)
            val bass = sin(phaseBass) * 0.28 * bassEnv

            // 2. Soft double heartbeat pulse at 0.80s and 1.18s
            val beat1 = if (t in 0.78..1.05) {
                val dt = t - 0.78
                sin(2.0 * PI * (68.0 - 24.0 * dt) * dt) * exp(-dt * 15.0) * 0.34
            } else 0.0
            val beat2 = if (t in 1.14..1.48) {
                val dt = t - 1.14
                sin(2.0 * PI * (76.0 - 28.0 * dt) * dt) * exp(-dt * 12.0) * 0.40
            } else 0.0

            // 3. Lush futuristic D-major9 shimmer chord blooming from 1.05s
            val chordEnv = if (t > 1.0) {
                val dt = t - 1.0
                (dt / 0.45).coerceIn(0.0, 1.0) * exp(-((dt - 0.85).coerceAtLeast(0.0)) * 1.35)
            } else 0.0
            phasePad1 += 2.0 * PI * 293.66 / sampleRate // D4
            phasePad2 += 2.0 * PI * 440.00 / sampleRate // A4
            phasePad3 += 2.0 * PI * 554.37 / sampleRate // C#5
            phaseShimmer += 2.0 * PI * 659.25 / sampleRate // E5
            val chord = (sin(phasePad1) * 0.14 + sin(phasePad2) * 0.11 + sin(phasePad3) * 0.08 + sin(phaseShimmer) * 0.06) * chordEnv

            // Master smooth fade-in and fade-out
            val fadeIn = (t / 0.08).coerceIn(0.0, 1.0)
            val fadeOut = ((durationSec - t) / 0.35).coerceIn(0.0, 1.0)
            val sample = ((bass + beat1 + beat2 + chord) * fadeIn * fadeOut).coerceIn(-0.92, 0.92)
            pcm[i] = (sample * 32767.0).toInt().toShort()
        }

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(pcm.size * 2)

        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcm, 0, pcm.size)
        track.setVolume(0.68f)
        track.play()
        delay(3250)
    } catch (_: Exception) {
    } finally {
        try {
            track?.stop()
            track?.release()
        } catch (_: Exception) {}
    }
}
