package com.bitchat.android.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * chabit privacy and safety settings. Every protection is ON by default ("optimised defaults");
 * the numeric tunables are only shown in advanced mode.
 */
object SecurityPreferences {
    private const val PREFS_NAME = "chabit_security"

    private const val KEY_ADVANCED = "advanced_mode"
    private const val KEY_KILL_SWITCH = "tor_kill_switch"
    private const val KEY_PRESENCE = "send_presence"
    private const val KEY_EPHEMERAL_HOURS = "ephemeral_hours"
    private const val KEY_BLOCK_SCREENSHOTS = "block_screenshots"
    private const val KEY_APP_LOCK = "app_lock"
    private const val KEY_CLIPBOARD_CLEAR = "clipboard_clear"
    private const val KEY_AUTO_WIPE_DAYS = "auto_wipe_days"
    private const val KEY_LAST_OPEN = "last_open_ms"

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

    private val _autoWipeDays = MutableStateFlow(DEFAULT_AUTO_WIPE_DAYS)
    val autoWipeDays: StateFlow<Int> = _autoWipeDays.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _advanced.value = prefs.getBoolean(KEY_ADVANCED, false)
        _killSwitch.value = prefs.getBoolean(KEY_KILL_SWITCH, true)
        _presence.value = prefs.getBoolean(KEY_PRESENCE, false)
        _ephemeralHours.value = prefs.getInt(KEY_EPHEMERAL_HOURS, DEFAULT_EPHEMERAL_HOURS)
        _blockScreenshots.value = prefs.getBoolean(KEY_BLOCK_SCREENSHOTS, true)
        _appLock.value = prefs.getBoolean(KEY_APP_LOCK, true)
        _clipboardClear.value = prefs.getBoolean(KEY_CLIPBOARD_CLEAR, true)
        _autoWipeDays.value = prefs.getInt(KEY_AUTO_WIPE_DAYS, DEFAULT_AUTO_WIPE_DAYS)
    }

    private fun put(block: SharedPreferences.Editor.() -> Unit) {
        if (::prefs.isInitialized) prefs.edit().apply(block).apply()
    }

    fun setAdvancedMode(v: Boolean) { _advanced.value = v; put { putBoolean(KEY_ADVANCED, v) } }
    fun setKillSwitch(v: Boolean) { _killSwitch.value = v; put { putBoolean(KEY_KILL_SWITCH, v) } }
    fun setSendPresence(v: Boolean) { _presence.value = v; put { putBoolean(KEY_PRESENCE, v) } }
    fun setEphemeralHours(v: Int) { _ephemeralHours.value = v; put { putInt(KEY_EPHEMERAL_HOURS, v) } }
    fun setBlockScreenshots(v: Boolean) { _blockScreenshots.value = v; put { putBoolean(KEY_BLOCK_SCREENSHOTS, v) } }
    fun setAppLock(v: Boolean) { _appLock.value = v; put { putBoolean(KEY_APP_LOCK, v) } }
    fun setClipboardClear(v: Boolean) { _clipboardClear.value = v; put { putBoolean(KEY_CLIPBOARD_CLEAR, v) } }
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
