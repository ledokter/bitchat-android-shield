package com.bitchat.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bitchat.android.R

/** The chatbit pixel-art cat; its bow follows the chosen accent colour. */
@Composable
fun ChatbitLogo(height: Dp = 64.dp, modifier: Modifier = Modifier) {
    Box(modifier.height(height).aspectRatio(24f / 21f)) {
        Image(painterResource(R.drawable.chatbit_cat), contentDescription = null, modifier = Modifier.fillMaxSize())
        Image(
            painterResource(R.drawable.chatbit_bow),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxSize()
        )
        Image(painterResource(R.drawable.chatbit_bow_knot), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}
