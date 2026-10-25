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
