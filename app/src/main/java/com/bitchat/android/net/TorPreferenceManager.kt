package com.bitchat.android.net

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object TorPreferenceManager {
    private const val PREFS_NAME = "bitchat_settings"
    private const val KEY_TOR_MODE = "tor_mode_v3"

    private val _modeFlow = MutableStateFlow(TorMode.ON)
    val modeFlow: StateFlow<TorMode> = _modeFlow

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_TOR_MODE, TorMode.ON.name)
        val mode = runCatching { TorMode.valueOf(saved ?: TorMode.ON.name) }.getOrDefault(TorMode.ON)
        _modeFlow.value = mode
    }

    // Rotation automatique de la route Tor (minutes, 0 = jamais)
    private const val KEY_ROTATION_MIN = "tor_rotation_minutes"
    const val DEFAULT_ROTATION_MIN = 30
    val ROTATION_CHOICES = listOf(0, 10, 30, 60, 180)

    private val _rotationFlow = MutableStateFlow(DEFAULT_ROTATION_MIN)
    val rotationFlow: StateFlow<Int> = _rotationFlow

    fun loadRotation(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _rotationFlow.value = prefs.getInt(KEY_ROTATION_MIN, DEFAULT_ROTATION_MIN).coerceIn(0, 1440)
    }

    fun setRotation(context: Context, minutes: Int) {
        val m = minutes.coerceIn(0, 1440)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_ROTATION_MIN, m).apply()
        _rotationFlow.value = m
    }

    fun set(context: Context, mode: TorMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOR_MODE, mode.name).apply()
        _modeFlow.value = mode
    }

    fun get(context: Context): TorMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_TOR_MODE, TorMode.ON.name)
        return runCatching { TorMode.valueOf(saved ?: TorMode.ON.name) }.getOrDefault(TorMode.ON)
    }
}
