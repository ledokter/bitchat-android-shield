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

    val linksTor by SecurityPreferences.linksViaTor.collectAsState()
    // link previews, photo metadata and lock-screen notifications are always protected
    val active = listOf(rotation > 0, killSwitch, !presence, true, true, ephemeral > 0, true,
        screenshots, lock, clipboard, wipe > 0, linksTor).count { it }

    Column {
        AboutSectionLabel(text = stringResource(R.string.security_section))
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AboutHorizontalPadding),
            color = MaterialTheme.colorScheme.surface,
            shape = AboutCardShape
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.security_summary, active, SECURITY_TOTAL),
                    fontFamily = BitchatFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                PinSettings()
                // links: shown even without advanced mode, it decides what every link tap does
                com.bitchat.android.ui.kitty.KittyTip("links_tor", stringResource(R.string.tip_links_tor))
                SecSwitch(R.string.sec_links_title, R.string.sec_links_desc, linksTor, help = R.string.help_links_tor) { SecurityPreferences.setLinksViaTor(it) }
                if (!advanced) {
                    SecHint(stringResource(R.string.security_summary_hint))
                    return@Column
                }
                SecSwitch(R.string.sec_lock_title, R.string.sec_lock_pin_desc, lock) { SecurityPreferences.setAppLock(it) }
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

/** Number of protections counted by [securityActiveCount]. */
internal const val SECURITY_TOTAL = 12

/** How many of the SECURITY_TOTAL protections are on (link previews, photo metadata and lock-screen notifications always are). */
@Composable
internal fun securityActiveCount(): Int {
    val lock by SecurityPreferences.appLock.collectAsState()
    val screenshots by SecurityPreferences.blockScreenshots.collectAsState()
    val presence by SecurityPreferences.sendPresence.collectAsState()
    val killSwitch by SecurityPreferences.killSwitch.collectAsState()
    val clipboard by SecurityPreferences.clipboardClear.collectAsState()
    val ephemeral by SecurityPreferences.ephemeralHours.collectAsState()
    val wipe by SecurityPreferences.autoWipeDays.collectAsState()
    val rotation by TorPreferenceManager.rotationFlow.collectAsState()
    val linksTor by SecurityPreferences.linksViaTor.collectAsState()
    return listOf(rotation > 0, killSwitch, !presence, true, true, ephemeral > 0, true,
        screenshots, lock, clipboard, wipe > 0, linksTor).count { it }
}

/** Every protection switch and delay, each with its help bubble (settings tab, Security card). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SecurityToggles() {
    val lock by SecurityPreferences.appLock.collectAsState()
    val screenshots by SecurityPreferences.blockScreenshots.collectAsState()
    val presence by SecurityPreferences.sendPresence.collectAsState()
    val killSwitch by SecurityPreferences.killSwitch.collectAsState()
    val clipboard by SecurityPreferences.clipboardClear.collectAsState()
    val ephemeral by SecurityPreferences.ephemeralHours.collectAsState()
    val wipe by SecurityPreferences.autoWipeDays.collectAsState()
    val linksTor by SecurityPreferences.linksViaTor.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        com.bitchat.android.ui.kitty.KittyTip("links_tor", stringResource(R.string.tip_links_tor))
        SecSwitch(R.string.sec_links_title, R.string.sec_links_desc, linksTor, help = R.string.help_links_tor) { SecurityPreferences.setLinksViaTor(it) }
        SecSwitch(R.string.sec_lock_title, R.string.sec_lock_pin_desc, lock, help = R.string.help_lock) { SecurityPreferences.setAppLock(it) }
        SecSwitch(R.string.sec_screenshots_title, R.string.sec_screenshots_desc, screenshots, help = R.string.help_screenshots) { SecurityPreferences.setBlockScreenshots(it) }
        SecSwitch(R.string.sec_killswitch_title, R.string.sec_killswitch_desc, killSwitch, help = R.string.help_killswitch) { SecurityPreferences.setKillSwitch(it) }
        SecSwitch(R.string.sec_presence_title, R.string.sec_presence_desc, presence, help = R.string.help_presence) { SecurityPreferences.setSendPresence(it) }
        SecSwitch(R.string.sec_clipboard_title, R.string.sec_clipboard_desc, clipboard, help = R.string.help_clipboard) { SecurityPreferences.setClipboardClear(it) }
        SecLabel(stringResource(R.string.sec_ephemeral_title), R.string.help_ephemeral)
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
        SecLabel(stringResource(R.string.sec_autowipe_title), R.string.help_autowipe)
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

/** Always-visible switch between the simple settings and every power-user option. */
@Composable
internal fun AdvancedModeToggle(advanced: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AboutHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            SecLabel(stringResource(R.string.advanced_mode_title), R.string.help_advanced)
            SecHint(stringResource(R.string.advanced_mode_desc))
        }
        Switch(checked = advanced, onCheckedChange = { SecurityPreferences.setAdvancedMode(it) })
    }
}

