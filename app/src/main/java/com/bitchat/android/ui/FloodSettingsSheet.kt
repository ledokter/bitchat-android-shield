package com.bitchat.android.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.R
import com.bitchat.android.core.ui.component.sheet.BitchatBottomSheet
import com.bitchat.android.core.ui.component.sheet.BitchatSheetTitle
import com.bitchat.android.core.ui.component.sheet.BitchatSheetTopBar
import com.bitchat.android.nostr.BitchatShield
import com.bitchat.android.nostr.FloodDetector
import com.bitchat.android.nostr.ShieldLists
import com.bitchat.android.ui.theme.BitchatFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private val RULE_LABELS = mapOf(
    FloodDetector.RULE_RATE to R.string.flood_rule_rate,
    FloodDetector.RULE_REPEAT to R.string.flood_rule_repeat,
    FloodDetector.RULE_SHORT to R.string.flood_rule_short,
    FloodDetector.RULE_COPY to R.string.flood_rule_copy,
    FloodDetector.RULE_SAME_TEXT to R.string.flood_rule_same_text,
    FloodDetector.RULE_NICKNAMES to R.string.flood_rule_nicknames,
    FloodDetector.RULE_NICK_KEYS to R.string.flood_rule_nickname_keys,
    FloodDetector.RULE_PATTERN to R.string.flood_rule_pattern,
    FloodDetector.RULE_LINK to R.string.flood_rule_link,
    FloodDetector.RULE_GIANT to R.string.flood_rule_giant,
    FloodDetector.RULE_ZALGO to R.string.flood_rule_zalgo,
    FloodDetector.RULE_KEYWORD to R.string.flood_rule_keyword
)

