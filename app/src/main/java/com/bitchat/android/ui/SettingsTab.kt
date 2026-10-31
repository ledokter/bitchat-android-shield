package com.bitchat.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.R
import com.bitchat.android.game.Badge
import com.bitchat.android.game.KittyGame
import com.bitchat.android.net.ArtiTorManager
import com.bitchat.android.net.TorMode
import com.bitchat.android.net.TorPreferenceManager
import com.bitchat.android.nostr.BitchatShield
import com.bitchat.android.nostr.PoWPreferenceManager
import com.bitchat.android.security.SecurityPreferences
import com.bitchat.android.ui.theme.BitchatFontFamily
import com.bitchat.android.ui.theme.ChatUiModeManager
import com.bitchat.android.ui.theme.ThemePreferenceManager

/**
 * Settings tab: the nickname, the kitty collection, then one folding card per subject. Each card
 * title says the current state in one line ("11 of 11 protections active"); the details open only
 * when the card is tapped, one card at a time. Technical settings stay behind the advanced mode.
 */
@Composable
internal fun SettingsTab(viewModel: ChatViewModel, onReplayTour: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val advanced by SecurityPreferences.advancedMode.collectAsState()
    val isPro by com.bitchat.android.monetization.ProStatus.isPro.collectAsState()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    // free version: the anti-spam card opens after a video, every time (closing it locks it again)
    var spamUnlocked by remember { mutableStateOf(false) }
    var showFloodTuto by remember { mutableStateOf(false) }
    var gate by remember { mutableStateOf<AdGate?>(null) }
    // no video for Pro, nor in the website build (built without ads)
    val adFree = isPro || !com.bitchat.android.BuildConfig.ADS_ENABLED
    var showPro by remember { mutableStateOf(false) }
    val toggle: (String) -> Unit = { id ->
        when {
            open == id -> { open = null; if (id == "spam") spamUnlocked = false }
            id == "spam" && !adFree && !spamUnlocked -> gate = AdGate.ANTI_SPAM
            else -> {
                if (open == "spam") spamUnlocked = false
                if (id == "spam" && adFree && !KittyGame.tipSeen(FLOOD_TUTO_TIP)) showFloodTuto = true
                open = id
            }
        }
    }
    gate?.let { g ->
        AdGateSheet(
            gate = g,
            onUnlocked = {
                gate = null
                when (g) {
                    AdGate.ANTI_SPAM -> { spamUnlocked = true; showFloodTuto = true; open = "spam" }
                    AdGate.ADVANCED -> SecurityPreferences.setAdvancedMode(true)
                }
            },
            onDismiss = { gate = null },
            onShowPro = { gate = null; showPro = true }
        )
    }
    if (showPro) ProSheet(onDismiss = { showPro = false })
    var showKitty by remember { mutableStateOf(false) }
    var showFlood by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        PoWPreferenceManager.init(context)
        BitchatShield.init(context)
    }
    if (showKitty) com.bitchat.android.ui.kitty.KittyCollectionSheet(onDismiss = { showKitty = false })
    if (showFlood) FloodSettingsSheet(onDismiss = { showFlood = false }, geohashViewModel = viewModel.geohashViewModel)
    if (showDebug) com.bitchat.android.ui.debug.DebugSettingsSheet(
        isPresented = true, onDismiss = { showDebug = false }, meshService = viewModel.meshService
    )
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colorScheme.background),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = top, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "title") { TabTitle(stringResource(R.string.about_tab_settings)) }
        item(key = "me") { ProfileCard(viewModel) }
        item(key = "kitty") {
            val badges by KittyGame.badges.collectAsState()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(colorScheme.primary.copy(alpha = 0.08f))
                    .clickable { showKitty = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChatbitLogo(height = 20.dp, dressed = true)
                Text(
                    stringResource(R.string.kitty_collection, badges.size, Badge.entries.size),
                    fontFamily = BitchatFontFamily, fontSize = 13.sp, color = colorScheme.primary
                )
            }
        }

        item(key = "pro") {
            if (isPro) {
                Text(
                    stringResource(R.string.pro_active), fontFamily = BitchatFontFamily, fontSize = 13.sp,
                    color = okColor(), modifier = Modifier.padding(start = 4.dp)
                )
            } else {
                Surface(
                    onClick = { showPro = true },
                    shape = RoundedCornerShape(18.dp),
                    color = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.pro_button), fontFamily = BitchatFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.settings_pro_sum), fontFamily = BitchatFontFamily, fontSize = 12.sp)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                    }
                }
            }
        }

        item(key = "security") {
            val active = securityActiveCount()
            SettingsCard(
                id = "security", icon = Icons.Filled.VerifiedUser, open = open, onToggle = toggle,
                title = stringResource(R.string.security_section),
                summary = stringResource(R.string.security_summary, active, SECURITY_TOTAL), ok = active == SECURITY_TOTAL
            ) { SecurityToggles() }
        }

        item(key = "pin") {
            val hasPin by SecurityPreferences.hasPin.collectAsState()
            val hasDuress by SecurityPreferences.hasDuress.collectAsState()
            SettingsCard(
                id = "pin", icon = Icons.Filled.Key, open = open, onToggle = toggle,
                title = stringResource(R.string.pin_title),
                summary = stringResource(
                    when {
                        hasPin && hasDuress -> R.string.settings_pin_sum_both
                        hasPin -> R.string.settings_pin_sum_pin
                        else -> R.string.settings_pin_sum_none
                    }
                ),
                ok = hasPin && hasDuress
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PinSettings()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.settings_panic_title), fontFamily = BitchatFontFamily, fontSize = 14.sp, color = colorScheme.onSurface)
                                HelpDot(R.string.help_panic)
                            }
                            Text(stringResource(R.string.settings_panic_desc), fontFamily = BitchatFontFamily, fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = colorScheme.error)
                    }
                }
            }
        }

        item(key = "tor") {
            val tor = remember { ArtiTorManager.getInstance() }
            val status by tor.statusFlow.collectAsStateWithLifecycle()
            val mode by TorPreferenceManager.modeFlow.collectAsState()
            val available = remember { tor.isTorAvailable() }
            val ready = status.running && status.bootstrapPercent >= 100
            SettingsCard(
                id = "tor", icon = Icons.Filled.Security, open = open, onToggle = toggle,
                title = stringResource(R.string.about_tor_title),
                summary = when {
                    mode == TorMode.OFF -> stringResource(R.string.settings_tor_sum_off)
                    ready -> stringResource(R.string.settings_tor_sum_on)
                    status.running -> stringResource(R.string.settings_tor_sum_boot, status.bootstrapPercent)
                    else -> stringResource(R.string.about_tor_disconnected)
                },
                ok = mode == TorMode.ON && ready,
                contentPadding = PaddingValues(bottom = 14.dp)
            ) {
                Column {
                    SettingsToggleRow(
                        icon = Icons.Filled.Security,
                        title = stringResource(R.string.about_tor_title),
                        subtitle = stringResource(R.string.about_tor_route),
                        checked = mode == TorMode.ON,
                        onCheckedChange = { on -> if (available) TorPreferenceManager.set(context, if (on) TorMode.ON else TorMode.OFF) },
                        enabled = available,
                        help = R.string.help_tor
                    )
                    TorStatusCard(showRotation = true, outerPadding = 12.dp)
                }
            }
        }

        item(key = "spam") {
            val floodEnabled by BitchatShield.floodEnabled.collectAsState()
            val flagged by BitchatShield.flaggedCount.collectAsState()
            val bots by BitchatShield.blockBots.collectAsState()
            val links by BitchatShield.blockLinks.collectAsState()
            val zalgo by BitchatShield.blockZalgo.collectAsState()
            val blocked by BitchatShield.blockedCount.collectAsState()
            SettingsCard(
                id = "spam", icon = Icons.Filled.Block, open = open, onToggle = toggle,
                title = stringResource(R.string.settings_spam_title),
                summary = stringResource(if (floodEnabled) R.string.settings_spam_sum else R.string.settings_spam_sum_base, flagged),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                Column {
                    if (showFloodTuto) FloodTutorial(onDone = { showFloodTuto = false; KittyGame.markTipSeen(FLOOD_TUTO_TIP) })
                    SettingsToggleRow(Icons.Filled.Shield, stringResource(R.string.flood_row_title, flagged), stringResource(R.string.flood_row_desc),
                        floodEnabled, { BitchatShield.setFloodEnabled(it) }, help = R.string.help_flood)
                    SettingsToggleRow(Icons.Filled.Lock, stringResource(R.string.shield_bots_title, blocked), stringResource(R.string.shield_bots_desc),
                        bots, { BitchatShield.setBlockBots(it) }, help = R.string.help_bots)
                    SettingsToggleRow(Icons.Filled.Public, stringResource(R.string.shield_links_title), stringResource(R.string.shield_links_desc),
                        links, { BitchatShield.setBlockLinks(it) }, help = R.string.help_links)
                    SettingsToggleRow(Icons.Filled.Warning, stringResource(R.string.shield_zalgo_title), stringResource(R.string.shield_zalgo_desc),
                        zalgo, { BitchatShield.setBlockZalgo(it) }, help = R.string.help_zalgo)
                    ChatbitButton(
                        text = stringResource(R.string.flood_open_settings),
                        onClick = { showFlood = true },
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
        }

        item(key = "share") {
            SettingsCard(
                id = "share", icon = Icons.Filled.Share, open = open, onToggle = toggle,
                title = stringResource(R.string.settings_share_title),
                summary = stringResource(R.string.settings_share_sum),
                help = R.string.help_share,
                contentPadding = PaddingValues(bottom = 8.dp)
            ) { ApkShareRows() }
        }

        item(key = "look") {
            val theme by ThemePreferenceManager.themeFlow.collectAsState()
            val accent by ThemePreferenceManager.accentFlow.collectAsState()
            val chatUi by ChatUiModeManager.modeFlow.collectAsState()
            val language = remember(open) {
                LanguagePreferenceManager.supportedLanguages(context)
                    .firstOrNull { it.languageTag == LanguagePreferenceManager.currentLanguageTag() }?.endonym
            }
            val summary = listOf(
                stringResource(if (theme.isLight) R.string.about_light else if (theme.isDark) R.string.about_dark else R.string.about_system)
                    .replaceFirstChar { it.titlecase() },
                stringResource(accent.label),
                stringResource(if (chatUi.isMatrix) R.string.settings_terminal else R.string.settings_bubbles),
                language ?: stringResource(R.string.about_system_default)
            ).joinToString(" · ")
            SettingsCard(
                id = "look", icon = Icons.Filled.Palette, open = open, onToggle = toggle,
                title = stringResource(R.string.settings_look_title), summary = summary,
                help = R.string.help_theme
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppearanceSettings(showLabel = false, outerPadding = 0.dp)
                    LanguageSettings(showLabel = false, outerPadding = 0.dp)
                }
            }
        }

        item(key = "run") {
            var background by remember { mutableStateOf(com.bitchat.android.service.MeshServicePreferences.isBackgroundEnabled(true)) }
            var liveVoice by remember { mutableStateOf(com.bitchat.android.features.voice.LiveVoicePreferences.isEnabled(context)) }
            SettingsCard(
                id = "run", icon = Icons.Filled.Bolt, open = open, onToggle = toggle,
                title = stringResource(R.string.settings_run_title),
                summary = stringResource(if (background) R.string.settings_run_sum_on else R.string.settings_run_sum_off),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                Column {
                    SettingsToggleRow(
                        icon = Icons.Filled.Bluetooth,
                        title = stringResource(R.string.about_background_title),
                        subtitle = stringResource(R.string.about_background_desc),
                        checked = background,
                        onCheckedChange = { on ->
                            background = on
                            com.bitchat.android.service.MeshServicePreferences.setBackgroundEnabled(on)
                            if (on) com.bitchat.android.service.MeshForegroundService.start(context)
                            else com.bitchat.android.service.MeshForegroundService.stop(context)
                        },
                        help = R.string.help_background
                    )
                    SettingsToggleRow(
                        icon = Icons.Filled.Mic,
                        title = stringResource(R.string.live_ptt_title),
                        subtitle = stringResource(R.string.live_ptt_desc),
                        checked = liveVoice,
                        onCheckedChange = { on ->
                            liveVoice = on
                            com.bitchat.android.features.voice.LiveVoicePreferences.setEnabled(context, on)
                        },
                        help = R.string.help_live_ptt
                    )
                }
            }
        }

        if (advanced) item(key = "tech") {
            val powEnabled by PoWPreferenceManager.powEnabled.collectAsState()
            val powDifficulty by PoWPreferenceManager.powDifficulty.collectAsState()
            val sendPowEnabled by PoWPreferenceManager.sendPowEnabled.collectAsState()
            val sendPowDifficulty by PoWPreferenceManager.sendPowDifficulty.collectAsState()
            SettingsCard(
                id = "tech", icon = Icons.Filled.Tune, open = open, onToggle = toggle,
                title = stringResource(R.string.settings_tech_title),
                summary = stringResource(R.string.settings_tech_sum),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                Column {
                    SettingsToggleRow(Icons.Filled.Speed, stringResource(R.string.pow_send_title, if (sendPowEnabled) sendPowDifficulty else 0),
                        stringResource(R.string.pow_send_desc), sendPowEnabled, { PoWPreferenceManager.setSendPowEnabled(it) }, help = R.string.help_pow_send)
                    SettingsToggleRow(Icons.Filled.Speed, stringResource(R.string.pow_receive_title, if (powEnabled) powDifficulty else 0),
                        stringResource(R.string.pow_receive_desc), powEnabled, { PoWPreferenceManager.setPowEnabled(it) }, help = R.string.help_pow_receive)
                    PowSliders(outerPadding = 12.dp)
                    TextButton(onClick = { showDebug = true }, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                        Text(stringResource(R.string.about_debug_settings), fontFamily = BitchatFontFamily, fontSize = 13.sp)
                    }
                }
            }
        }

        item(key = "about") {
            SettingsCard(
                id = "about", icon = Icons.Outlined.Info, open = open, onToggle = toggle,
                title = stringResource(R.string.about_section_about),
                summary = stringResource(R.string.settings_about_sum, versionName),
                contentPadding = PaddingValues(bottom = 14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AboutFeatureCard()
                    AboutHowToUseSection()
                    ChatbitButton(
                        text = stringResource(R.string.kitty_replay_tour),
                        onClick = onReplayTour,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                    // the site (provisional address until the final domain)
                    ChatbitButton(
                        text = stringResource(R.string.about_site),
                        onClick = {
                            com.bitchat.android.net.TorLinks.open(context, SITE_URL)
                        },
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }

        item(key = "advanced") {
            // free version: switching it on costs a video, every time (it switches off on leaving Settings)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.advanced_mode_title), fontFamily = BitchatFontFamily, fontSize = 14.sp, color = colorScheme.onSurface)
                        HelpDot(R.string.help_advanced)
                    }
                    Text(
                        stringResource(if (adFree) R.string.advanced_mode_desc else R.string.adgate_advanced_hint),
                        fontFamily = BitchatFontFamily, fontSize = 12.sp, color = colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.Switch(
                    checked = advanced,
                    onCheckedChange = { on ->
                        when {
                            !on -> SecurityPreferences.setAdvancedMode(false)
                            adFree -> SecurityPreferences.setAdvancedMode(true)
                            else -> gate = AdGate.ADVANCED
                        }
                    }
                )
            }
        }
        item(key = "footer") {
            Text(
                "chatbit v$versionName · " + stringResource(R.string.about_footer),
                fontFamily = BitchatFontFamily, fontSize = 11.sp,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

private const val FLOOD_TUTO_TIP = "flood_tuto"

/** chatbit's site; change it here when the final domain is chosen. */
private const val SITE_URL = "https://chatbit.zap-info.com/"

/** Big title at the top of a tab. */
@Composable
internal fun TabTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontFamily = BitchatFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(top = 14.dp, bottom = 6.dp, start = 2.dp)
    )
}

/** Green that reads in both themes, for "all good" summaries. */
@Composable
internal fun okColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF32D74B) else Color(0xFF1B7A34)

/** One folding card: icon, title, one-line state; the content opens below. */
@Composable
private fun SettingsCard(
    id: String,
    icon: ImageVector,
    title: String,
    summary: String,
    open: String?,
    onToggle: (String) -> Unit,
    ok: Boolean = false,
    help: Int? = null,
    contentPadding: PaddingValues = PaddingValues(start = 14.dp, end = 14.dp, bottom = 14.dp),
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val expanded = open == id
    val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "cardChevron")
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, if (expanded) colorScheme.primary.copy(alpha = 0.45f) else colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(id) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontFamily = BitchatFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
                        help?.let { HelpDot(it) }
                    }
                    Text(
                        summary, fontFamily = BitchatFontFamily, fontSize = 12.sp,
                        color = if (ok) okColor() else colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = colorScheme.onSurfaceVariant, modifier = Modifier.rotate(angle))
            }
            AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Box(Modifier.fillMaxWidth().padding(contentPadding)) { content() }
            }
        }
    }
}

/** Kitty, the nickname, and a pencil to change it in place. */
@Composable
private fun ProfileCard(viewModel: ChatViewModel) {
    val colorScheme = MaterialTheme.colorScheme
    val nickname by viewModel.nickname.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var draft by remember(editing) { mutableStateOf(nickname) }
    val focus = remember { FocusRequester() }
    val save = {
        draft.trim().takeIf { it.isNotEmpty() }?.let(viewModel::setNickname)
        editing = false
    }
    LaunchedEffect(editing) { if (editing) focus.requestFocus() }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatbitLogo(height = 44.dp, dressed = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (editing) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it.take(24) },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = BitchatFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface),
                        cursorBrush = SolidColor(colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                } else {
                    Text("@$nickname", fontFamily = BitchatFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(stringResource(R.string.settings_nick_hint), fontFamily = BitchatFontFamily, fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { if (editing) save() else editing = true }) {
                Icon(
                    if (editing) Icons.Filled.Check else Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.settings_nick_edit),
                    tint = if (editing) colorScheme.primary else colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
