package com.bitchat.android.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.util.Base64
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Photos, voice notes and files kept on the phone are encrypted (AES-256-GCM) with a random key
 * that is itself stored encrypted by Android Keystore ([SecurePrefs]). A forensic copy of the app
 * folder only yields unreadable blobs, and deleting the key (panic wipe) makes every leftover
 * block unrecoverable. Files written before this existed, or by the watch, stay readable: the
 * reader looks for the [MAGIC] header.
 */
object MediaVault {
    private const val PREFS = "chatbit_vault"
    private const val KEY_DATA_KEY = "media_key"
    private val MAGIC = byteArrayOf(0x43, 0x42, 0x56, 0x31) // "CBV1"
    private const val IV_BYTES = 12

    /** Only the phone app turns this on; the watch keeps writing plain files. */
    @Volatile var enabled = false
        private set

    @Volatile private var key: SecretKeySpec? = null
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        enabled = true
    }

    @Synchronized
    private fun key(): SecretKeySpec {
        key?.let { return it }
        val prefs = SecurePrefs.open(appContext, PREFS)
        val raw = prefs.getString(KEY_DATA_KEY, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: ByteArray(32).also {
                SecureRandom().nextBytes(it)
                prefs.edit().putString(KEY_DATA_KEY, Base64.encodeToString(it, Base64.NO_WRAP)).commit()
            }
        return SecretKeySpec(raw, "AES").also { key = it }
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return MAGIC + cipher.iv + cipher.doFinal(plain)
    }

    private fun isSealed(bytes: ByteArray) =
        bytes.size > MAGIC.size + IV_BYTES && MAGIC.indices.all { bytes[it] == MAGIC[it] }

    fun isSealed(file: File): Boolean = runCatching {
        file.inputStream().use { s -> ByteArray(MAGIC.size).also { s.read(it) }.contentEquals(MAGIC) }
    }.getOrDefault(false)

    /** Writes [bytes] to [file], encrypted when the vault is on. */
    fun write(file: File, bytes: ByteArray) {
        file.writeBytes(if (enabled) encrypt(bytes) else bytes)
    }

    /** Plain content of [file], whether it is encrypted or not. */
    fun readBytes(file: File): ByteArray {
        val bytes = file.readBytes()
        if (!isSealed(bytes)) return bytes
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, MAGIC.size, IV_BYTES))
        return cipher.doFinal(bytes, MAGIC.size + IV_BYTES, bytes.size - MAGIC.size - IV_BYTES)
    }

    fun readBytes(path: String): ByteArray = readBytes(File(path))

    /** Encrypts an existing plain file in place (e.g. a sent photo or a recorded voice note). */
    fun seal(path: String) {
        if (!enabled) return
        runCatching {
            val file = File(path)
            if (!file.isFile || isSealed(file)) return
            val tmp = File(file.parentFile, file.name + ".seal")
            tmp.writeBytes(encrypt(file.readBytes()))
            if (!tmp.renameTo(file)) tmp.delete()
        }
    }

    fun decodeBitmap(path: String): Bitmap? = runCatching {
        val bytes = readBytes(path)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    /** In-memory source for MediaPlayer / MediaExtractor; null when the file is plain. */
    fun dataSource(path: String): MediaDataSource? {
        val file = File(path)
        if (!isSealed(file)) return null
        val bytes = readBytes(file)
        return object : MediaDataSource() {
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                if (position >= bytes.size) return -1
                val n = minOf(size.toLong(), bytes.size - position).toInt()
                System.arraycopy(bytes, position.toInt(), buffer, offset, n)
                return n
            }
            override fun getSize(): Long = bytes.size.toLong()
            override fun close() {}
        }
    }

    /** Panic wipe: forgets the key, so encrypted leftovers can never be read again. */
    @Synchronized
    fun destroyKey() {
        key = null
        if (::appContext.isInitialized) SecurePrefs.open(appContext, PREFS).edit().clear().commit()
    }
}
