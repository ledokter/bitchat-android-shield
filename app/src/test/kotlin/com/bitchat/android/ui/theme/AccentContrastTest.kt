package com.bitchat.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG AA (4.5:1 for normal text), as recommended by Material / Android accessibility guidelines. */
class AccentContrastTest {
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun check(name: String, fg: Color, bg: Color) {
        val c = contrast(fg, bg)
        assertTrue("$name: contrast %.2f < 4.5".format(c), c >= 4.5)
    }

    @Test
    fun everyAccentIsReadableInBothThemes() {
        for (accent in AccentColor.entries) {
            for (dark in listOf(false, true)) {
                val base = if (dark) DarkBitchatColorScheme else LightBitchatColorScheme
                val s = base.withAccent(accent, dark)
                val tag = "${accent.name}/${if (dark) "dark" else "light"}"
                check("$tag primary on background", s.primary, s.background)
                check("$tag primary on surface", s.primary, s.surface)
                check("$tag onPrimary on primary", s.onPrimary, s.primary)
                check("$tag onPrimaryContainer on primaryContainer", s.onPrimaryContainer, s.primaryContainer)
            }
        }
    }
}
