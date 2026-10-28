package com.bitchat.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.bitchat.android.R
import com.bitchat.android.ui.theme.BitchatFontFamily

/**
 * A small "?" after a setting. Tapping it opens a bubble where kitty explains what the option
 * does and why it matters; tapping anywhere else closes it.
 */
@Composable
internal fun HelpDot(text: Int, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    Box(modifier) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = stringResource(R.string.cd_help),
            tint = if (open) colorScheme.primary else colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(28.dp)
                .clip(CircleShape)
                .clickable { open = !open }
                .padding(5.dp)
        )
        if (open) {
            val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
            val margin = with(LocalDensity.current) { 12.dp.roundToPx() }
            Popup(
                popupPositionProvider = remember(gap, margin) { BelowOrAbove(gap, margin) },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.primary),
                    shadowElevation = 8.dp,
                    modifier = Modifier.widthIn(max = 340.dp).clickable { open = false }
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        ChatbitLogo(height = 30.dp, dressed = true)
                        Text(
                            text = stringResource(text),
                            fontFamily = BitchatFontFamily,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/** Centred on the screen, under the "?" when it fits, above it otherwise. */
private class BelowOrAbove(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(margin)
        val below = anchorBounds.bottom + gap
        val y = if (below + popupContentSize.height <= windowSize.height - margin) below
            else (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(margin)
        return IntOffset(x, y)
    }
}
