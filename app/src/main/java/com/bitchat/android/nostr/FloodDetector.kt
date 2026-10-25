package com.bitchat.android.nostr

import java.text.Normalizer
import java.util.ArrayDeque
import java.util.zip.Deflater
import kotlin.math.pow

/**
 * Two-stage flood detector for geohash channels (port of the bitchat-bot anti-flood).
 *
 * Every message adds points to its sender key for suspicious behaviour (rate, repetition,
 * same text or link from several keys, nickname rotation, zalgo…). Keys that obviously belong
 * to the same person are linked (union-find) and scored as a group. A group is flagged only when
 * 1) its score reaches [Rules.threshold] AND 2) it is chatty (≥ [Rules.minChatty] messages in
 * 10 min) AND its content looks like spam (low text diversity or mostly empty messages).
 * A human chatting fast varies their messages, so they are never flagged on volume alone.
 * Only a "zalgo bomb" (hundreds of stacked diacritics) is flagged on the first message.
 *
 * Pure Kotlin (no Android dependency); times are in seconds.
 */
class FloodDetector(@Volatile var rules: Rules = Rules()) {

    data class Rules(
        val rateWindow: Int = 30,          // s
        val maxRate: Int = 8,              // messages per rateWindow before penalty
        val repeatWindow: Int = 600,       // s
        val maxRepeat: Int = 3,            // same text, same key
        val multiKeyWindow: Int = 120,     // s
        val minKeysSameText: Int = 3,      // same text coming from that many keys
        val minLenMultiKey: Int = 6,       // ignores "lol", "hi"…
        val maxNicknames: Int = 3,         // different nicknames for one key (10 min)
        val maxKeysPerNickname: Int = 3,   // different keys for one nickname (10 min)
        val giantMessage: Int = 800,       // characters
        val halfLife: Int = 900,           // s, score decay
        val threshold: Double = 6.0,       // group score from which a group can be flagged
        val maxShort: Int = 6,             // 1-3 character messages in 60 s before penalty
        val minKeysSameLink: Int = 3,      // same domain posted by that many new or dedicated keys
        val minKeysPattern: Int = 4,       // same nickname pattern (bot_3fa9c2e1…) on that many new keys
        val zalgoBomb: Int = 100,          // stacked diacritics in one message: flagged at once
        val minChatty: Int = 6,            // messages in 10 min (key or linked group) to be "chatty"
        val maxDiversity: Double = 0.5,    // distinct texts / messages: below this it is spam
        val minEmpty: Double = 0.5,        // share of 1-3 character messages from which it is noise
        val disabled: Set<String> = emptySet(), // rule ids from [RULE_IDS] that add no points
        val keywords: List<String> = emptyList()
    )

    data class Verdict(
        val messages: Int,
        val diversity: Double,
        val empty: Double,
        val chatty: Boolean,
        val spam: Boolean
    )

    data class Alert(val root: String, val keys: List<String>, val score: Double, val newKeys: Int)

    data class Chatty(
        val pubkey: String,
        val name: String,
        val keys: Int,
        val flagged: Boolean,
        val verdict: Verdict,
        val example: String
    )

    data class KeyReport(
        val pubkey: String,
        val nicknames: List<String>,
        val messages: Int,
        val score: Double,
        val reasons: Map<String, Int>,
        val examples: List<String>
    )

    private class Info(var first: Double, var last: Double, var count: Int = 0) {
        val geohashes = HashSet<String>()
        var teleport = false
    }

    private val msgs = HashMap<String, ArrayDeque<Pair<Double, String>>>()
    private val nicknames = HashMap<String, LinkedHashMap<String, Double>>()
    private val keysByNickname = HashMap<String, HashMap<String, Double>>()
    private val keysByText = HashMap<String, HashMap<String, Double>>()
    private val keysByLink = HashMap<String, HashMap<String, Double>>()
    private val keysByPattern = HashMap<String, HashMap<String, Double>>()
    private val floodTexts = ArrayDeque<Pair<Set<String>, String>>()
    private val score = HashMap<String, Pair<Double, Double>>()
    private val reasons = HashMap<String, HashMap<String, Int>>()
    private val examples = HashMap<String, ArrayDeque<String>>()
    private val info = HashMap<String, Info>()
    private val parent = HashMap<String, String>()
    private var version = 0
    private var groupsCache: Pair<Int, Map<String, List<String>>> = -1 to emptyMap()
    private val alertAt = HashMap<String, Double>()
    private var start: Double? = null
    private var lastPurge = 0.0

