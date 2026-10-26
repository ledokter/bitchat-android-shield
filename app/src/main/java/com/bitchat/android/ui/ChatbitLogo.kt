package com.bitchat.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bitchat.android.R
import com.bitchat.android.game.KittyGame
import com.bitchat.android.game.KittyOutfit

/**
 * The chatbit pixel-art cat; its bow follows the chosen accent colour. With [dressed], it wears
 * the outfit picked in the kitty collection ([outfit] forces one, e.g. for previews).
 */
@Composable
fun ChatbitLogo(
    height: Dp = 64.dp,
    modifier: Modifier = Modifier,
    dressed: Boolean = false,
    outfit: KittyOutfit? = null
) {
    val worn by KittyGame.outfit.collectAsState()
    val shown = outfit ?: worn.takeIf { dressed }
    Box(modifier.height(height).aspectRatio(24f / 21f)) {
        Image(painterResource(R.drawable.chatbit_cat), contentDescription = null, modifier = Modifier.fillMaxSize())
        Image(
            painterResource(R.drawable.chatbit_bow),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxSize()
        )
        Image(painterResource(R.drawable.chatbit_bow_knot), contentDescription = null, modifier = Modifier.fillMaxSize())
        if (shown != null) Canvas(Modifier.fillMaxSize()) { drawOutfit(shown) }
    }
}

private val INK = Color(0xFF1A1A1A)

/** Outfits drawn on the logo's 24×21 pixel grid. */
private fun DrawScope.drawOutfit(outfit: KittyOutfit) {
    val c = size.width / 24f
    fun px(x: Int, y: Int, w: Int, h: Int, color: Color) =
        drawRect(color, Offset(x * c, y * c), Size(w * c, h * c))
    when (outfit) {
        KittyOutfit.SCARF -> {
            val red = Color(0xFFE53935); val dark = Color(0xFFB71C1C)
            px(4, 17, 16, 2, red); px(4, 18, 16, 1, dark)
            px(14, 19, 3, 2, red); px(14, 20, 3, 1, dark)
        }
        KittyOutfit.BERET -> {
            val beret = Color(0xFF3949AB)
            px(6, 4, 9, 2, beret); px(7, 3, 6, 1, beret); px(10, 2, 1, 1, INK)
            px(6, 6, 9, 1, Color(0xFF283593))
        }
        KittyOutfit.CROWN -> {
            val gold = Color(0xFFFFC828); val shade = Color(0xFFE0A800)
            px(8, 4, 6, 2, gold); px(8, 5, 6, 1, shade)
            px(8, 2, 1, 2, gold); px(10, 2, 2, 2, gold); px(13, 2, 1, 2, gold)
            px(10, 4, 2, 1, Color(0xFFE83C78))
        }
        KittyOutfit.EXPLORER -> {
            val hat = Color(0xFFC8A165); val band = Color(0xFF6D4C41)
            px(8, 2, 6, 3, hat); px(8, 4, 6, 1, band); px(5, 5, 12, 1, hat); px(5, 6, 12, 1, Color(0xFFA17F4A))
        }
        KittyOutfit.SUNGLASSES -> {
            px(5, 11, 4, 3, INK); px(15, 11, 4, 3, INK); px(9, 11, 6, 1, INK)
            px(6, 11, 1, 1, Color(0xFF90CAF9)); px(16, 11, 1, 1, Color(0xFF90CAF9))
        }
        KittyOutfit.TOR_HOOD -> {
            val hood = Color(0xFF7D4698); val dark = Color(0xFF59316B)
            px(1, 3, 22, 4, hood); px(0, 7, 3, 9, hood); px(21, 7, 3, 9, hood)
            px(3, 7, 18, 1, dark)
            px(11, 4, 2, 2, Color(0xFFF5F5F5)); px(11, 4, 2, 1, Color(0xFFCDB6DB))
        }
        KittyOutfit.THUG_LIFE -> {
            // "deal with it" pixel shades
            px(4, 10, 16, 1, INK)
            px(5, 11, 5, 1, INK); px(14, 11, 5, 1, INK)
            px(6, 12, 4, 1, INK); px(15, 12, 4, 1, INK)
            px(6, 11, 1, 1, Color.White); px(7, 12, 1, 1, Color.White)
            px(15, 11, 1, 1, Color.White); px(16, 12, 1, 1, Color.White)
            // joint, glowing tip and a puff of smoke
            px(12, 15, 5, 1, Color(0xFFF5F0E1)); px(12, 16, 1, 1, Color(0xFFD7CCB0))
            px(17, 15, 1, 1, Color(0xFFFF6D00)); px(18, 15, 1, 1, Color(0xFFFFC828))
            px(19, 13, 1, 1, Color(0xFFBDBDBD)); px(20, 12, 1, 1, Color(0xFF9E9E9E)); px(20, 10, 2, 1, Color(0xFFBDBDBD).copy(alpha = 0.7f))
        }
        KittyOutfit.PIRATE -> {
            px(5, 11, 4, 3, INK); px(9, 10, 12, 1, INK); px(3, 10, 2, 1, INK)
            val bandana = Color(0xFFD32F2F)
            px(6, 5, 12, 2, bandana); px(9, 5, 1, 1, Color.White); px(13, 6, 1, 1, Color.White)
        }
    }
}
