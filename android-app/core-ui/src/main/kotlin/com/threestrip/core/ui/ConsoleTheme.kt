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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
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
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
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
                    modifier = Modifier.width(spec.width).height(spec.height),
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
        repeat(segments) { idx ->
            val top = idx * (segHeight + gap)
            val segCenter = idx.toFloat() / (segments - 1).coerceAtLeast(1)
            val active = when (mode) {
                ConsoleMode.LISTENING -> kotlin.math.abs(segCenter - sweep) < 0.34f
                ConsoleMode.THINKING, ConsoleMode.SPEAKING -> kotlin.math.abs(segCenter - sweep) < 0.18f
                else -> true
            }
            val alpha = if (active) intensity else intensity * 0.18f
            drawReferenceSegment(
                top = top,
                height = segHeight,
                alpha = alpha,
            )
        }
    }
}

private fun DrawScope.drawReferenceSegment(
    top: Float,
    height: Float,
    alpha: Float,
) {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                ConsoleRed.copy(alpha = alpha),
                ConsoleRed.copy(alpha = alpha * 0.92f),
            )
        ),
        topLeft = Offset(size.width * 0.04f, top),
        size = Size(size.width * 0.92f, height),
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