    /** Keys flagged as flooders (persisted by the caller). */
    val flagged: MutableSet<String> = HashSet()

    /** Keys declared as false positives: never scored. */
    val ignored: MutableSet<String> = HashSet()

    // union-find: links keys of the same person
    private fun root(pk: String): String {
        parent.putIfAbsent(pk, pk)
        var k = pk
        while (parent[k] != k) {
            val gp = parent[parent[k]!!]!!
            parent[k] = gp
            k = gp
        }
        return k
    }

    private fun link(a: String, b: String) {
        val ra = root(a)
        val rb = root(b)
        if (ra != rb) {
            parent[rb] = ra
            version++
            alertAt[ra] = maxOf(alertAt[ra] ?: 0.0, alertAt.remove(rb) ?: 0.0)
        }
    }

    @Synchronized
    fun group(pk: String): List<String> {
        if (groupsCache.first != version) {
            val g = HashMap<String, MutableList<String>>()
            for (k in parent.keys.toList()) g.getOrPut(root(k)) { mutableListOf() }.add(k)
            groupsCache = version to g
        }
        return groupsCache.second[root(pk)] ?: listOf(pk)
    }

    private fun keyScore(pk: String, now: Double): Double {
        val (v, t) = score[pk] ?: return 0.0
        return v * 0.5.pow((now - t) / rules.halfLife)
    }

    private fun add(pk: String, pts: Double, reason: String, now: Double) {
        if (reason in rules.disabled) return
        score[pk] = (keyScore(pk, now) + pts) to now
        val r = reasons.getOrPut(pk) { HashMap() }
        r[reason] = (r[reason] ?: 0) + 1
    }

    @Synchronized
    fun groupScore(pk: String, now: Double): Double = group(pk).sumOf { keyScore(it, now) }

    @Synchronized
    fun isFlagged(pk: String): Boolean = pk in flagged