/**
 * Anti-flood settings: two-stage detection thresholds, rules on/off, live chatty keys,
 * hidden messages (with "not a spammer"), and import/export of blocked / flagged lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloodSettingsSheet(
    onDismiss: () -> Unit,
    geohashViewModel: GeohashViewModel
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { BitchatShield.init(context) }

    val enabled by BitchatShield.floodEnabled.collectAsState()
    val rules by BitchatShield.floodRules.collectAsState()
    val flaggedCount by BitchatShield.flaggedCount.collectAsState()
    val hidden by BitchatShield.hidden.collectAsState()
    // long lists fold into accordions, closed by default
    var blockedOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var hiddenOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var chattyOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val blockVersion by DataManager.blockVersion.collectAsState()
    val blocked = remember(blockVersion) { geohashViewModel.blockedPubkeys().sorted() }
    var chatty by remember { mutableStateOf(emptyList<FloodDetector.Chatty>()) }
    LaunchedEffect(Unit) {
        while (true) {
            chatty = withContext(Dispatchers.Default) { BitchatShield.detector.chatty(limit = 15) }
            delay(3000)
        }
    }

    var pendingExport by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val body = pendingExport
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) } }.isSuccess
            }
            Toast.makeText(context, context.getString(if (ok) R.string.flood_export_done else R.string.flood_export_failed), Toast.LENGTH_SHORT).show()
        }
    }
    fun export(kind: ShieldLists.Kind, json: Boolean) {
        val keys = if (kind == ShieldLists.Kind.BLOCKED) geohashViewModel.blockedPubkeys() else BitchatShield.flaggedKeys()
        pendingExport = ShieldLists.export(kind, keys, { geohashViewModel.knownNickname(it) }, json)
        exportLauncher.launch(ShieldLists.fileName(kind, json))
    }

    // Direct send (Bluetooth, Quick Share, messaging…) through the Android share sheet
    fun shareDirect(kind: ShieldLists.Kind) {
        val keys = if (kind == ShieldLists.Kind.BLOCKED) geohashViewModel.blockedPubkeys() else BitchatShield.flaggedKeys()
        val body = ShieldLists.export(kind, keys, { geohashViewModel.knownNickname(it) }, json = false)
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = java.io.File(context.cacheDir, "lists").apply { mkdirs() }
                    val file = java.io.File(dir, ShieldLists.fileName(kind, json = false))
                    file.writeText(body)
                    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }.getOrNull()
            }
            if (uri == null) {
                Toast.makeText(context, context.getString(R.string.flood_export_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newRawUri("", uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                android.content.Intent.createChooser(send, context.getString(R.string.flood_send_chooser))
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    var importTarget by remember { mutableStateOf(ShieldLists.Kind.BLOCKED) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val target = importTarget
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { String(it.readNBytes(8_000_000)) }
                }.getOrNull()
            }
            if (text == null) {
                Toast.makeText(context, context.getString(R.string.flood_import_unreadable), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val keys = withContext(Dispatchers.Default) { ShieldLists.parse(text).keys }
            val added = if (target == ShieldLists.Kind.BLOCKED) geohashViewModel.blockPubkeys(keys) else BitchatShield.flagKeys(keys)
            Toast.makeText(context, context.getString(R.string.flood_import_result, keys.size, added), Toast.LENGTH_LONG).show()
        }
    }

    BitchatBottomSheet(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 80.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { com.bitchat.android.ui.kitty.KittyTip("flood", stringResource(R.string.tip_flood)) }
                item {
                    FloodCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Label(stringResource(R.string.flood_hide_title), bold = true)
                                Hint(stringResource(R.string.flood_hide_desc, flaggedCount))
                            }
                            Switch(checked = enabled, onCheckedChange = { BitchatShield.setFloodEnabled(it) })
                        }
                    }
                }
                item {
                    // like the bot: tick to see what blocked / reported people still write
                    val showBlocked by com.bitchat.android.security.SecurityPreferences.showBlocked.collectAsState()
                    FloodCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Label(stringResource(R.string.show_blocked_title), bold = true)
                                Hint(stringResource(R.string.show_blocked_desc))
                            }
                            androidx.compose.material3.Checkbox(
                                checked = showBlocked,
                                onCheckedChange = { com.bitchat.android.security.SecurityPreferences.setShowBlocked(it) }
                            )
                        }
                    }
                }
                item {
                    FloodCard {
                        Label(stringResource(R.string.flood_detection), bold = true)
                        RuleSlider(stringResource(R.string.flood_slider_chatty), rules.minChatty.toFloat(), 2f..30f, 27, "%.0f") {
                            BitchatShield.setFloodRules(rules.copy(minChatty = it.toInt()))
                        }
                        RuleSlider(stringResource(R.string.flood_slider_diversity), rules.maxDiversity.toFloat(), 0.1f..1f, 17, "%.2f") {
                            BitchatShield.setFloodRules(rules.copy(maxDiversity = it.toDouble()))
                        }
                        RuleSlider(stringResource(R.string.flood_slider_empty), rules.minEmpty.toFloat(), 0.1f..1f, 17, "%.2f") {
                            BitchatShield.setFloodRules(rules.copy(minEmpty = it.toDouble()))
                        }
                        RuleSlider(stringResource(R.string.flood_slider_threshold), rules.threshold.toFloat(), 2f..20f, 17, "%.0f") {
                            BitchatShield.setFloodRules(rules.copy(threshold = it.toDouble()))
                        }
                        RuleSlider(stringResource(R.string.flood_slider_rate, rules.rateWindow), rules.maxRate.toFloat(), 2f..30f, 27, "%.0f") {
                            BitchatShield.setFloodRules(rules.copy(maxRate = it.toInt()))
                        }
                        RuleSlider(stringResource(R.string.flood_slider_zalgo), rules.zalgoBomb.toFloat(), 20f..500f, 47, "%.0f") {
                            BitchatShield.setFloodRules(rules.copy(zalgoBomb = it.toInt()))
                        }
                        ChatbitButton(
                            text = stringResource(R.string.flood_defaults),
                            onClick = { BitchatShield.setFloodRules(FloodDetector.Rules(disabled = rules.disabled, keywords = rules.keywords)) }
                        )
                    }
                }
                item {
                    FloodCard {
                        Label(stringResource(R.string.flood_rules), bold = true)
                        for (id in FloodDetector.RULE_IDS) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(RULE_LABELS[id]?.let { stringResource(it) } ?: id, fontFamily = BitchatFontFamily, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Switch(checked = id !in rules.disabled, onCheckedChange = { on ->
                                    BitchatShield.setFloodRules(rules.copy(disabled = if (on) rules.disabled - id else rules.disabled + id))
                                })
                            }
                        }
                        var keywords by remember { mutableStateOf(rules.keywords.joinToString(", ")) }
                        OutlinedTextField(
                            value = keywords,
                            onValueChange = { keywords = it },
                            label = { Text(stringResource(R.string.flood_keywords), fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        ChatbitButton(text = stringResource(R.string.flood_keywords_save), onClick = {
                            BitchatShield.setFloodRules(rules.copy(keywords = keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }))
                        })
                    }
                }
                item {
                    FloodCard {
                        AccordionHeader(stringResource(R.string.flood_chatty_title) + " (${chatty.size})", chattyOpen) { chattyOpen = !chattyOpen }
                        if (chattyOpen && chatty.isEmpty()) Hint(stringResource(R.string.flood_chatty_none))
                        if (chattyOpen) for (c in chatty) {
                            val v = c.verdict
                            Label(c.name + (if (c.keys > 1) " " + stringResource(R.string.flood_chatty_linked, c.keys - 1) else "") + (if (c.flagged) "  ⚠ " + stringResource(R.string.flood_chatty_flagged) else ""))
                            Hint(stringResource(R.string.flood_chatty_stats, v.messages, "%.2f".format(v.diversity), "%.2f".format(v.empty), stringResource(if (v.spam) R.string.flood_verdict_spam else R.string.flood_verdict_human)) + " — « ${c.example} »")
                        }
                    }
                }
                item {
                    FloodCard {
                        AccordionHeader(stringResource(R.string.flood_hidden_title, hidden.size), hiddenOpen) { hiddenOpen = !hiddenOpen }
                        if (hiddenOpen) {
                            if (hidden.isEmpty()) Hint(stringResource(R.string.flood_hidden_none))
                            else ChatbitButton(text = stringResource(R.string.flood_hidden_clear), onClick = { BitchatShield.clearHidden() })
                        }
                    }
                }
                if (hiddenOpen) items(hidden.take(50), key = { "${it.pubkey}-${it.atMs}-${it.content.hashCode()}" }) { h ->
                    Surface(shape = RoundedCornerShape(10.dp), color = colorScheme.surfaceVariant.copy(alpha = 0.15f)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Label("${h.name} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(h.atMs))}")
                                Text(h.content, fontFamily = BitchatFontFamily, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                            ChatbitButton(text = stringResource(R.string.flood_not_spammer), onClick = { BitchatShield.unflag(h.pubkey) })
                        }
                    }
                }
                item {
                    FloodCard {
                        AccordionHeader(stringResource(R.string.blocked_users_title, blocked.size), blockedOpen) { blockedOpen = !blockedOpen }
                        if (blockedOpen && blocked.isEmpty()) Hint(stringResource(R.string.blocked_users_none))
                    }
                }
                if (blockedOpen) items(blocked, key = { "blocked-$it" }) { pk ->
                    Surface(shape = RoundedCornerShape(10.dp), color = colorScheme.surfaceVariant.copy(alpha = 0.3f)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Label((geohashViewModel.knownNickname(pk) ?: "anon") + "#" + pk.takeLast(4))
                                Hint(pk.take(16) + "…")
                            }
                            ChatbitButton(text = stringResource(R.string.unblock_short), onClick = { geohashViewModel.unblockPubkey(pk) })
                        }
                    }
                }
                item {
                    FloodCard {
                        Label(stringResource(R.string.flood_lists_title), bold = true)
                        Hint(stringResource(R.string.flood_export))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton(stringResource(R.string.flood_blocked_file, "txt")) { export(ShieldLists.Kind.BLOCKED, false) }
                            SmallButton(stringResource(R.string.flood_blocked_file, "json")) { export(ShieldLists.Kind.BLOCKED, true) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton(stringResource(R.string.flood_flagged_file, "txt")) { export(ShieldLists.Kind.FLAGGED, false) }
                            SmallButton(stringResource(R.string.flood_flagged_file, "json")) { export(ShieldLists.Kind.FLAGGED, true) }
                        }
                        Hint(stringResource(R.string.flood_send_direct))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton(stringResource(R.string.flood_send_blocked)) { shareDirect(ShieldLists.Kind.BLOCKED) }
                            SmallButton(stringResource(R.string.flood_send_flagged)) { shareDirect(ShieldLists.Kind.FLAGGED) }
                        }
                        Hint(stringResource(R.string.flood_import_hint))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton(stringResource(R.string.flood_import_block)) {
                                importTarget = ShieldLists.Kind.BLOCKED
                                importLauncher.launch(arrayOf("text/*", "application/json", "application/octet-stream"))
                            }
                            SmallButton(stringResource(R.string.flood_import_flag)) {
                                importTarget = ShieldLists.Kind.FLAGGED
                                importLauncher.launch(arrayOf("text/*", "application/json", "application/octet-stream"))
                            }
                        }
                    }
                }
            }
            BitchatSheetTopBar(
                onClose = onDismiss,
                modifier = Modifier.align(Alignment.TopCenter),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Shield, contentDescription = null)
                        BitchatSheetTitle(stringResource(R.string.flood_sheet_title))
                    }
                }
            )
        }
    }
}

@Composable
private fun FloodCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Label(text: String, bold: Boolean = false, modifier: Modifier = Modifier) {
    Text(text, fontFamily = BitchatFontFamily, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal, modifier = modifier)
}

@Composable
private fun Hint(text: String) {
    Text(text, fontFamily = BitchatFontFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    ChatbitButton(text = text, onClick = onClick)
}

/** Slider that saves only when released (no prefs write per pixel). */
@Composable
private fun RuleSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: String,
    onSave: (Float) -> Unit
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row {
            Text(label, fontFamily = BitchatFontFamily, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.padding(4.dp))
            Text(format.format(current), fontFamily = BitchatFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Slider(value = current, onValueChange = { current = it }, valueRange = range, steps = steps, onValueChangeFinished = { onSave(current) })
    }
}

/** Tappable card title with a chevron; folds a long list. */
@Composable
private fun AccordionHeader(title: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Label(title, bold = true, modifier = Modifier.weight(1f))
        androidx.compose.material3.Icon(
            if (open) androidx.compose.material.icons.Icons.Filled.ExpandLess else androidx.compose.material.icons.Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
