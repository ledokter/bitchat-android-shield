package com.bitchat.android.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.R
import com.bitchat.android.net.TorLinks.Reason
import com.bitchat.android.core.ui.component.sheet.BitchatBottomSheet
import com.bitchat.android.game.KittyGame
import com.bitchat.android.net.ArtiTorManager
import com.bitchat.android.net.TorMode
import com.bitchat.android.net.TorPreferenceManager
import com.bitchat.android.ui.kitty.KittyAnchors
import com.bitchat.android.ui.kitty.KittyTour
import com.bitchat.android.ui.kitty.kittyAnchor
import com.bitchat.android.ui.theme.BitchatFontFamily

/** Height of the bottom tab bar, above the system navigation bar. */
val TabBarHeight: Dp = 64.dp

private enum class MainTab { CHAT, MAP, PRIVATE, SETTINGS }

/**
 * chatbit main screen: four tabs at the bottom (Chat, Map, Private, Settings). The chat stays
 * composed underneath the other tabs, so its sheets (private chat, people, channels) keep working
 * from anywhere; Map opens the full-screen globe.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainTabs(viewModel: ChatViewModel) {
    var tab by rememberSaveable { mutableStateOf(MainTab.CHAT) }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val unread by viewModel.unreadPrivateMessages.collectAsStateWithLifecycle()

    BackHandler(enabled = tab != MainTab.CHAT) { tab = MainTab.CHAT }
    LaunchedEffect(Unit) {
        com.bitchat.android.monetization.ProStatus.init(context)
        SenderStats.init(context)
    }
    // free version: advanced settings are unlocked by a video for one visit of Settings only
    LaunchedEffect(tab) {
        if (tab != MainTab.SETTINGS && !com.bitchat.android.monetization.ProStatus.isPro.value) {
            com.bitchat.android.security.SecurityPreferences.setAdvancedMode(false)
        }
    }
    LaunchedEffect(tab) { if (tab != MainTab.CHAT) focus.clearFocus() }

    // Map: the globe returns the geohash picked there, which becomes the current channel
    val locationManager = remember { com.bitchat.android.geohash.LocationChannelManager.getInstance(context) }
    val mapLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringExtra(GeohashPickerActivity.EXTRA_RESULT_GEOHASH)
                ?.takeIf { it.isNotBlank() }
                ?.let { gh -> channelForManualGeohash(gh)?.let { locationManager.selectManual(it) } }
            tab = MainTab.CHAT
        }
    }

    val showBar = !imeVisible || tab != MainTab.CHAT
    Box(Modifier.fillMaxSize()) {
        ChatScreen(viewModel = viewModel, bottomBarHeight = if (showBar) TabBarHeight else 0.dp)

        val tabModifier = Modifier.fillMaxSize().padding(bottom = TabBarHeight + navInset)
        when (tab) {
            MainTab.PRIVATE -> PrivateTab(viewModel, tabModifier)
            MainTab.SETTINGS -> SettingsTab(
                viewModel = viewModel,
                onReplayTour = { KittyGame.replayTips(); tab = MainTab.CHAT },
                modifier = tabModifier
            )
            else -> Unit
        }

        if (showBar) {
            TabBar(
                current = tab,
                unread = unread.size,
                onSelect = { t ->
                    if (t == MainTab.MAP) mapLauncher.launch(android.content.Intent(context, WorldMapActivity::class.java))
                    else tab = t
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // first-launch tour, over everything including the tab bar
        if (tab == MainTab.CHAT) Box(Modifier.fillMaxSize()) { KittyTour() }
    }
    TorBrowserRequiredDialog()
}

/**
 * A tapped link waits on the person (see TorLinks): first copy-and-paste explanation, Tor Browser
 * missing, or links switched off. Copying is always their own choice, with the risk spelled out.
 */
