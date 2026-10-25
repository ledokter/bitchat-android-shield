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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    FloodDetector.RULE_RATE to "Débit (trop de messages en peu de temps)",
    FloodDetector.RULE_REPEAT to "Répétition du même texte",
    FloodDetector.RULE_SHORT to "Rafales de messages vides (« g », « hh »…)",
    FloodDetector.RULE_COPY to "Copie d'un texte de flooder",
    FloodDetector.RULE_SAME_TEXT to "Même texte depuis plusieurs clés",
    FloodDetector.RULE_NICKNAMES to "Changements de pseudo sur une clé",
    FloodDetector.RULE_NICK_KEYS to "Même pseudo sur plusieurs clés",
    FloodDetector.RULE_PATTERN to "Même modèle de pseudo (bot_3fa9…)",
    FloodDetector.RULE_LINK to "Même lien posté par des clés neuves",
    FloodDetector.RULE_GIANT to "Messages géants ou très répétitifs",
    FloodDetector.RULE_ZALGO to "Texte zalgo (diacritiques empilés)",
    FloodDetector.RULE_KEYWORD to "Mots-clés choisis"
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
            Toast.makeText(context, if (ok) "Liste exportée" else "Échec de l'export", Toast.LENGTH_SHORT).show()
        }
    }
    fun export(kind: ShieldLists.Kind, json: Boolean) {
        val keys = if (kind == ShieldLists.Kind.BLOCKED) geohashViewModel.blockedPubkeys() else BitchatShield.flaggedKeys()
        pendingExport = ShieldLists.export(kind, keys, { geohashViewModel.knownNickname(it) }, json)
        exportLauncher.launch(ShieldLists.fileName(kind, json))
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
                Toast.makeText(context, "Fichier illisible", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val keys = withContext(Dispatchers.Default) { ShieldLists.parse(text).keys }
            val added = if (target == ShieldLists.Kind.BLOCKED) geohashViewModel.blockPubkeys(keys) else BitchatShield.flagKeys(keys)
            Toast.makeText(context, "${keys.size} clé(s) trouvée(s), $added ajoutée(s)", Toast.LENGTH_LONG).show()
        }
    }

    BitchatBottomSheet(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 80.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    FloodCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Label("Masquer les flooders", bold = true)
                                Hint("Détection en deux temps : d'abord les clés bavardes, ensuite seulement le contenu. $flaggedCount clé(s) signalée(s).")
                            }
                            Switch(checked = enabled, onCheckedChange = { BitchatShield.setFloodEnabled(it) })
                        }
                    }
                }
                item {
                    FloodCard {
                        Label("Détection", bold = true)
                        RuleSlider("Messages en 10 min pour être « bavard »", rules.minChatty.toFloat(), 2f..30f, 27, "%.0f") {
                            BitchatShield.setFloodRules(rules.copy(minChatty = it.toInt()))
                        }
                        RuleSlider("Diversité maximale d'un spammeur", rules.maxDiversity.toFloat(), 0.1f..1f, 17, "%.2f") {
                            BitchatShield.setFloodRules(rules.copy(maxDiversity = it.toDouble()))
                        }
                        RuleSlider("Part de messages vides à partir de laquelle c'est du bruit", rules.minEmpty.toFloat(), 0.1f..1f, 17, "%.2f") {
                            BitchatShield.setFloodRules(rules.copy(minEmpty = it.toDouble()))
                        }
                        RuleSlider("Seuil de points d'un groupe", rules.threshold.toFloat(), 2f..20f, 17, "%.0f") {
                            BitchatShield.setFloodRules(rules.copy(threshold = it.toDouble()))
                        }
                        RuleSlider("Messages par ${rules.rateWindow} s avant pénalité", rules.maxRate.toFloat(), 2f..30f, 27, "%.0f") {
                            BitchatShield.setFloodRules(rules.copy(maxRate = it.toInt()))
                        }
                        RuleSlider("Bombe zalgo (diacritiques dans un message)", rules.zalgoBomb.toFloat(), 20f..500f, 47, "%.0f") {
                            BitchatShield.setFloodRules(rules.copy(zalgoBomb = it.toInt()))
                        }
                        TextButton(onClick = { BitchatShield.setFloodRules(FloodDetector.Rules(disabled = rules.disabled, keywords = rules.keywords)) }) {
                            Text("Valeurs par défaut", fontFamily = BitchatFontFamily, fontSize = 12.sp)
                        }
                    }
                }
                item {
                    FloodCard {
                        Label("Règles", bold = true)
                        for (id in FloodDetector.RULE_IDS) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(RULE_LABELS[id] ?: id, fontFamily = BitchatFontFamily, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Switch(checked = id !in rules.disabled, onCheckedChange = { on ->
                                    BitchatShield.setFloodRules(rules.copy(disabled = if (on) rules.disabled - id else rules.disabled + id))
                                })
                            }
                        }
                        var keywords by remember { mutableStateOf(rules.keywords.joinToString(", ")) }
                        OutlinedTextField(
                            value = keywords,
                            onValueChange = { keywords = it },
                            label = { Text("Mots-clés (séparés par des virgules)", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(onClick = {
                            BitchatShield.setFloodRules(rules.copy(keywords = keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }))
                        }) { Text("Enregistrer les mots-clés", fontFamily = BitchatFontFamily, fontSize = 12.sp) }
                    }
                }
                item {
                    FloodCard {
                        Label("Clés bavardes (10 dernières minutes)", bold = true)
                        if (chatty.isEmpty()) Hint("Personne pour l'instant.")
                        for (c in chatty) {
                            val v = c.verdict
                            Label("${c.name}${if (c.keys > 1) " (+${c.keys - 1} clés liées)" else ""}${if (c.flagged) "  ⚠ signalé" else ""}")
                            Hint("${v.messages} msg · diversité ${"%.2f".format(v.diversity)} · vides ${"%.2f".format(v.empty)} · ${if (v.spam) "spam" else "humain"} — « ${c.example} »")
                        }
                    }
                }
                item {
                    FloodCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Label("Messages masqués (${hidden.size})", bold = true, modifier = Modifier.weight(1f))
                            if (hidden.isNotEmpty()) TextButton(onClick = { BitchatShield.clearHidden() }) {
                                Text("Vider", fontFamily = BitchatFontFamily, fontSize = 12.sp)
                            }
                        }
                        if (hidden.isEmpty()) Hint("Aucun message masqué.")
                    }
                }
                items(hidden.take(50), key = { "${it.pubkey}-${it.atMs}-${it.content.hashCode()}" }) { h ->
                    Surface(shape = RoundedCornerShape(10.dp), color = colorScheme.surfaceVariant.copy(alpha = 0.15f)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Label("${h.name} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(h.atMs))}")
                                Text(h.content, fontFamily = BitchatFontFamily, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { BitchatShield.unflag(h.pubkey) }) {
                                Text("pas un\nspammeur", fontFamily = BitchatFontFamily, fontSize = 11.sp)
                            }
                        }
                    }
                }
                item {
                    FloodCard {
                        Label("Listes (compatibles avec le bot)", bold = true)
                        Hint("Exporter")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton("bloqués .txt") { export(ShieldLists.Kind.BLOCKED, false) }
                            SmallButton("bloqués .json") { export(ShieldLists.Kind.BLOCKED, true) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton("signalés .txt") { export(ShieldLists.Kind.FLAGGED, false) }
                            SmallButton("signalés .json") { export(ShieldLists.Kind.FLAGGED, true) }
                        }
                        Hint("Importer un fichier (export du bot ou n'importe quel texte : chaque clé hex ou npub1… trouvée est prise)")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton("… et les bloquer") {
                                importTarget = ShieldLists.Kind.BLOCKED
                                importLauncher.launch(arrayOf("text/*", "application/json", "application/octet-stream"))
                            }
                            SmallButton("… comme spammeurs") {
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
                        BitchatSheetTitle("Anti-flood")
                    }
                }
            )
        }
    }
}

@Composable
private fun FloodCard(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Label(text: String, bold: Boolean = false, modifier: Modifier = Modifier) {
    Text(text, fontFamily = BitchatFontFamily, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal, modifier = modifier)
}

@Composable
private fun Hint(text: String) {
    Text(text, fontFamily = BitchatFontFamily, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, fontFamily = BitchatFontFamily, fontSize = 12.sp)
    }
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
