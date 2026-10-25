package com.bitchat.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bitchat.android.R
import com.bitchat.android.ui.theme.BitchatFontFamily

/** Opaque cover shown while chatbit is locked; it swallows every touch underneath. */
@Composable
fun AppLockScreen(onUnlock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
    ) {
        ChatbitLogo(height = 96.dp)
        Text(stringResource(R.string.app_lock_locked), fontFamily = BitchatFontFamily, color = MaterialTheme.colorScheme.onBackground)
        Button(onClick = onUnlock) {
            Text(stringResource(R.string.app_lock_unlock), fontFamily = BitchatFontFamily)
        }
    }
}