    /** Analyses one message; returns an alert when a group gets (newly) flagged. */
    @Synchronized
    fun process(
        pubkey: String,
        content: String,
        nickname: String?,
        geohash: String? = null,
        teleport: Boolean = false,
        now: Double = System.currentTimeMillis() / 1000.0
    ): Alert? {
        val r = rules
        val pk = pubkey
        if (start == null) start = now
        if (pk in ignored) return null
        if (now - lastPurge > 300) {
            purge(now)
            lastPurge = now
        }
        val norm = normalize(content)
        // action messages sent by the app itself ("* anon42 took a screenshot *", hugs, slaps):
        // identical for everybody, they don't count as text
        val action = RE_ACTION.matches(content.trim())
        var bomb = false
        val nick = nickname ?: "anon"
        val nickLower = nick.lowercase()

        val inf = info.getOrPut(pk) { Info(now, now) }
        inf.last = now
        inf.count++
        geohash?.let { inf.geohashes.add(it) }
        inf.teleport = inf.teleport || teleport
        root(pk)

        val dq = msgs.getOrPut(pk) { ArrayDeque() }
        dq.addLast(now to norm)
        while (dq.isNotEmpty() && now - dq.first.first > r.repeatWindow) dq.removeFirst()
        val nickMap = nicknames.getOrPut(pk) { LinkedHashMap() }
        val newNickname = nick !in nickMap
        nickMap[nick] = now
        examples.getOrPut(pk) { ArrayDeque() }.apply {
            addLast(content.take(200))
            while (size > 3) removeFirst()
        }
        keysByNickname.getOrPut(nickLower) { HashMap() }[pk] = now
        keysByText.getOrPut(norm) { HashMap() }[pk] = now

        // 1. rate
        if (dq.count { now - it.first <= r.rateWindow } > r.maxRate) add(pk, 1.0, RULE_RATE, now)
        // 2. same text among its last 5 messages ("ok", "lol": 5 out of 5)
        val last5 = dq.toList().takeLast(5).map { it.second }
        if (!action && last5.count { it == norm } >= (if (norm.length >= r.minLenMultiKey) r.maxRepeat else 5)) {
            // nobody sends the same long sentence three times in a row
            add(pk, if (norm.length >= 20) 2.0 else 1.0, RULE_REPEAT, now)
        }
        // 2b. burst of meaningless messages ("g", "hh", "yy"…)
        if (norm.length <= 3 && dq.count { now - it.first <= 60 && it.second.length <= 3 } > r.maxShort) {
            add(pk, 1.0, RULE_SHORT, now)
        }
        // 2c. (near) copy of a text already sent by a flagged flooder, from another key
        val words = if (norm.length >= 20 && !action) norm.split(' ').toSet() else null
        if (words != null && pk !in flagged) {
            for ((w2, k2) in floodTexts) {
                if (k2 != pk && (words intersect w2).size >= 0.75 * (words union w2).size) {
                    // points but no link: someone pasting a spam once is not the spammer
                    add(pk, 2.0, RULE_COPY, now)
                    break
                }
            }
        }
        val young = now - inf.first <= 600 && inf.first - (start ?: now) > 60
        // 3. same text from several keys (throwaway keys)
        if (norm.length >= r.minLenMultiKey && !action) {
            val keys = keysByText[norm]!!.filter { now - it.value <= r.multiKeyWindow }.keys
            if (keys.size >= r.minKeysSameText) {
                // "happy new year" sent by several regulars must not link them: only link keys
                // that say almost nothing else, and only if the text is long or the key brand new
                fun dedicated(k: String): Boolean {
                    val ns = msgs[k]?.map { it.second } ?: return false
                    return ns.isNotEmpty() && ns.count { it == norm } * 2 >= ns.size
                }
                if (dedicated(pk) && (norm.length >= 20 || young)) {
                    for (k in keys) if (dedicated(k)) link(pk, k)
                    add(pk, 1.5, RULE_SAME_TEXT, now)
                } else {
                    add(pk, 0.3, RULE_SAME_TEXT, now)
                }
            }
        }
        // 4. nickname rotation on one key
        if (newNickname && nickMap.count { now - it.value <= 600 } >= r.maxNicknames) {
            add(pk, 2.0, RULE_NICKNAMES, now)
        }
        // 5. same nickname on several keys
        val sameNick = keysByNickname[nickLower]!!.filter { now - it.value <= 600 }.keys
        if (sameNick.size >= r.maxKeysPerNickname && !nickLower.startsWith("anon")) {
            for (k in sameNick) link(pk, k)
            if (inf.count == 1) add(pk, 1.0, RULE_NICK_KEYS, now)
        }
        // 5b. same nickname pattern on new keys (bot_3fa9c2e1, bot_77b04d1e…)
        val pattern = nicknamePattern(nick)
        if (pattern != null && young && inf.count <= 3) {
            val m = keysByPattern.getOrPut(pattern) { HashMap() }
            m[pk] = now
            val keys = m.filter { now - it.value <= 600 }.keys
            if (keys.size >= r.minKeysPattern) {
                for (k in keys) link(pk, k)
                add(pk, 1.0, RULE_PATTERN, now)
            }
        }
        // 5c. same link posted by several new keys or keys used only for that (ads)
        val domains = RE_LINK.findAll(content).map { it.groupValues[1].lowercase().removePrefix("www.") }.toSet() - COMMON_LINKS
        for (dom in domains) {
            val nd = normalize(dom)
            val with = dq.count { nd in it.second }
            if (inf.count > 3 || !(young || with * 2 >= dq.size)) continue // regulars share what they want
            val m = keysByLink.getOrPut(dom) { HashMap() }
            m[pk] = now
            val keys = m.filter { now - it.value <= 600 }.keys
            if (keys.size >= r.minKeysSameLink) {
                for (k in keys) link(pk, k)
                add(pk, 1.5, RULE_LINK, now)
            }
        }
        // 6. giant or highly repetitive messages
        if (content.length > r.giantMessage || (content.length > 200 && compressedRatio(content) < 0.15)) {
            add(pk, 1.0, RULE_GIANT, now)
        }
        // 7. zalgo text (stacked diacritics)
        if (content.length >= 20) {
            val mn = content.codePoints().filter { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }.count()
            if (mn >= r.zalgoBomb && RULE_ZALGO !in r.disabled) {
                // hundreds of stacked diacritics overflow the whole screen: nuisance from the 1st message
                add(pk, r.threshold, RULE_ZALGO, now)
                bomb = true
            } else if (mn > 0.3 * content.length) {
                add(pk, 2.0, RULE_ZALGO, now)
            }
        }
        // 8. user keywords
        if (r.keywords.isNotEmpty()) {
            val low = content.lowercase()
            if (r.keywords.any { it.isNotBlank() && it.lowercase() in low }) add(pk, 1.0, RULE_KEYWORD, now)
        }

        if (words != null && pk in flagged && floodTexts.none { it.first == words }) {
            floodTexts.addLast(words to pk)
            while (floodTexts.size > 300) floodTexts.removeFirst()
        }
        // points are not enough: the group must be chatty AND its content spam
        if (groupScore(pk, now) >= r.threshold && (bomb || verdict(pk, now).spam)) {
            val g = group(pk)
            val fresh = g.count { it !in flagged }
            flagged.addAll(g)
            val rt = root(pk)
            // a flooder with throwaway keys adds a key per message: one alert per minute is enough
            if (now - (alertAt[rt] ?: 0.0) > (if (fresh > 0) 60 else 1800)) {
                alertAt[rt] = now
                return Alert(rt, g, groupScore(pk, now), fresh)
            }
        }
        return null
    }

