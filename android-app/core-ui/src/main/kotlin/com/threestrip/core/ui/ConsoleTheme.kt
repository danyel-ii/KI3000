package com.threestrip.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.threestrip.core.storage.ConsoleMode
import kotlinx.coroutines.delay

val ConsoleRed = Color(0xFFFF2E2E)
val ConsoleDim = Color(0xFF300505)
val ConsoleBlack = Color(0xFF020202)
val ConsoleSlate = Color(0xFF3A3A3A)

@Composable
fun ThreeStripTheme(content: @Composable () -> Unit) {
    MaterialTheme { Surface(color = ConsoleBlack, content = content) }
}

@Composable
fun BootOverlay(visible: Boolean, onFinished: () -> Unit) {
    if (!visible) return
    Box(
        modifier = Modifier.fillMaxSize().background(ConsoleBlack),
        contentAlignment = Alignment.Center
    ) {
        Text("THREESTRIP", color = ConsoleRed, fontWeight = FontWeight.Bold)
    }
    LaunchedEffect(Unit) {
        delay(1500)
        onFinished()
    }
}

@Composable
fun ThreeStripConsole(
    mode: ConsoleMode,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "console")
    val idlePulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idlePulse"
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "sweep"
    )
    val flash = androidx.compose.runtime.remember { Animatable(0f) }
    LaunchedEffect(mode) {
        if (mode == ConsoleMode.ERROR) {
            repeat(3) {
                flash.animateTo(1f, tween(60))
                flash.animateTo(0f, tween(90))
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(ConsoleBlack), contentAlignment = Alignment.Center) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                StripSpec(width = 62.dp, height = 274.dp, segments = 11),
                StripSpec(width = 64.dp, height = 432.dp, segments = 17),
                StripSpec(width = 62.dp, height = 274.dp, segments = 11),
            ).forEachIndexed { index, spec ->
                ConsoleStrip(
                    modifier = Modifier
                        .width(spec.width)
                        .height(spec.height)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                    segments = spec.segments,
                    intensity = when (mode) {
                        ConsoleMode.BOOT -> 0.2f + index * 0.1f
                        ConsoleMode.IDLE -> idlePulse
                        ConsoleMode.LISTENING -> 0.4f + (((sweep * 1.6f + index * 0.18f) % 1f) * 0.5f)
                        ConsoleMode.THINKING -> 0.35f + (((sweep + index * 0.14f) % 1f) * 0.65f)
                        ConsoleMode.SPEAKING -> 0.45f + (((sweep * 3f + index * 0.21f) % 1f) * 0.55f)
                        ConsoleMode.ERROR -> 0.2f + flash.value * 0.8f
                    },
                    sweep = sweep,
                    mode = mode,
                )
            }
        }
    }
}

@Composable
private fun ConsoleStrip(
    modifier: Modifier,
    segments: Int,
    intensity: Float,
    sweep: Float,
    mode: ConsoleMode,
) {
    Canvas(modifier = modifier) {
        val gap = size.height * 0.03f
        val segHeight = (size.height - gap * (segments - 1)) / segments
        val travel = when (mode) {
            ConsoleMode.IDLE -> (0.04f + sweep * 0.92f).coerceIn(0f, 1f)
            ConsoleMode.LISTENING -> (0.02f + sweep * 0.96f).coerceIn(0f, 1f)
            ConsoleMode.THINKING -> sweep
            ConsoleMode.SPEAKING -> (0.01f + sweep * 0.98f).coerceIn(0f, 1f)
            ConsoleMode.ERROR -> 0.5f
            ConsoleMode.BOOT -> (0.08f + sweep * 0.84f).coerceIn(0f, 1f)
        }
        repeat(segments) { idx ->
            val top = idx * (segHeight + gap)
            val segCenter = idx.toFloat() / (segments - 1).coerceAtLeast(1)
            val active = when (mode) {
                ConsoleMode.LISTENING -> kotlin.math.abs(segCenter - travel) < 0.38f
                ConsoleMode.THINKING, ConsoleMode.SPEAKING -> kotlin.math.abs(segCenter - travel) < 0.22f
                else -> true
            }
            val alpha = if (active) intensity else intensity * 0.18f
            drawReferenceSegment(
                top = top,
                height = segHeight,
                alpha = alpha,
                lightBand = travel,
                bandStrength = when (mode) {
                    ConsoleMode.IDLE -> 0.22f
                    ConsoleMode.LISTENING -> 0.34f
                    ConsoleMode.THINKING -> 0.48f
                    ConsoleMode.SPEAKING -> 0.58f
                    ConsoleMode.ERROR -> 0.18f
                    ConsoleMode.BOOT -> 0.28f
                },
            )
        }
    }
}

