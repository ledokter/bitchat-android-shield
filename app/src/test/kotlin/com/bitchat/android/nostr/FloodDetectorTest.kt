package com.bitchat.android.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class FloodDetectorTest {
    private lateinit var d: FloodDetector
    private var t = 1_000_000.0
    private val rnd = Random(42)

    private fun key(): String = (1..32).joinToString("") { "%02x".format(rnd.nextInt(256)) }
    private fun hex(bytes: Int): String = (1..bytes).joinToString("") { "%02x".format(rnd.nextInt(256)) }

    private fun send(pk: String, text: String, nick: String = "bob", step: Double = 1.0): FloodDetector.Alert? {
        t += step
        return d.process(pk, text, nick, "u0", now = t)
    }

    @Before
    fun setUp() {
        d = FloodDetector()
        t = 1_000_000.0
        d.process(key(), "start", "x", now = t) // sets the warm-up start
        t += 120
    }

    @Test
    fun normalConversationIsNotFlagged() {
        val people = listOf("alice", "bob", "chloe", "dan", "eve").map { key() to it }
        val sentences = listOf("salut", "ça va ?", "quelqu'un à Lyon ?", "lol", "oui", "trop bien",
            "il pleut ici", "bonne soirée", "des nouvelles du match ?", "ok")
        val alerts = (1..300).map {
            val (pk, nick) = people.random(rnd)
            send(pk, sentences.random(rnd), nick, rnd.nextDouble(3.0, 40.0))
        }
        assertTrue(alerts.all { it == null })
    }

    @Test
    fun happyNewYearFromRegularsIsNotLinked() {
        val people = List(12) { key() }
        for (pk in people) send(pk, "coucou", hex(3), 30.0)
        t += 3600
        val alerts = people.map { send(it, "Bonne année !!!", "p", 2.0) }
        assertTrue(alerts.all { it == null })
    }

    @Test
    fun singleKeyFloodIsFlagged() {
        val pk = key()
        val alert = (0 until 20).map { send(pk, "ACHETEZ MON TRUC $it !!!", "spam", 0.5) }.first { it != null }!!
        assertEquals(1, alert.keys.size)
        val reasons = d.report(pk, t).reasons
        assertTrue(FloodDetector.RULE_RATE in reasons)
        assertTrue(FloodDetector.RULE_REPEAT in reasons)
    }

    @Test
    fun throwawayKeysAreGrouped() {
        val keys = List(6) { key() }
        val alerts = (0 until 18).map { send(keys[it % 6], "rejoignez t.me/arnaque maintenant", "user$it", 3.0) }
        assertTrue(alerts.any { it != null })
        assertEquals(6, d.group(keys[0]).size)
    }

    @Test
    fun adBotsWithVariedTextsAreGrouped() {
        val sentences = listOf(
            "Coucou tu veux des edits de tes bagarres ? DECOUVRE https://pub-exemple.com",
            "Hey t'as des rushes de manif DECOUVRE https://pub-exemple.com meilleur site",
            "Salut, un montage qui claque : https://pub-exemple.com jugé meilleur site de l'année"
        )
        val alerts = (0 until 8).map { send(key(), sentences[it % 3], "bot_" + hex(4), 4.0) }
        assertTrue(alerts.filterNotNull().last().keys.size >= 4)
    }

    @Test
    fun regularsSharingALinkAreNotLinked() {
        val people = listOf("alice", "bob", "chloe", "dan", "eve").map { key() to it }
        repeat(6) { for ((pk, n) in people) send(pk, listOf("salut", "ça va", "ok", "lol").random(rnd), n, 20.0) }
        val alerts = people.map { (pk, n) -> send(pk, "regardez https://journal-exemple.fr/article", n, 10.0) }
        assertTrue(alerts.all { it == null })
        assertEquals(1, d.group(people[0].first).size)
    }

    @Test
    fun zalgoBombIsFlaggedOnFirstMessage() {
        val bomb = "#" + (0 until 300).map { (0x300 + it % 0x70).toChar() }.joinToString("")
        assertTrue(send(key(), bomb, "sDF") != null)
        assertNull(send(key(), "café crème à côté, ça va très bien", "alice"))
    }

    @Test
    fun screenshotActionsAreNotLinked() {
        val people = List(6) { key() }
        val alerts = (0 until 4).flatMap { people.mapIndexed { i, pk -> send(pk, "* anon${1000 + i} took a screenshot *", "anon${1000 + i}", 5.0) } }
        assertTrue(alerts.all { it == null })
    }

    @Test
    fun letterBurstIsFlagged() {
        val pk = key()
        val alerts = "g gf ff f ff fdd d ww w x xx g h".split(" ").map { send(pk, it, "Admin", 0.5) }
        assertTrue(alerts.any { it != null })
    }

    @Test
    fun sameLongSentenceRepeatedIsFlagged() {
        val pk = key()
        val alerts = (0 until 8).map { send(pk, "JE ME SUIS FAITE AVOIR PAR DES GENS, VOUS ME REGARDEZ AVEC VOTRE AIR", "anon9565", 2.0) }
        assertTrue(alerts.any { it != null })
    }

    @Test
    fun fastHumanChatterIsNotFlagged() {
        val pk = key()
        val words = ("ouais grave|t'es où là|on se retrouve à la place|trop de monde ici|les flics bloquent la rue|" +
            "tu gères|vas-y je te suis|c'est chaud ce soir|qui a des nouvelles de lyon|mdr t'es sérieux|" +
            "j'arrive dans 10 min|ils ont fermé le métro|prenez de l'eau|ça bouge côté nord|on lâche rien|" +
            "quelqu'un a vu léa|le cortège repart|attention aux lacrymos|merci pour l'info|bonne nuit à tous|" +
            "regardez le live|il pleut maintenant|on se capte demain|la batterie lâche|bien vu|" +
            "c'est qui lui|je comprends rien|ok ça marche|grosse ambiance|le son est ouf|" +
            "on reste groupés|y a un médic ici|rue bloquée à droite|passez par le pont|top merci|" +
            "je rentre|faites gaffe|on est combien|ça fait plaisir|à plus").split("|")
        val alerts = words.mapIndexed { i, m -> send(pk, "@ami${i % 7} $m", "emmagold", 4.5) }
        assertTrue(alerts.all { it == null })
    }

    @Test
    fun nicknameRotationIsFlagged() {
        val pk = key()
        val alerts = (0 until 8).map { send(pk, "blabla ${rnd.nextInt(1_000_000)}", "nom$it", 20.0) }
        assertTrue(alerts.any { it != null })
        assertTrue(FloodDetector.RULE_NICKNAMES in d.report(pk, t).reasons)
    }

    @Test
    fun disabledRuleAddsNoPoints() {
        d.rules = d.rules.copy(disabled = setOf(FloodDetector.RULE_ZALGO))
        val bomb = "#" + (0 until 300).map { (0x300 + it % 0x70).toChar() }.joinToString("")
        assertNull(send(key(), bomb, "sDF"))
    }

    @Test
    fun nicknamePattern() {
        assertEquals("bot_#h", FloodDetector.nicknamePattern("bot_3fa9c2e1"))
        assertNull(FloodDetector.nicknamePattern("anon4821"))
        assertNull(FloodDetector.nicknamePattern("satana"))
        assertNull(FloodDetector.nicknamePattern("3fa9c2e1"))
        assertFalse(FloodDetector.normalize("spam 1") != FloodDetector.normalize("spam 2"))
    }
}