    /** Stage 1: chatty? (last 10 min, key or linked group). Stage 2, chatty only: diversity and empty share. */
    @Synchronized
    fun verdict(pk: String, now: Double): Verdict {
        val r = rules
        var g = group(pk)
        if (g.size > 60) g = g.sortedByDescending { info[it]?.last ?: 0.0 }.take(60)
        val ns = g.flatMap { k -> msgs[k]?.filter { now - it.first <= 600 }?.map { it.second } ?: emptyList() }
        val n = ns.size
        val div = if (n > 0) ns.toSet().size.toDouble() / n else 1.0
        val empty = if (n > 0) ns.count { it.length <= 3 }.toDouble() / n else 0.0
        val chatty = n >= r.minChatty
        return Verdict(n, div, empty, chatty, chatty && (div <= r.maxDiversity || empty >= r.minEmpty))
    }

    /** Chattiest keys (or groups) of the last 10 minutes, with their verdict. */
    @Synchronized
    fun chatty(now: Double = System.currentTimeMillis() / 1000.0, limit: Int = 40): List<Chatty> {
        val seen = HashSet<String>()
        val out = ArrayList<Chatty>()
        val counts = msgs.mapValues { (_, dq) -> dq.count { now - it.first <= 600 } }.filterValues { it > 0 }
        for (k in counts.keys.sortedByDescending { counts[it] }) {
            if (!seen.add(root(k))) continue
            val v = verdict(k, now)
            if (!v.chatty) continue
            out += Chatty(k, displayName(k), group(k).size, k in flagged, v, examples[k]?.lastOrNull()?.take(120) ?: "")
            if (out.size >= limit) break
        }
        return out
    }

    @Synchronized
    fun report(pk: String, now: Double = System.currentTimeMillis() / 1000.0): KeyReport = KeyReport(
        pk,
        nicknames[pk]?.entries?.sortedByDescending { it.value }?.map { it.key } ?: emptyList(),
        info[pk]?.count ?: 0,
        keyScore(pk, now),
        reasons[pk]?.toMap() ?: emptyMap(),
        examples[pk]?.toList() ?: emptyList()
    )