private fun DrawScope.drawReferenceSegment(
    top: Float,
    height: Float,
    alpha: Float,
    lightBand: Float,
    bandStrength: Float,
) {
    val left = size.width * 0.04f
    val width = size.width * 0.92f
    val segmentCenter = (top + (height * 0.5f)) / size.height
    val headDistance = kotlin.math.abs(segmentCenter - lightBand)
    val trailOffset = (lightBand - segmentCenter).coerceAtLeast(0f)
    val scannerHead = (1f - (headDistance / 0.085f)).coerceIn(0f, 1f) * bandStrength
    val scannerTail = (1f - (trailOffset / 0.24f)).coerceIn(0f, 1f) * bandStrength * 0.45f
    val glowBoost = scannerHead + scannerTail
    val baseAlpha = alpha * (0.66f + scannerHead * 0.52f + scannerTail * 0.28f)
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                ConsoleRed.copy(alpha = baseAlpha),
                ConsoleRed.copy(alpha = baseAlpha * 0.9f),
            )
        ),
        topLeft = Offset(left, top),
        size = Size(width, height),
    )
    if (glowBoost > 0.02f) {
        val highlightTop = top + (height * 0.2f)
        val highlightHeight = height * 0.58f
        val beamCenterX = left + width * 0.5f
        val headWidth = width * 0.22f
        val tailWidth = width * 0.58f
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFFFF8F8F).copy(alpha = scannerTail * 0.3f),
                    Color(0xFFFFEAEA).copy(alpha = scannerHead * 0.82f),
                    Color.Transparent,
                )
            ),
            topLeft = Offset(beamCenterX - tailWidth * 0.5f, highlightTop),
            size = Size(tailWidth, highlightHeight),
        )
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFFFFF3F3).copy(alpha = scannerHead * 0.95f),
                    Color.Transparent,
                )
            ),
            topLeft = Offset(beamCenterX - headWidth * 0.5f, top + height * 0.12f),
            size = Size(headWidth, height * 0.7f),
        )
        drawRect(
            color = Color(0xFFFFC8C8).copy(alpha = scannerHead * 0.22f),
            topLeft = Offset(left + width * 0.12f, top + height * 0.08f),
            size = Size(width * 0.76f, height * 0.04f),
        )
        drawRect(
            color = Color(0xFFFF5A5A).copy(alpha = scannerTail * 0.14f),
            topLeft = Offset(left + width * 0.16f, top + height * 0.82f),
            size = Size(width * 0.68f, height * 0.05f),
        )
    }
    drawRect(
        color = Color.Black.copy(alpha = 0.1f),
        topLeft = Offset(left + width * 0.02f, top + height * 0.9f),
        size = Size(width * 0.96f, height * 0.06f),
    )
}

private data class StripSpec(
    val width: Dp,
    val height: Dp,
    val segments: Int,
)

@Composable
fun HiddenOverlayTitle(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().background(ConsoleBlack, RoundedCornerShape(20.dp)).padding(16.dp)
    ) {
        Text(title, color = ConsoleRed, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = Color(0xFFFFB0B0))
    }
}

@Composable
fun SettingsCogButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(ConsoleBlack.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val radius = size.minDimension * 0.28f
            val center = Offset(size.width / 2f, size.height / 2f)
            repeat(8) { index ->
                rotate(index * 45f, center) {
                    drawRoundRect(
                        color = ConsoleSlate,
                        topLeft = Offset(center.x - size.width * 0.05f, center.y - radius - size.height * 0.12f),
                        size = Size(size.width * 0.1f, size.height * 0.18f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )
                }
            }
            drawCircle(color = ConsoleSlate, radius = radius, center = center)
            drawCircle(color = ConsoleBlack, radius = radius * 0.42f, center = center)
        }
    }
}