@Composable
private fun TorBrowserRequiredDialog() {
    val context = LocalContext.current
    // after Tor Browser opens: remind to paste (the link sits on the secure clipboard)
    LaunchedEffect(Unit) {
        com.bitchat.android.net.TorLinks.copied.collect {
            android.widget.Toast.makeText(context, R.string.torlink_pasted_toast, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val pending by com.bitchat.android.net.TorLinks.pending.collectAsStateWithLifecycle()
    val p = pending ?: return
    val links = com.bitchat.android.net.TorLinks
    val clipboardClear by com.bitchat.android.security.SecurityPreferences.clipboardClear.collectAsStateWithLifecycle()
    val title = when (p.reason) {
        Reason.OFF -> R.string.torlink_off_title
        Reason.MISSING -> R.string.torlink_title
        Reason.PASTE -> R.string.torlink_paste_title
    }
    val body = when (p.reason) {
        Reason.OFF -> R.string.torlink_off_body
        Reason.MISSING -> R.string.torlink_body
        Reason.PASTE -> R.string.torlink_paste_body
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { links.dismiss() },
        title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(title)) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(body))
                // why the clipboard is safe (or the warning when its clearing is switched off)
                androidx.compose.material3.Text(
                    androidx.compose.ui.res.stringResource(if (clipboardClear) R.string.torlink_clipboard_secure else R.string.torlink_clipboard_keep),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (p.reason != Reason.PASTE) androidx.compose.material3.Text(
                    androidx.compose.ui.res.stringResource(R.string.torlink_copy_risk),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End) {
                if (p.reason == Reason.PASTE) {
                    androidx.compose.material3.TextButton(onClick = { links.copyAndOpenTorBrowser(context, p.url) }) {
                        androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.torlink_paste_button))
                    }
                }
                if (p.reason == Reason.MISSING) {
                    androidx.compose.material3.TextButton(onClick = { links.openSource(context, links.GUARDIAN_FDROID) }) {
                        androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.torlink_fdroid))
                    }
                    androidx.compose.material3.TextButton(onClick = { links.openSource(context, links.TORPROJECT_ANDROID) }) {
                        androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.torlink_torproject))
                    }
                }
                if (p.reason != Reason.PASTE) {
                    androidx.compose.material3.TextButton(onClick = { links.copy(context, p.url) }) {
                        androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.torlink_copy))
                    }
                }
                androidx.compose.material3.TextButton(onClick = { links.dismiss() }) {
                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.torlink_cancel))
                }
            }
        }
    )
}

@Composable
private fun TabBar(current: MainTab, unread: Int, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().background(colorScheme.background)) {
        HorizontalDivider(color = colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .height(TabBarHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabItem(Icons.AutoMirrored.Outlined.Chat, R.string.tab_chat, current == MainTab.CHAT, 0, KittyAnchors.TAB_CHAT) { onSelect(MainTab.CHAT) }
            TabItem(Icons.Outlined.Public, R.string.tab_map, false, 0, KittyAnchors.TAB_MAP) { onSelect(MainTab.MAP) }
            TabItem(Icons.Outlined.Lock, R.string.tab_private, current == MainTab.PRIVATE, unread, KittyAnchors.TAB_PRIVATE) { onSelect(MainTab.PRIVATE) }
            TabItem(Icons.Outlined.Settings, R.string.about_tab_settings, current == MainTab.SETTINGS, 0, KittyAnchors.TAB_SETTINGS) { onSelect(MainTab.SETTINGS) }
        }
        // the system navigation bar sits below the tabs
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    icon: ImageVector,
    label: Int,
    selected: Boolean,
    badge: Int,
    anchor: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .weight(1f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.kittyAnchor(anchor)) {
            Box(
                Modifier
                    .width(56.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (selected) colorScheme.primary.copy(alpha = 0.2f) else colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
            if (badge > 0) Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary)
                    .padding(horizontal = 5.dp)
            ) { Text("$badge", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colorScheme.onPrimary) }
        }
        Text(
            stringResource(label), fontFamily = BitchatFontFamily, fontSize = 10.5.sp,
            color = if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** Opened by the cat in the header: Tor on or off, its state and a new route. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun TorSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val tor = remember { ArtiTorManager.getInstance() }
    val mode by TorPreferenceManager.modeFlow.collectAsState()
    val available = remember { tor.isTorAvailable() }
    BitchatBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)) {
            TabTitle(stringResource(R.string.about_tor_title), Modifier.padding(horizontal = 14.dp))
            SettingsToggleRow(
                icon = Icons.Filled.Security,
                title = stringResource(R.string.about_tor_title),
                subtitle = stringResource(R.string.about_tor_route),
                checked = mode == TorMode.ON,
                onCheckedChange = { on -> if (available) TorPreferenceManager.set(context, if (on) TorMode.ON else TorMode.OFF) },
                enabled = available,
                help = R.string.help_tor
            )
            TorStatusCard(showRotation = true, outerPadding = 14.dp)
        }
    }
}
