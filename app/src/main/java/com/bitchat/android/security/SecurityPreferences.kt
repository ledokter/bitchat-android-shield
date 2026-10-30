package com.bitchat.android.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * chatbit privacy and safety settings. Every protection is ON by default ("optimised defaults");
 * the numeric tunables are only shown in advanced mode.
 */
object SecurityPreferences {
    private const val PREFS_NAME = "chatbit_security"

    private const val KEY_ADVANCED = "advanced_mode"
    private const val KEY_KILL_SWITCH = "tor_kill_switch"
    private const val KEY_PRESENCE = "send_presence"
    private const val KEY_EPHEMERAL_HOURS = "ephemeral_hours"
    private const val KEY_BLOCK_SCREENSHOTS = "block_screenshots"
    private const val KEY_APP_LOCK = "app_lock"
    private const val KEY_CLIPBOARD_CLEAR = "clipboard_clear"
    private const val KEY_LINKS_TOR = "links_tor"
    private const val KEY_AUTO_WIPE_DAYS = "auto_wipe_days"
    private const val KEY_LAST_OPEN = "last_open_ms"
    private const val KEY_SHOW_BLOCKED = "show_blocked"
    private const val KEY_PIN = "pin_hash"
    private const val KEY_DURESS = "duress_hash"
    private const val KEY_PIN_FAILS = "pin_fails"
private const val KEY_PIN_LEN = "pin_len"
private const val KEY_DURESS_LEN = "duress_len"
    private const val KEY_PIN_LOCKOUT = "pin_lockout_until"
    private const val PIN_ITERATIONS = 20_000
    const val PIN_MIN = 4
    const val PIN_MAX = 8

    const val DEFAULT_EPHEMERAL_HOURS = 24
    const val DEFAULT_AUTO_WIPE_DAYS = 30
    const val CLIPBOARD_CLEAR_SECONDS = 60L
    val EPHEMERAL_CHOICES = listOf(0, 1, 24, 72, 168)      // hours, 0 = keep
    val AUTO_WIPE_CHOICES = listOf(0, 7, 30, 90)           // days, 0 = never

    private lateinit var prefs: SharedPreferences

    private val _advanced = MutableStateFlow(false)
    val advancedMode: StateFlow<Boolean> = _advanced.asStateFlow()

    private val _killSwitch = MutableStateFlow(true)
    val killSwitch: StateFlow<Boolean> = _killSwitch.asStateFlow()

    private val _presence = MutableStateFlow(false)
    val sendPresence: StateFlow<Boolean> = _presence.asStateFlow()

    private val _ephemeralHours = MutableStateFlow(DEFAULT_EPHEMERAL_HOURS)
    val ephemeralHours: StateFlow<Int> = _ephemeralHours.asStateFlow()

    private val _blockScreenshots = MutableStateFlow(true)
    val blockScreenshots: StateFlow<Boolean> = _blockScreenshots.asStateFlow()

    private val _appLock = MutableStateFlow(true)
    val appLock: StateFlow<Boolean> = _appLock.asStateFlow()

    private val _clipboardClear = MutableStateFlow(true)
    val clipboardClear: StateFlow<Boolean> = _clipboardClear.asStateFlow()

    /** Links open in Tor Browser (on by default); off, chatbit opens no link at all (see TorLinks). */
    private val _linksViaTor = MutableStateFlow(true)
    val linksViaTor: StateFlow<Boolean> = _linksViaTor.asStateFlow()

    private val _autoWipeDays = MutableStateFlow(DEFAULT_AUTO_WIPE_DAYS)
    val autoWipeDays: StateFlow<Int> = _autoWipeDays.asStateFlow()

