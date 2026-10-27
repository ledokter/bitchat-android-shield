package com.bitchat.android.ui.theme

import android.content.Context
import androidx.annotation.StringRes
import com.bitchat.android.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App theme preference: System default, Light, or Dark.
 */
enum class ThemePreference {
    System,
    Light,
    Dark;

    val isSystem : Boolean get() = this == System
    val isLight : Boolean get() = this == Light
    val isDark : Boolean get() = this == Dark
}

/**
 * Primary accent colour, with one tone per theme. Every tone keeps a WCAG AA contrast of at least
 * 4.5:1 against the theme background and surface and against its on-colour (black on dark tones,
 * white on light tones); [AccentContrastTest] enforces it.
 */
enum class AccentColor(@StringRes val label: Int, val light: Long, val dark: Long) {
    Pink(R.string.accent_pink, 0xFFC2185B, 0xFFFF8AB4),
    Green(R.string.accent_green, 0xFF1B7A34, 0xFF32D74B),
    Blue(R.string.accent_blue, 0xFF1565C0, 0xFF64B5F6),
    Purple(R.string.accent_purple, 0xFF7B1FA2, 0xFFCE93D8),
    Orange(R.string.accent_orange, 0xFFB45309, 0xFFFFB74D),
    Red(R.string.accent_red, 0xFFC62828, 0xFFFF8A80),
    Teal(R.string.accent_teal, 0xFF00796B, 0xFF4DD0C4),
    Yellow(R.string.accent_yellow, 0xFF8A6100, 0xFFFFD54F);

    companion object {
        val DEFAULT = Pink
    }
}

/**
 * Simple SharedPreferences-backed manager for theme preference with a StateFlow.
 * Avoids adding DataStore dependency for now.
 */
object ThemePreferenceManager {
    private const val PREFS_NAME = "bitchat_settings"
    private const val KEY_THEME = "theme_preference"
    private const val KEY_ACCENT = "accent_color"

    private val _themeFlow = MutableStateFlow(ThemePreference.System)
    val themeFlow: StateFlow<ThemePreference> = _themeFlow

    private val _accentFlow = MutableStateFlow(AccentColor.DEFAULT)
    val accentFlow: StateFlow<AccentColor> = _accentFlow

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME, ThemePreference.System.name)
        _themeFlow.value = runCatching { ThemePreference.valueOf(saved!!) }.getOrDefault(ThemePreference.System)
        val accent = prefs.getString(KEY_ACCENT, AccentColor.DEFAULT.name)
        _accentFlow.value = runCatching { AccentColor.valueOf(accent!!) }.getOrDefault(AccentColor.DEFAULT)
    }

    fun setAccent(context: Context, accent: AccentColor) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_ACCENT, accent.name).apply()
        _accentFlow.value = accent
    }

    fun set(context: Context, preference: ThemePreference) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, preference.name).apply()
        _themeFlow.value = preference
    }
}
