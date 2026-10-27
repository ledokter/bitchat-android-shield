package com.bitchat.android.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ShieldListsTest {
    // NIP-19 test vector
    private val hex = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val npub = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"
    private val other = "a".repeat(64)

    @Test
    fun npubRoundTrip() {
        assertEquals(hex, ShieldLists.npubToHex(npub))
        assertEquals(npub, ShieldLists.hexToNpub(hex))
        assertNull(ShieldLists.npubToHex(npub.dropLast(1) + "q")) // bad checksum
    }

    @Test
    fun textExportRoundTrip() {
        val names = mapOf(hex to "spammer")
        val txt = ShieldLists.export(ShieldLists.Kind.FLAGGED, listOf(hex, other), { names[it] }, json = false, now = Date(0))
        assertTrue(txt.startsWith("# bitchat — spammeurs signalés, 2 clés"))
        assertEquals(mapOf(hex to "spammer", other to null), ShieldLists.parse(txt))
    }

    @Test
    fun jsonExportRoundTrip() {
        val json = ShieldLists.export(ShieldLists.Kind.BLOCKED, listOf(hex), { "a \"b\"" }, json = true, now = Date(0))
        assertTrue(json.contains("\"type\": \"bitchat-bloques\""))
        assertTrue(json.contains(npub))
        assertEquals(setOf(hex), ShieldLists.parse(json).keys)
    }

    @Test
    fun parsesAnyText() {
        val found = ShieldLists.parse("bloquez $npub et aussi ${other.uppercase()} merci\nrien ici")
        assertEquals(setOf(hex, other), found.keys)
    }
}
