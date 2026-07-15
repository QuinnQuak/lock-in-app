package com.example.lockin

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// The reactive mascot companion ("blob buddy") from CONTEXT.md's Design
// Direction: a round blob with big eyes and tiny stub limbs that recolors to
// the active theme and reacts to app state. Appears on Home, Profile, session
// status, and loading states.
enum class MascotMood {
    IDLE,      // idle/breathing loop while compliant
    HAPPY,     // bounce + sparkle on a completed lock-in
    BREAK,     // droop + tears on a break
    SLEEPING,  // zzz when there's no active session
}

@Composable
fun Mascot(
    mood: MascotMood,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    // Defaults to whatever the user has equipped (provided at the composition
    // root), so every Mascot wears it. Call sites that preview a specific
    // accessory (the trophy case / shop cells) pass one explicitly.
    accessory: MascotAccessory = LocalEquippedAccessory.current,
) {
    val bodyColor = MaterialTheme.colorScheme.primary
    val faceColor = MaterialTheme.colorScheme.onPrimary

    val infinite = rememberInfiniteTransition(label = "mascotMood")
    // Idle: a gentle vertical breathing squish, always running so the
    // mascot never reads as a static image.
    val breathe by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val happyBounce by infinite.animateFloat(
        initialValue = 0f,
        targetValue = -14f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "happyBounce"
    )
    val zzzFloat by infinite.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "zzzFloat"
    )

    val scaleY = when (mood) {
        MascotMood.IDLE -> breathe
        MascotMood.BREAK -> 0.9f
        MascotMood.HAPPY -> 1f
        MascotMood.SLEEPING -> 0.94f
    }
    val translateY = when (mood) {
        MascotMood.HAPPY -> happyBounce
        MascotMood.BREAK -> 8f
        else -> 0f
    }
    val bodyAlpha = if (mood == MascotMood.SLEEPING) 0.7f else 1f

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .size(size)
                .scale(scaleX = 1f, scaleY = scaleY)
                .offset { androidx.compose.ui.unit.IntOffset(0, translateY.roundToInt()) }
        ) {
            val w = this.size.width
            val h = this.size.height

            // Stub limbs, drawn behind the body.
            drawOval(
                color = bodyColor.copy(alpha = bodyAlpha),
                topLeft = Offset(w * 0.02f, h * 0.62f),
                size = Size(w * 0.16f, h * 0.22f)
            )
            drawOval(
                color = bodyColor.copy(alpha = bodyAlpha),
                topLeft = Offset(w * 0.82f, h * 0.62f),
                size = Size(w * 0.16f, h * 0.22f)
            )

            // Body: a simple round blob.
            drawOval(
                color = bodyColor.copy(alpha = bodyAlpha),
                topLeft = Offset(w * 0.06f, h * 0.08f),
                size = Size(w * 0.88f, h * 0.84f)
            )

            val eyeStroke = Stroke(width = w * 0.045f)
            when (mood) {
                MascotMood.IDLE, MascotMood.HAPPY -> {
                    // Big round eyes with a highlight -- "expressive," per spec.
                    val eyeR = w * 0.06f
                    drawCircle(faceColor, radius = eyeR, center = Offset(w * 0.36f, h * 0.42f))
                    drawCircle(faceColor, radius = eyeR, center = Offset(w * 0.64f, h * 0.42f))
                    val hl = w * 0.02f
                    drawCircle(bodyColor, radius = hl, center = Offset(w * 0.375f, h * 0.405f))
                    drawCircle(bodyColor, radius = hl, center = Offset(w * 0.655f, h * 0.405f))
                    // Open smile, wider when happy.
                    val smileSweep = if (mood == MascotMood.HAPPY) 160f else 120f
                    drawArc(
                        color = faceColor,
                        startAngle = 90f - smileSweep / 2f,
                        sweepAngle = smileSweep,
                        useCenter = false,
                        style = eyeStroke,
                        topLeft = Offset(w * 0.36f, h * 0.5f),
                        size = Size(w * 0.28f, h * 0.22f)
                    )
                }
                MascotMood.BREAK -> {
                    // Droopy downturned eyes and a frown.
                    drawArc(
                        color = faceColor,
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        style = eyeStroke,
                        topLeft = Offset(w * 0.30f, h * 0.40f),
                        size = Size(w * 0.14f, h * 0.12f)
                    )
                    drawArc(
                        color = faceColor,
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        style = eyeStroke,
                        topLeft = Offset(w * 0.56f, h * 0.40f),
                        size = Size(w * 0.14f, h * 0.12f)
                    )
                    drawArc(
                        color = faceColor,
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        style = eyeStroke,
                        topLeft = Offset(w * 0.36f, h * 0.62f),
                        size = Size(w * 0.28f, h * 0.16f)
                    )
                }
                MascotMood.SLEEPING -> {
                    // Closed, contented eyes -- flat curves.
                    drawArc(
                        color = faceColor,
                        startAngle = 20f,
                        sweepAngle = 140f,
                        useCenter = false,
                        style = eyeStroke,
                        topLeft = Offset(w * 0.30f, h * 0.42f),
                        size = Size(w * 0.16f, h * 0.10f)
                    )
                    drawArc(
                        color = faceColor,
                        startAngle = 20f,
                        sweepAngle = 140f,
                        useCenter = false,
                        style = eyeStroke,
                        topLeft = Offset(w * 0.56f, h * 0.42f),
                        size = Size(w * 0.16f, h * 0.10f)
                    )
                }
            }
        }

        when (mood) {
            MascotMood.HAPPY -> Text(
                text = "✨",
                fontSize = (size.value * 0.28f).sp,
                modifier = Modifier.align(Alignment.TopEnd)
            )
            MascotMood.BREAK -> Text(
                text = "💧",
                fontSize = (size.value * 0.22f).sp,
                modifier = Modifier.align(Alignment.BottomStart)
            )
            MascotMood.SLEEPING -> Text(
                text = "💤",
                fontSize = (size.value * 0.24f).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { androidx.compose.ui.unit.IntOffset(0, zzzFloat.roundToInt()) }
            )
            MascotMood.IDLE -> {}
        }

        // The equipped accessory, drawn as an emoji at its slot. Slots are
        // centered (HEAD top, FACE over the eyes, NECK low on the body) so they
        // never land on the corner mood overlays above.
        if (accessory != MascotAccessory.NONE) {
            val v = size.value
            val (align, dy, fontFactor) = when (accessory.slot) {
                AccessorySlot.HEAD -> Triple(Alignment.TopCenter, v * 0.02f, 0.34f)
                AccessorySlot.FACE -> Triple(Alignment.Center, -v * 0.08f, 0.30f)
                AccessorySlot.NECK -> Triple(Alignment.BottomCenter, -v * 0.16f, 0.26f)
            }
            Text(
                text = accessory.emoji,
                fontSize = (v * fontFactor).sp,
                modifier = Modifier
                    .align(align)
                    .offset(y = dy.dp)
            )
        }
    }
}
