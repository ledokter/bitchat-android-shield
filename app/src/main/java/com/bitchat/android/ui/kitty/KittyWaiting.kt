package com.bitchat.android.ui.kitty

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.bitchat.android.ui.ChatbitLogo
import kotlin.math.sin

private val INK = Color(0xFF1A1A1A)
private val FUR = Color(0xFFF5F5F5)
private val SHADE = Color(0xFFDADADA)

/**
 * Kitty waiting with crossed arms, tapping a foot, while a conversation loads.
 * No text on purpose: it shows that something is on its way, not how.
 */
@Composable
fun KittyWaiting(modifier: Modifier = Modifier, height: Dp = 120.dp) {
    val loop = rememberInfiniteTransition(label = "kittyWait")
    // 0..1 over 2.4 s: four foot taps, then a sigh-like head tilt
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "t")
    val tapUp = ((t * 8).toInt() % 2 == 0) && t < 0.5f
    val tilt = if (t in 0.6f..0.9f) sin((t - 0.6f) / 0.3f * Math.PI.toFloat()) * 7f else 0f

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val headHeight = height * 0.5f
        ChatbitLogo(
            height = headHeight, dressed = true,
            modifier = Modifier
                .graphicsLayer { rotationZ = tilt; transformOrigin = TransformOrigin(0.5f, 1f) }
                .offset(y = headHeight * 0.06f)
        )
        // body on the same pixel grid as the head: 24 cells wide, 14 high
        Canvas(Modifier.height(height * 0.5f).aspectRatio(24f / 14f)) {
            val c = size.width / 24f
            fun px(x: Int, y: Int, w: Int, h: Int, color: Color) =
                drawRect(color, Offset(x * c, y * c), Size(w * c, h * c))
            // body
            px(6, 0, 12, 12, INK); px(7, 0, 10, 11, FUR); px(8, 10, 8, 1, SHADE)
            // crossed arms: the lower one sticks out on the right, the upper one on the left
            // and rests on it, so the two forearms read as folded across the chest
            px(9, 4, 12, 4, INK); px(10, 5, 10, 2, FUR); px(10, 6, 10, 1, SHADE); px(19, 5, 1, 2, INK)
            px(3, 2, 13, 4, INK); px(4, 3, 11, 2, FUR); px(4, 4, 11, 1, SHADE); px(14, 3, 1, 1, INK)
            // feet: the right one taps
            px(7, 12, 4, 2, INK); px(8, 12, 2, 1, FUR)
            val lift = if (tapUp) 1 else 0
            px(13, 12 - lift, 4, 2, INK); px(14, 12 - lift, 2, 1, FUR)
        }
        // three dots filling in turn
        Row(Modifier.offset(y = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                val on = ((t * 3).toInt() % 3) >= i
                Box(
                    Modifier.size(7.dp).alpha(if (on) 1f else 0.25f).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/** True while a location channel's conversation is still loading; the header's route arrows spin meanwhile. */
object ConversationLoading {
    val active = kotlinx.coroutines.flow.MutableStateFlow(false)
}
