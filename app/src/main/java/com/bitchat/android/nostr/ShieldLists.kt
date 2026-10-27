package com.bitchat.android.nostr

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Import / export of blocked and flagged key lists, in the same formats as bitchat-bot
 * (one hex key per line with "# nickname#abcd" comments, or {"type","date","nombre","cles":[…]} JSON),
 * so a list can move between the bot and the phone in both directions.
 */
object ShieldLists {
    enum class Kind(val id: String, val title: String) {
        BLOCKED("bloques", "utilisateurs bloqués"),
        FLAGGED("signales", "spammeurs signalés")
    }

    private val RE_KEY = Regex("""\b(npub1[02-9ac-hj-np-z]{58})\b|\b([0-9a-fA-F]{64})\b""")
    private val RE_NAME = Regex("""#\s*([^#\n"]{1,40}?)#[0-9a-f]{4}\s*$|"pseudo":\s*"([^"]{1,40})"""")

    fun npubToHex(npub: String): String? = try {
        val (hrp, data) = Bech32.decode(npub.lowercase())
        if (hrp == "npub" && data.size == 32) data.joinToString("") { "%02x".format(it) } else null
    } catch (_: Exception) {
        null
    }

    fun hexToNpub(hex: String): String = try {
        Bech32.encode("npub", hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
    } catch (_: Exception) {
        ""
    }

    /** Every hex key (64 chars) or npub1… found in the text, with the nickname when the line has one. */
    fun parse(text: String): Map<String, String?> {
        val found = LinkedHashMap<String, String?>()
        for (line in text.lineSequence()) {
            var last: String? = null
            for (m in RE_KEY.findAll(line)) {
                val k = if (m.groupValues[1].isNotEmpty()) npubToHex(m.groupValues[1]) else m.groupValues[2].lowercase()
                if (k != null) {
                    found.putIfAbsent(k, null)
                    last = k
                }
            }
            val name = RE_NAME.find(line)?.let { (it.groupValues[1].ifEmpty { it.groupValues[2] }).trim() }
            if (last != null && !name.isNullOrEmpty()) found[last] = name
        }
        return found
    }

    fun fileName(kind: Kind, json: Boolean, now: Date = Date()): String =
        "bitchat-${kind.id}-${stamp(now)}.${if (json) "json" else "txt"}"

    fun export(kind: Kind, keys: Collection<String>, nameOf: (String) -> String?, json: Boolean, now: Date = Date()): String {
        val rows = keys.sorted().map { it to nameOf(it) }
        val date = stamp(now)
        if (json) {
            val items = rows.joinToString(",\n") { (k, p) ->
                " {\"pubkey\": \"$k\", \"npub\": \"${hexToNpub(k)}\", \"pseudo\": ${p?.let { "\"${escape(it)}\"" } ?: "null"}}"
            }
            return "{\"type\": \"bitchat-${kind.id}\", \"date\": \"$date\", \"nombre\": ${rows.size}, \"cles\": [\n$items\n]}"
        }
        val sb = StringBuilder()
        sb.append("# bitchat — ${kind.title}, ${rows.size} clés, exporté le $date\n")
        sb.append("# une clé par ligne (hex), le pseudo après le #\n")
        for ((k, p) in rows) sb.append(if (p != null) "$k  # $p#${k.takeLast(4)}\n" else "$k\n")
        return sb.toString()
    }

    private fun stamp(d: Date) = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(d)

    private fun escape(s: String) = buildString {
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
}
