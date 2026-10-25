package com.bitchat.android.rooms

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class PrivateRoomTest {
    private val rnd = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(42L) }
    private fun room(name: String = "les amis 🐱") = PrivateRoom.create(name, "00".repeat(32), rnd, nowMs = 0)

    @Test
    fun inviteRoundTrip() {
        val r = room()
        val link = r.inviteLink()
        assertTrue(link.startsWith(PrivateRoom.INVITE_PREFIX))
        val inv = PrivateRoom.parseInvite("rejoins-nous : $link merci")!!
        assertArrayEquals(r.id, inv.id)
        assertArrayEquals(r.key, inv.key)
        assertEquals("les amis 🐱", inv.name)
    }

    @Test
    fun invalidInvitesAreRejected() {
        assertNull(PrivateRoom.parseInvite("chatbit://room#pas-une-invitation"))
        assertNull(PrivateRoom.parseInvite(""))
        assertNull(PrivateRoom.parseInvite("https://exemple.org"))
    }

    @Test
    fun encryptionRoundTripAndTamperDetection() {
        val r = room()
        val blob = r.encrypt(PrivateRoom.encodeMessage("alice", "salut\nça va ?"))
        assertEquals("alice" to "salut\nça va ?", PrivateRoom.decodeMessage(r.decrypt(blob)!!))
        // another room (other key) cannot read it
        assertNull(room().decrypt(blob))
        // a modified blob is rejected
        val tampered = blob.dropLast(2) + if (blob.endsWith("AA")) "BB" else "AA"
        assertNull(r.decrypt(tampered))
    }

    @Test
    fun tagIsStableAndDoesNotRevealTheId() {
        val r = room()
        assertEquals(32, r.tag.length)
        assertEquals(PrivateRoom.tagFor(r.id), r.tag)
        assertNotEquals(r.idHex.take(32), r.tag)
    }

    @Test
    fun nicknameCannotInjectLines() {
        val plain = PrivateRoom.encodeMessage("eve\nfake", "texte")
        assertEquals("eve fake" to "texte", PrivateRoom.decodeMessage(plain))
    }

    @Test
    fun longNamesAreTruncatedToTheLimit() {
        val r = room("x".repeat(200))
        assertTrue(r.name.toByteArray().size <= PrivateRoom.MAX_NAME_BYTES)
    }
}