@Composable
private fun SecSwitch(title: Int, desc: Int, checked: Boolean, enabled: Boolean = true, help: Int? = null, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            SecLabel(stringResource(title), help)
            SecHint(stringResource(desc))
        }
        Switch(checked = checked && enabled, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun SecLabel(text: String, help: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontFamily = BitchatFontFamily, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        help?.let { HelpDot(it) }
    }
}

@Composable
private fun SecHint(text: String) {
    Text(text, fontFamily = BitchatFontFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** chatbit PIN and duress PIN: always visible, they matter more than any tunable. */
@Composable
internal fun PinSettings() {
    val context = LocalContext.current
    val hasPin by SecurityPreferences.hasPin.collectAsState()
    val hasDuress by SecurityPreferences.hasDuress.collectAsState()
    var editing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Boolean?>(null) } // true = PIN, false = duress

    PinRow(
        title = R.string.pin_title, desc = R.string.pin_desc, isSet = hasPin, help = R.string.help_pin,
        onSet = { editing = true }, onRemove = { SecurityPreferences.setPin(null) }
    )
    if (hasPin) {
        PinRow(
            title = R.string.duress_title, desc = R.string.duress_desc, isSet = hasDuress, help = R.string.help_duress,
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
                } else {
                    val problem = when (SecurityPreferences.setDuressPin(pin)) {
                        SecurityPreferences.DuressResult.OK -> null
                        SecurityPreferences.DuressResult.SAME_AS_PIN -> R.string.duress_same_as_pin
                        SecurityPreferences.DuressResult.WRONG_LENGTH -> R.string.duress_wrong_length
                    }
                    if (problem == null) editing = null
                    else android.widget.Toast.makeText(context, context.getString(problem), android.widget.Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

@Composable
private fun PinRow(title: Int, desc: Int, isSet: Boolean, help: Int? = null, onSet: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            SecLabel(stringResource(title), help)
            SecHint(stringResource(desc))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ChatbitButton(text = stringResource(if (isSet) R.string.pin_change else R.string.pin_set), onClick = onSet)
            if (isSet) ChatbitButton(text = stringResource(R.string.pin_remove), onClick = onRemove, danger = true)
        }
    }
}

@Composable
private fun PinDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    // In-app keypad only: the PIN never reaches the system keyboard.
    var first by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var mismatch by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = BitchatFontFamily) },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecHint(stringResource(R.string.pin_rules, SecurityPreferences.PIN_MIN, SecurityPreferences.PIN_MAX))
                Keypad(
                    title = stringResource(when {
                        mismatch -> R.string.pin_mismatch
                        first == null -> R.string.pin_new
                        else -> R.string.pin_confirm
                    }),
                    isError = mismatch,
                    enabled = true,
                    autoLength = first?.length ?: 0,
                    keySize = 54.dp
                ) { typed ->
                    val firstPin = first
                    when {
                        firstPin == null -> if (SecurityPreferences.isValidPin(typed)) { first = typed; mismatch = false }
                        firstPin == typed -> onConfirm(typed)
                        else -> { first = null; mismatch = true }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_lower), fontFamily = BitchatFontFamily)
            }
        }
    )
}
