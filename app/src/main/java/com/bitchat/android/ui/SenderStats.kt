package com.bitchat.android.ui

import android.content.Context
import android.content.SharedPreferences
import com.bitchat.android.model.BitchatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.bitchat.android.R
import com.bitchat.android.ui.theme.LocalBitchatPalette
import org.json.JSONObject

/**
 * What this phone has seen of each sender: how many of their messages, and since when. Shown next
 * to the nickname so a newcomer flooding a room stands out from a regular. Kept on this phone only
 * and erased by the panic wipe.
 */
object SenderStats {
    data class Stat(val count: Int, val firstSeen: Long)

    private const val PREFS = "chatbit_sender_stats"
    private const val MAX_SEEN = 4000
    private const val MAX_SENDERS = 3000

    private val _stats = MutableStateFlow<Map<String, Stat>>(emptyMap())
    val stats: StateFlow<Map<String, Stat>> = _stats.asStateFlow()
    private val seen = LinkedHashSet<String>()
    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        runCatching {
            val json = JSONObject(p.getString("stats", "{}")!!)
            _stats.value = json.keys().asSequence().associateWith { k ->
                json.getJSONArray(k).let { Stat(it.getInt(0), it.getLong(1)) }
            }
        }
        seen += p.getString("seen", "")!!.split(',').filter { it.isNotEmpty() }
    }

    /** One key per person: their Nostr key in zones, their mesh peer ID, else the nickname. */
    fun keyFor(m: BitchatMessage): String = m.senderNostrPubkey ?: m.senderPeerID ?: m.sender

    /** Counts the messages not counted yet. */
    @Synchronized
    fun record(messages: List<BitchatMessage>) {
        val next = _stats.value.toMutableMap()
        var changed = false
        for (m in messages) {
            if (m.sender == "system" || !seen.add(m.id)) continue
            val key = keyFor(m)
            val at = m.timestamp.time
            val old = next[key]
            next[key] = if (old == null) Stat(1, at) else Stat(old.count + 1, minOf(old.firstSeen, at))
            changed = true
        }
        if (!changed) return
        while (seen.size > MAX_SEEN) seen.remove(seen.first())
        if (next.size > MAX_SENDERS) {
            // forget the senders seen least
            next.entries.sortedBy { it.value.count }.take(next.size - MAX_SENDERS).forEach { next.remove(it.key) }
        }
        _stats.value = next
        val json = JSONObject()
        next.forEach { (k, s) -> json.put(k, org.json.JSONArray().put(s.count).put(s.firstSeen)) }
        prefs?.edit()?.putString("stats", json.toString())?.putString("seen", seen.joinToString(","))?.apply()
    }

    @Synchronized
    fun wipe() {
        _stats.value = emptyMap()
        seen.clear()
        prefs?.edit()?.clear()?.apply()
    }
}

/** "12 msg" (accent colour) and "3 d" or "new" (purple) after someone else's nickname. */
@Composable
internal fun senderExtras(message: BitchatMessage, isSelf: Boolean): List<Pair<String, Color>> {
    val stats by SenderStats.stats.collectAsState()
    if (isSelf || message.sender == "system") return emptyList()
    val s = stats[SenderStats.keyFor(message)] ?: return emptyList()
    val days = ((System.currentTimeMillis() - s.firstSeen) / 86_400_000L).toInt().coerceAtLeast(0)
    return listOf(
        pluralStringResource(R.plurals.sender_msgs, s.count, s.count) to MaterialTheme.colorScheme.primary,
        (if (days == 0) stringResource(R.string.sender_new) else stringResource(R.string.sender_days, days)) to LocalBitchatPalette.current.accentPurple
    )
}
