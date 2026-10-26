package com.bitchat.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.R
import com.bitchat.android.net.TorPreferenceManager
import com.bitchat.android.security.AppLock
import com.bitchat.android.security.SecurityPreferences
import com.bitchat.android.ui.theme.BitchatFontFamily

/**
 * chatbit protections. By default a one-line summary (everything is already set to the safest
 * value); in advanced mode every switch and delay can be changed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SecuritySettingsCard(advanced: Boolean) {
    val context = LocalContext.current
    val lock by SecurityPreferences.appLock.collectAsState()
    val screenshots by SecurityPreferences.blockScreenshots.collectAsState()
    val presence by SecurityPreferences.sendPresence.collectAsState()
    val killSwitch by SecurityPreferences.killSwitch.collectAsState()
    val clipboard by SecurityPreferences.clipboardClear.collectAsState()
    val ephemeral by SecurityPreferences.ephemeralHours.collectAsState()
    val wipe by SecurityPreferences.autoWipeDays.collectAsState()
    val rotation by TorPreferenceManager.rotationFlow.collectAsState()
    val canLock = AppLock.canLock(context)

    // link previews, photo metadata and lock-screen notifications are always protected
    val active = listOf(rotation > 0, killSwitch, !presence, true, true, ephemeral > 0, true,
        screenshots, lock && canLock, clipboard, wipe > 0).count { it }

    Column {
        AboutSectionLabel(text = stringResource(R.string.security_section))
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AboutHorizontalPadding),
            color = MaterialTheme.colorScheme.surface,
            shape = AboutCardShape
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.security_summary, active, 11),
                    fontFamily = BitchatFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                PinSettings()
                if (!advanced) {
                    SecHint(stringResource(R.string.security_summary_hint))
                    return@Column
                }
                SecSwitch(R.string.sec_lock_title, if (canLock) R.string.sec_lock_desc else R.string.sec_lock_unavailable,
                    lock, enabled = canLock) { SecurityPreferences.setAppLock(it) }
                SecSwitch(R.string.sec_screenshots_title, R.string.sec_screenshots_desc, screenshots) { SecurityPreferences.setBlockScreenshots(it) }
                SecSwitch(R.string.sec_killswitch_title, R.string.sec_killswitch_desc, killSwitch) { SecurityPreferences.setKillSwitch(it) }
                SecSwitch(R.string.sec_presence_title, R.string.sec_presence_desc, presence) { SecurityPreferences.setSendPresence(it) }
                SecSwitch(R.string.sec_clipboard_title, R.string.sec_clipboard_desc, clipboard) { SecurityPreferences.setClipboardClear(it) }

                SecLabel(stringResource(R.string.sec_ephemeral_title))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecurityPreferences.EPHEMERAL_CHOICES.forEach { h ->
                        FilterChip(
                            selected = ephemeral == h,
                            onClick = { SecurityPreferences.setEphemeralHours(h) },
                            label = {
                                Text(
                                    when {
                                        h == 0 -> stringResource(R.string.sec_keep_forever)
                                        h < 24 -> stringResource(R.string.duration_hours, h)
                                        else -> stringResource(R.string.duration_days, h / 24)
                                    },
                                    fontFamily = BitchatFontFamily, fontSize = 11.sp
                                )
                            }
                        )
                    }
                }
                SecLabel(stringResource(R.string.sec_autowipe_title))
                SecHint(stringResource(R.string.sec_autowipe_desc))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecurityPreferences.AUTO_WIPE_CHOICES.forEach { d ->
                        FilterChip(
                            selected = wipe == d,
                            onClick = { SecurityPreferences.setAutoWipeDays(d) },
                            label = {
                                Text(
                                    if (d == 0) stringResource(R.string.tor_rotation_never) else stringResource(R.string.duration_days, d),
                                    fontFamily = BitchatFontFamily, fontSize = 11.sp
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Always-visible switch between the simple settings and every power-user option. */
@Composable
internal fun AdvancedModeToggle(advanced: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AboutHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            SecLabel(stringResource(R.string.advanced_mode_title))
            SecHint(stringResource(R.string.advanced_mode_desc))
        }
        Switch(checked = advanced, onCheckedChange = { SecurityPreferences.setAdvancedMode(it) })
    }
}

@Composable
private fun SecSwitch(title: Int, desc: Int, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            SecLabel(stringResource(title))
            SecHint(stringResource(desc))
        }
        Switch(checked = checked && enabled, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun SecLabel(text: String) {
    Text(text, fontFamily = BitchatFontFamily, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun SecHint(text: String) {
    Text(text, fontFamily = BitchatFontFamily, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** chatbit PIN and duress PIN: always visible, they matter more than any tunable. */
@Composable
private fun PinSettings() {
    val context = LocalContext.current
    val hasPin by SecurityPreferences.hasPin.collectAsState()
    val hasDuress by SecurityPreferences.hasDuress.collectAsState()
    var editing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Boolean?>(null) } // true = PIN, false = duress

    PinRow(
        title = R.string.pin_title, desc = R.string.pin_desc, isSet = hasPin,
        onSet = { editing = true }, onRemove = { SecurityPreferences.setPin(null) }
    )
    if (hasPin) {
        PinRow(
            title = R.string.duress_title, desc = R.string.duress_desc, isSet = hasDuress,
            onSet = { editing = false }, onRemove = { SecurityPreferences.setDuressPin(null) }
        )
    }
    editing?.let { forPin ->
        PinDialog(
            title = stringResource(if (forPin) R.string.pin_title else R.string.duress_title),
            onDismiss = { editing = null },
            onConfirm = { pin ->
                if (forPin) {
                    SecurityPreferences.setPin(pin); editing = null
                } else if (SecurityPreferences.setDuressPin(pin)) {
                    editing = null
                } else {
                    android.widget.Toast.makeText(context, context.getString(R.string.duress_same_as_pin), android.widget.Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

@Composable
private fun PinRow(title: Int, desc: Int, isSet: Boolean, onSet: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            SecLabel(stringResource(title))
            SecHint(stringResource(desc))
        }
        androidx.compose.material3.TextButton(onClick = onSet) {
            Text(stringResource(if (isSet) R.string.pin_change else R.string.pin_set), fontFamily = BitchatFontFamily, fontSize = 12.sp)
        }
        if (isSet) androidx.compose.material3.TextButton(onClick = onRemove) {
            Text(stringResource(R.string.pin_remove), fontFamily = BitchatFontFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PinDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var again by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val valid = SecurityPreferences.isValidPin(pin) && pin == again
    val digits = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = BitchatFontFamily) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecHint(stringResource(R.string.pin_rules, SecurityPreferences.PIN_MIN, SecurityPreferences.PIN_MAX))
                androidx.compose.material3.OutlinedTextField(
                    value = pin, onValueChange = { v -> pin = v.filter { it.isDigit() }.take(SecurityPreferences.PIN_MAX) },
                    label = { Text(stringResource(R.string.pin_new)) }, singleLine = true, keyboardOptions = digits,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = again, onValueChange = { v -> again = v.filter { it.isDigit() }.take(SecurityPreferences.PIN_MAX) },
                    label = { Text(stringResource(R.string.pin_confirm)) }, singleLine = true, keyboardOptions = digits,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(pin) }, enabled = valid) {
                Text(stringResource(R.string.pin_save), fontFamily = BitchatFontFamily)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_lower), fontFamily = BitchatFontFamily)
            }
        }
    )
}