    @Synchronized
    fun displayName(pk: String): String {
        val nick = nicknames[pk]?.entries?.maxByOrNull { it.value }?.key ?: "anon"
        return "$nick#${pk.takeLast(4)}"
    }

    /** Forgets inactive, non-flagged keys (bounded memory). */
    @Synchronized
    fun purge(now: Double, age: Double = 3600.0) {
        val old = info.filter { now - it.value.last > age && it.key !in flagged }.keys.toSet()
        for (k in old) {
            msgs.remove(k); nicknames.remove(k); score.remove(k); reasons.remove(k); examples.remove(k); info.remove(k)
        }
        for (d in listOf(keysByNickname, keysByText, keysByLink, keysByPattern)) {
            val it = d.entries.iterator()
            while (it.hasNext()) {
                val e = it.next()
                e.value.entries.removeAll { it.key in old || now - it.value > age }
                if (e.value.isEmpty()) it.remove()
            }
        }
        val refs = parent.filter { it.key != it.value }.values.toSet()
        for (k in old) if (parent[k] == k && k !in refs) parent.remove(k)
        version++
    }

    /** Clears all state (tests, panic reset). */
    @Synchronized
    fun reset() {
        for (m in listOf(msgs, nicknames, keysByNickname, keysByText, keysByLink, keysByPattern, score, reasons,
            examples, info, parent, alertAt)) m.clear()
        floodTexts.clear(); flagged.clear(); ignored.clear()
        start = null; lastPurge = 0.0; version++
    }

    /** Forgets everything about these keys being flooders (false positive or manual reset). */
    @Synchronized
    fun unflag(keys: Collection<String>) {
        flagged.removeAll(keys.toSet())
        for (k in keys) score.remove(k)
    }

    companion object {
        const val RULE_RATE = "rate"
        const val RULE_REPEAT = "repeat"
        const val RULE_SHORT = "short"
        const val RULE_COPY = "copy"
        const val RULE_SAME_TEXT = "same_text"
        const val RULE_NICKNAMES = "nicknames"
        const val RULE_NICK_KEYS = "nickname_keys"
        const val RULE_PATTERN = "pattern"
        const val RULE_LINK = "link"
        const val RULE_GIANT = "giant"
        const val RULE_ZALGO = "zalgo"
        const val RULE_KEYWORD = "keyword"
        val RULE_IDS = listOf(
            RULE_RATE, RULE_REPEAT, RULE_SHORT, RULE_COPY, RULE_SAME_TEXT, RULE_NICKNAMES,
            RULE_NICK_KEYS, RULE_PATTERN, RULE_LINK, RULE_GIANT, RULE_ZALGO, RULE_KEYWORD
        )

        // sites shared by everybody: they don't link keys together
        val COMMON_LINKS = setOf(
            "youtube.com", "youtu.be", "x.com", "twitter.com", "instagram.com", "tiktok.com", "google.com",
            "wikipedia.org", "reddit.com", "imgur.com", "giphy.com", "tenor.com", "github.com", "t.me",
            "facebook.com", "twitch.tv", "spotify.com", "apple.com", "nostr.com", "primal.net", "bitchat.free"
        )
        private val RE_ACTION = Regex("""\* .{1,200} \*""", RegexOption.DOT_MATCHES_ALL)
        private val RE_LINK = Regex("""(?:https?://|www\.)([a-z0-9.-]+\.[a-z]{2,})""", RegexOption.IGNORE_CASE)
        private val RE_DIGITS = Regex("""\d+""")
        private val RE_RUN = Regex("""(.)\1{3,}""")
        private val RE_SEP = Regex("""(?U)[^\w#]+""")
        private val RE_SPACES = Regex("""\s+""")
        private val RE_HEX = Regex("""[0-9a-f]{6,}""")

        fun normalize(text: String): String {
            var t = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
            t = RE_DIGITS.replace(t, "#")          // "spam 1", "spam 2"… = same text
            t = RE_RUN.replace(t, "$1$1$1")
            t = RE_SEP.replace(t, " ")             // punctuation and emoji separators ignored…
            t = RE_SPACES.replace(t, " ").trim()
            return t.ifEmpty { RE_SPACES.replace(text, "") } // …unless the message is only that
        }

        /** "bot_3fa9c2e1" → "bot_#h", "spam1234" → "spam#"; null if nothing varies or bitchat's default "anon1234". */
        fun nicknamePattern(nickname: String?): String? {
            val p = (nickname ?: "").lowercase()
            val m = RE_DIGITS.replace(RE_HEX.replace(p, "#h"), "#")
            if (m == p || m.startsWith("anon") || m.replace("#", "").replace("h", "").length < 2) return null
            return m
        }

        private fun compressedRatio(text: String): Double {
            val input = text.toByteArray()
            val d = Deflater()
            d.setInput(input)
            d.finish()
            val buf = ByteArray(input.size + 64)
            val n = d.deflate(buf)
            d.end()
            return n.toDouble() / input.size
        }
    }
}

