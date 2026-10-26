package com.bitchat.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.R
import com.bitchat.android.security.AppLock
import com.bitchat.android.security.SecurityPreferences
import com.bitchat.android.security.SecurityPreferences.PinResult
import com.bitchat.android.ui.theme.BitchatFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opaque cover shown while chatbit is locked; it swallows every touch underneath. Only chatbit's
 * PIN unlocks it (no fingerprint / face: they can be forced). The duress PIN unlocks exactly like
 * the real one after [onDuress] wiped everything, so whoever forces the unlock sees an empty app.
 * Without a PIN yet, the user creates one here.
 */
@Composable
fun AppLockScreen(onDuress: () -> Unit = {}) {
    val hasPin by SecurityPreferences.hasPin.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
    ) {
        ChatbitLogo(height = 96.dp)
        if (hasPin) PinPad(onDuress) else PinCreate()
    }
}

/** First PIN: type it, type it again, saved and unlocked. "No lock" turns the app lock off. */
@Composable
private fun PinCreate() {
    var first by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableStateOf(false) }
    Keypad(
        title = when {
            mismatch -> stringResource(R.string.pin_mismatch)
            first == null -> stringResource(R.string.pin_create, SecurityPreferences.PIN_MIN)
            else -> stringResource(R.string.pin_create_confirm)
        },
        isError = mismatch,
        enabled = true,
        onSubmit = { pin ->
            val firstPin = first
            when {
                firstPin == null -> if (SecurityPreferences.isValidPin(pin)) { first = pin; mismatch = false }
                firstPin == pin -> { SecurityPreferences.setPin(pin); AppLock.unlock() }
                else -> { first = null; mismatch = true }
            }
        }
    )
    androidx.compose.material3.TextButton(onClick = { SecurityPreferences.setAppLock(false); AppLock.unlock() }) {
        Text(stringResource(R.string.pin_skip_lock), fontFamily = BitchatFontFamily, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }
}

@Composable
private fun PinPad(onDuress: () -> Unit) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var waitMs by remember { mutableLongStateOf(SecurityPreferences.pinLockoutRemainingMs()) }
    LaunchedEffect(waitMs > 0) {
        while (waitMs > 0) { delay(1000); waitMs = SecurityPreferences.pinLockoutRemainingMs() }
    }
    Keypad(
        title = when {
            waitMs > 0 -> stringResource(R.string.pin_wait, ((waitMs + 999) / 1000).toInt())
            error -> stringResource(R.string.pin_wrong)
            else -> stringResource(R.string.pin_enter)
        },
        isError = error || waitMs > 0,
        enabled = waitMs <= 0 && !checking,
        onSubmit = { typed ->
            checking = true
            scope.launch {
                val result = withContext(Dispatchers.Default) { SecurityPreferences.checkPin(typed) }
                checking = false
                when (result) {
                    PinResult.OK -> AppLock.unlock()
                    PinResult.DURESS -> { onDuress(); AppLock.unlock() }
                    PinResult.WRONG, PinResult.LOCKED_OUT -> {
                        error = true
                        waitMs = SecurityPreferences.pinLockoutRemainingMs()
                    }
                }
            }
        }
    )
}

/** Dots + 3×4 keypad; [onSubmit] gets the typed digits when OK is pressed. */
@Composable
private fun Keypad(title: String, isError: Boolean, enabled: Boolean, onSubmit: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    Text(title, fontFamily = BitchatFontFamily, color = if (isError) colors.error else colors.onBackground)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(maxOf(SecurityPreferences.PIN_MIN, pin.length)) { i ->
            Box(
                Modifier.size(14.dp).clip(CircleShape)
                    .background(if (i < pin.length) colors.primary else colors.onBackground.copy(alpha = 0.2f))
            )
        }
    }
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(68.dp).clip(CircleShape)
                            .background(colors.surfaceVariant)
                            .clickable(enabled = enabled) {
                                when (k) {
                                    "⌫" -> pin = pin.dropLast(1)
                                    "OK" -> if (pin.length >= SecurityPreferences.PIN_MIN) { onSubmit(pin); pin = "" }
                                    else -> if (pin.length < SecurityPreferences.PIN_MAX) pin += k
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (k == "⌫") Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.pin_delete), tint = colors.onSurface)
                        else Text(k, fontFamily = BitchatFontFamily, fontSize = if (k == "OK") 16.sp else 24.sp, color = if (k == "OK") colors.primary else colors.onSurface)
                    }
                }
            }
        }
    }
}