    private val _showBlocked = MutableStateFlow(false)
    /** Show messages of blocked / reported people in the chat (hidden by default). */
    val showBlocked: StateFlow<Boolean> = _showBlocked.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = SecurePrefs.open(context, PREFS_NAME)
        _advanced.value = prefs.getBoolean(KEY_ADVANCED, false)
        _killSwitch.value = prefs.getBoolean(KEY_KILL_SWITCH, true)
        _presence.value = prefs.getBoolean(KEY_PRESENCE, false)
        _ephemeralHours.value = prefs.getInt(KEY_EPHEMERAL_HOURS, DEFAULT_EPHEMERAL_HOURS)
        _blockScreenshots.value = prefs.getBoolean(KEY_BLOCK_SCREENSHOTS, true)
        _appLock.value = prefs.getBoolean(KEY_APP_LOCK, true)
        _clipboardClear.value = prefs.getBoolean(KEY_CLIPBOARD_CLEAR, true)
        _linksViaTor.value = prefs.getBoolean(KEY_LINKS_TOR, true)
        _autoWipeDays.value = prefs.getInt(KEY_AUTO_WIPE_DAYS, DEFAULT_AUTO_WIPE_DAYS)
        _showBlocked.value = prefs.getBoolean(KEY_SHOW_BLOCKED, false)
        loadPins()
    }

    private fun put(block: SharedPreferences.Editor.() -> Unit) {
        if (::prefs.isInitialized) prefs.edit().apply(block).apply()
    }

    fun setLinksViaTor(v: Boolean) { _linksViaTor.value = v; put { putBoolean(KEY_LINKS_TOR, v) } }
    fun setAdvancedMode(v: Boolean) { _advanced.value = v; put { putBoolean(KEY_ADVANCED, v) } }
    fun setKillSwitch(v: Boolean) { _killSwitch.value = v; put { putBoolean(KEY_KILL_SWITCH, v) } }
    fun setSendPresence(v: Boolean) { _presence.value = v; put { putBoolean(KEY_PRESENCE, v) } }
    fun setEphemeralHours(v: Int) { _ephemeralHours.value = v; put { putInt(KEY_EPHEMERAL_HOURS, v) } }
    /**
     * Debug builds running on Firebase Test Lab keep screenshots so the automated crawl can check
     * every screen and translation; real users always get the protection.
     */
    fun isTestLab(context: Context): Boolean = com.bitchat.android.BuildConfig.DEBUG &&
        runCatching {
            android.provider.Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true"
        }.getOrDefault(false)

    fun setBlockScreenshots(v: Boolean) { _blockScreenshots.value = v; put { putBoolean(KEY_BLOCK_SCREENSHOTS, v) } }
    fun setAppLock(v: Boolean) { _appLock.value = v; put { putBoolean(KEY_APP_LOCK, v) } }
    fun setClipboardClear(v: Boolean) { _clipboardClear.value = v; put { putBoolean(KEY_CLIPBOARD_CLEAR, v) } }
    // MARK: chatbit PIN and duress PIN

    enum class PinResult { OK, DURESS, WRONG, LOCKED_OUT }

    private val _hasPin = MutableStateFlow(false)
    /** A chatbit PIN replaces the system unlock (fingerprint / screen code) when set. */
    val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

    private val _hasDuress = MutableStateFlow(false)
    /** Typing the duress PIN at the lock screen silently wipes everything and opens an empty app. */
    val hasDuress: StateFlow<Boolean> = _hasDuress.asStateFlow()

    private fun loadPins() {
        _hasPin.value = prefs.getString(KEY_PIN, null) != null
        _hasDuress.value = prefs.getString(KEY_DURESS, null) != null
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, PIN_ITERATIONS, 256)
        return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun encode(pin: String): String {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val b64 = { b: ByteArray -> android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP) }
        return b64(salt) + ":" + b64(hash(pin, salt))
    }

    private fun matches(pin: String, stored: String?): Boolean {
        val parts = stored?.split(':')?.takeIf { it.size == 2 } ?: return false
        val salt = android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP)
        val expected = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
        return java.security.MessageDigest.isEqual(expected, hash(pin, salt))
    }

    fun isValidPin(pin: String) = pin.length in PIN_MIN..PIN_MAX && pin.all { it.isDigit() }

    /** Sets (or removes with null) the chatbit PIN; removing it also removes the duress PIN. */
    fun setPin(pin: String?) {
        put {
            if (pin == null) { remove(KEY_PIN); remove(KEY_DURESS); remove(KEY_PIN_LEN); remove(KEY_DURESS_LEN) }
            else { putString(KEY_PIN, encode(pin)); putInt(KEY_PIN_LEN, pin.length) }
            putInt(KEY_PIN_FAILS, 0)
        }
        if (pin != null) setAppLock(true)
        _hasPin.value = pin != null
        if (pin == null) _hasDuress.value = false
    }

    enum class DuressResult { OK, SAME_AS_PIN, WRONG_LENGTH }

    /** Digits of the PIN, or 0 when unknown (PIN set by an older version). */
    fun pinLength(): Int = prefs.getInt(KEY_PIN_LEN, 0)

    /**
     * The PIN is accepted as soon as its last digit is typed (no OK) when its length is known and
     * the duress PIN, if any, has the same length: neither code can then be the start of the other.
     */
    fun autoSubmitLength(): Int {
        val len = pinLength()
        if (len == 0) return 0
        val duressLen = prefs.getInt(KEY_DURESS_LEN, 0)
        return if (prefs.getString(KEY_DURESS, null) == null || duressLen == len) len else 0
    }

    /** The duress PIN must differ from the PIN and have as many digits (same dots on screen). */
    fun setDuressPin(pin: String?): DuressResult {
        if (pin != null) {
            if (matches(pin, prefs.getString(KEY_PIN, null))) return DuressResult.SAME_AS_PIN
            val len = pinLength()
            if (len != 0 && pin.length != len) return DuressResult.WRONG_LENGTH
        }
        put {
            if (pin == null) { remove(KEY_DURESS); remove(KEY_DURESS_LEN) }
            else { putString(KEY_DURESS, encode(pin)); putInt(KEY_DURESS_LEN, pin.length) }
        }
        _hasDuress.value = pin != null
        if (pin != null) com.bitchat.android.game.KittyGame.onDuressSet()
        return DuressResult.OK
    }

    /** Milliseconds to wait before the next try (after 5 wrong PINs: 30 s, doubling). */
    fun pinLockoutRemainingMs(nowMs: Long = System.currentTimeMillis()): Long =
        (prefs.getLong(KEY_PIN_LOCKOUT, 0L) - nowMs).coerceAtLeast(0L)

    fun checkPin(pin: String): PinResult {
        if (pinLockoutRemainingMs() > 0) return PinResult.LOCKED_OUT
        return when {
            matches(pin, prefs.getString(KEY_DURESS, null)) -> {
                // same visible outcome as the real PIN: no hint that a wipe happened
                put { putInt(KEY_PIN_FAILS, 0) }
                PinResult.DURESS
            }
            matches(pin, prefs.getString(KEY_PIN, null)) -> {
                put { putInt(KEY_PIN_FAILS, 0); putInt(KEY_PIN_LEN, pin.length) }
                PinResult.OK
            }
            else -> {
                val fails = prefs.getInt(KEY_PIN_FAILS, 0) + 1
                put {
                    putInt(KEY_PIN_FAILS, fails)
                    if (fails >= 5) putLong(KEY_PIN_LOCKOUT, System.currentTimeMillis() + (30_000L shl (fails - 5).coerceAtMost(8)))
                }
                PinResult.WRONG
            }
        }
    }

    fun setShowBlocked(v: Boolean) { _showBlocked.value = v; put { putBoolean(KEY_SHOW_BLOCKED, v) } }
    fun setAutoWipeDays(v: Int) { _autoWipeDays.value = v; put { putInt(KEY_AUTO_WIPE_DAYS, v) } }

    /**
     * Returns true when the app was not opened for longer than the auto-wipe delay; records this
     * opening either way. The first launch never wipes.
     */
    fun recordOpenAndCheckWipe(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!::prefs.isInitialized) return false
        val last = prefs.getLong(KEY_LAST_OPEN, 0L)
        prefs.edit().putLong(KEY_LAST_OPEN, nowMs).apply()
        val days = _autoWipeDays.value
        return days > 0 && last > 0 && nowMs - last > days * 86_400_000L
    }

    /** Messages older than this are deleted from the device; null when kept forever. */
    fun ephemeralCutoffMs(nowMs: Long = System.currentTimeMillis()): Long? =
        _ephemeralHours.value.takeIf { it > 0 }?.let { nowMs - it * 3_600_000L }
}
