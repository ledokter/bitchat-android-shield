package com.bitchat.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
    // decided once: the setup flow keeps going (duress step) after the PIN itself is saved
    val setup = remember { !SecurityPreferences.hasPin.value }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
    ) {
        ChatbitLogo(height = 72.dp)
        if (setup || !hasPin) PinCreate() else PinPad(onDuress)
    }
}

/**
 * First start: create the PIN (typed twice), then the duress PIN with an explanation of what it
 * is for (typed twice, must differ; "Later" skips it). "No lock" turns the app lock off.
 */
@Composable
private fun PinCreate() {
    var duressStep by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    val colors = MaterialTheme.colorScheme

    if (duressStep) {
        Text(
            stringResource(R.string.duress_title),
            fontFamily = BitchatFontFamily, fontSize = 18.sp, color = colors.primary
        )
        Text(
            stringResource(R.string.duress_onboarding_explain),
            fontFamily = BitchatFontFamily, fontSize = 13.sp, color = colors.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
    Keypad(
        title = error?.let { stringResource(it) } ?: when {
            first == null && !duressStep -> stringResource(R.string.pin_create, SecurityPreferences.PIN_MIN)
            first == null -> stringResource(R.string.duress_create)
            else -> stringResource(R.string.pin_create_confirm)
        },
        isError = error != null,
        enabled = true,
        onSubmit = { pin ->
            val firstPin = first
            error = null
            when {
                firstPin == null -> if (SecurityPreferences.isValidPin(pin)) first = pin
                firstPin != pin -> { first = null; error = R.string.pin_mismatch }
                !duressStep -> { SecurityPreferences.setPin(pin); first = null; duressStep = true }
                SecurityPreferences.setDuressPin(pin) -> AppLock.unlock()
                else -> { first = null; error = R.string.duress_same_as_pin }
            }
        }
    )
    androidx.compose.material3.TextButton(onClick = {
        if (!duressStep) SecurityPreferences.setAppLock(false)
        AppLock.unlock()
    }) {
        Text(
            stringResource(if (duressStep) R.string.duress_later else R.string.pin_skip_lock),
            fontFamily = BitchatFontFamily, fontSize = 13.sp, color = colors.onBackground.copy(alpha = 0.7f)
        )
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
