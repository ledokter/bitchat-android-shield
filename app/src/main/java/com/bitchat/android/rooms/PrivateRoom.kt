package com.bitchat.android.rooms

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A chabit private room: a random 16-byte ID and a 32-byte AES key shared by invitation.
 *
 * Messages travel as ephemeral Nostr events tagged ["h", [tag]] where the tag is derived from the
 * ID (relays never see the ID, the key or the room name). The content is AES-256-GCM over a small
 * JSON payload (nickname + text), so relays only see an opaque blob. Each room gets its own random
 * Nostr identity, so rooms cannot be linked to each other or to public channels.
 */
data class PrivateRoom(
    val id: ByteArray,
    val key: ByteArray,
    val name: String,
    val identityPrivateKeyHex: String,
    val joinedAtMs: Long
) {
    val idHex: String get() = id.toHex()

    /** Public routing tag: hex(SHA-256("chabit-room-v1" || id))[0..32]. */
    val tag: String get() = tagFor(id)

    fun inviteLink(): String = INVITE_PREFIX + encodeInvite(id, key, name)

    fun encrypt(plain: ByteArray, random: SecureRandom = SecureRandom()): String {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(tag.toByteArray())
        return B64.encodeToString(nonce + cipher.doFinal(plain))
    }

    /** Null when the blob was not made with this room's key (wrong room, tampering). */
    fun decrypt(blob: String): ByteArray? = try {
        val raw = B64D.decode(blob)
        if (raw.size <= NONCE_BYTES + 16) null else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, raw, 0, NONCE_BYTES))
            cipher.updateAAD(tag.toByteArray())
            cipher.doFinal(raw, NONCE_BYTES, raw.size - NONCE_BYTES)
        }
    } catch (_: Exception) {
        null
    }

    override fun equals(other: Any?) = other is PrivateRoom && other.id.contentEquals(id)
    override fun hashCode() = id.contentHashCode()

    data class Invite(val id: ByteArray, val key: ByteArray, val name: String)

    companion object {
        const val INVITE_PREFIX = "chabit://room#"
        const val MAX_NAME_BYTES = 64
        private const val VERSION: Byte = 1
        private const val NONCE_BYTES = 12
        private val B64 = Base64.getUrlEncoder().withoutPadding()
        private val B64D = Base64.getUrlDecoder()

        fun create(name: String, identityPrivateKeyHex: String, random: SecureRandom = SecureRandom(), nowMs: Long = System.currentTimeMillis()) =
            PrivateRoom(
                ByteArray(16).also(random::nextBytes),
                ByteArray(32).also(random::nextBytes),
                cleanName(name),
                identityPrivateKeyHex,
                nowMs
            )

        fun tagFor(id: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest("chabit-room-v1".toByteArray() + id).toHex().take(32)

        fun cleanName(name: String): String {
            var n = name.trim().replace(Regex("\\s+"), " ")
            while (n.toByteArray().size > MAX_NAME_BYTES) n = n.dropLast(1)
            return n
        }

        fun encodeInvite(id: ByteArray, key: ByteArray, name: String): String {
            val nameBytes = cleanName(name).toByteArray()
            val buf = ByteBuffer.allocate(1 + 16 + 32 + nameBytes.size)
            buf.put(VERSION).put(id).put(key).put(nameBytes)
            return B64.encodeToString(buf.array())
        }

        /** Accepts the full link, the part after '#', or a text containing the link. */
        fun parseInvite(text: String): Invite? = try {
            val token = text.trim().let { t ->
                val i = t.indexOf(INVITE_PREFIX)
                if (i >= 0) t.substring(i + INVITE_PREFIX.length).takeWhile { !it.isWhitespace() } else t
            }
            val raw = B64D.decode(token)
            if (raw.size < 49 || raw[0] != VERSION) null else {
                val name = String(raw, 49, raw.size - 49).take(MAX_NAME_BYTES)
                Invite(raw.copyOfRange(1, 17), raw.copyOfRange(17, 49), cleanName(name))
            }
        } catch (_: Exception) {
            null
        }

        /** Plaintext of a message: "1\n<nickname>\n<text>" (nickname without line breaks, 40 chars max). */
        fun encodeMessage(nickname: String, text: String): ByteArray =
            ("1\n" + nickname.replace(Regex("[\r\n]"), " ").take(40) + "\n" + text).toByteArray()

        fun decodeMessage(plain: ByteArray): Pair<String, String>? {
            val s = String(plain)
            if (!s.startsWith("1\n")) return null
            val rest = s.substring(2)
            val i = rest.indexOf('\n')
            return if (i < 0) null else rest.substring(0, i) to rest.substring(i + 1)
        }

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}
