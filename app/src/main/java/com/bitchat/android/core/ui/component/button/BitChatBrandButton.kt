package com.bitchat.android.core.ui.component.button

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bitchat.android.ui.ChatbitLogo
import com.bitchat.android.ui.rememberPressScale
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MultiClickThreshold = 300.milliseconds

@Composable
fun BitChatBrandButton(
    onClick: () -> Unit,
    onTripleClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
) {
    var tapCount by remember { mutableIntStateOf(0) }
    var resetJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnTripleClick by rememberUpdatedState(onTripleClick)

    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)

    // A plain Box rather than an IconButton: IconButton insists on drawing a ripple, which was the
    // only press background left in the header once every other control moved to scale-only
    // feedback.
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = contentDescription
            ) {
                tapCount += 1
                resetJob?.cancel()

                if (tapCount == 3) {
                    tapCount = 0
                    resetJob = null
                    currentOnTripleClick()
                } else {
                    resetJob = coroutineScope.launch {
                        delay(MultiClickThreshold)
                        if (tapCount == 1) {
                            currentOnClick()
                        }
                        tapCount = 0
                        resetJob = null
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // chatbit cat; its bow follows the accent colour
        ChatbitLogo(
            height = iconSize,
            modifier = Modifier
                .scale(pressScale)
                .semantics { this.contentDescription = contentDescription },
        )
    }
}