/** Stores [FloodDetector.Rules] as "name=value" lines (no JSON dependency, readable in prefs dumps). */
object FloodRulesCodec {
    fun encode(r: FloodDetector.Rules): String = listOf(
        "rateWindow=${r.rateWindow}", "maxRate=${r.maxRate}", "maxRepeat=${r.maxRepeat}",
        "minKeysSameText=${r.minKeysSameText}", "maxNicknames=${r.maxNicknames}",
        "maxKeysPerNickname=${r.maxKeysPerNickname}", "giantMessage=${r.giantMessage}", "halfLife=${r.halfLife}",
        "threshold=${r.threshold}", "maxShort=${r.maxShort}", "minKeysSameLink=${r.minKeysSameLink}",
        "minKeysPattern=${r.minKeysPattern}", "zalgoBomb=${r.zalgoBomb}", "minChatty=${r.minChatty}",
        "maxDiversity=${r.maxDiversity}", "minEmpty=${r.minEmpty}",
        "disabled=${r.disabled.joinToString(",")}",
        "keywords=${r.keywords.joinToString(",") { it.replace(",", " ").replace("\n", " ") }}"
    ).joinToString("\n")

    fun decode(s: String): FloodDetector.Rules {
        val m = s.lines().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
        val d = FloodDetector.Rules()
        fun i(k: String, def: Int, lo: Int, hi: Int) = (m[k]?.toIntOrNull() ?: def).coerceIn(lo, hi)
        fun f(k: String, def: Double, lo: Double, hi: Double) = (m[k]?.toDoubleOrNull() ?: def).coerceIn(lo, hi)
        fun list(k: String) = m[k]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        return d.copy(
            rateWindow = i("rateWindow", d.rateWindow, 5, 600),
            maxRate = i("maxRate", d.maxRate, 1, 100),
            maxRepeat = i("maxRepeat", d.maxRepeat, 2, 20),
            minKeysSameText = i("minKeysSameText", d.minKeysSameText, 2, 20),
            maxNicknames = i("maxNicknames", d.maxNicknames, 2, 20),
            maxKeysPerNickname = i("maxKeysPerNickname", d.maxKeysPerNickname, 2, 20),
            giantMessage = i("giantMessage", d.giantMessage, 100, 5000),
            halfLife = i("halfLife", d.halfLife, 60, 7200),
            threshold = f("threshold", d.threshold, 1.0, 50.0),
            maxShort = i("maxShort", d.maxShort, 2, 50),
            minKeysSameLink = i("minKeysSameLink", d.minKeysSameLink, 2, 20),
            minKeysPattern = i("minKeysPattern", d.minKeysPattern, 2, 20),
            zalgoBomb = i("zalgoBomb", d.zalgoBomb, 20, 2000),
            minChatty = i("minChatty", d.minChatty, 2, 100),
            maxDiversity = f("maxDiversity", d.maxDiversity, 0.05, 1.0),
            minEmpty = f("minEmpty", d.minEmpty, 0.1, 1.0),
            disabled = list("disabled").filter { it in FloodDetector.RULE_IDS }.toSet(),
            keywords = list("keywords")
        )
    }
}
