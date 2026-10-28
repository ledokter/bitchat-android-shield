package com.bitchat.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.R
import com.bitchat.android.core.ui.component.sheet.BitchatBottomSheet
import com.bitchat.android.monetization.ProStatus
import com.bitchat.android.monetization.RewardedAds
import com.bitchat.android.nostr.BitchatShield
import com.bitchat.android.ui.theme.BitchatFontFamily

internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** What a video unlocks, each time, in the free version. */
enum class AdGate(val title: Int) { ANTI_SPAM(R.string.adgate_title_spam), ADVANCED(R.string.adgate_title_advanced) }

/**
 * Page shown before a gated settings page: kitty explains that a short video keeps chatbit free,
 * that it loads outside Tor, and offers Pro instead. [onUnlocked] runs after the reward (or when no
 * video can be loaded, e.g. offline on the mesh: the page is then given for free).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdGateSheet(gate: AdGate, onUnlocked: () -> Unit, onDismiss: () -> Unit, onShowPro: () -> Unit) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    BitchatBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HoppingKitty(64.dp)
            Text(stringResource(gate.title), fontFamily = BitchatFontFamily, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
            Text(
                stringResource(R.string.adgate_body), fontFamily = BitchatFontFamily, fontSize = 14.sp, lineHeight = 20.sp,
                textAlign = TextAlign.Center, color = colorScheme.onSurface
            )
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colorScheme.surfaceVariant).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.adgate_tor_note), fontFamily = BitchatFontFamily, fontSize = 12.sp, lineHeight = 17.sp, color = colorScheme.onSurfaceVariant)
            }
            message?.let {
                Text(stringResource(it), fontFamily = BitchatFontFamily, fontSize = 13.sp, color = colorScheme.error, textAlign = TextAlign.Center)
            }
            Button(
                onClick = {
                    val activity = context.findActivity() ?: return@Button
                    loading = true
                    message = null
                    RewardedAds.show(activity) { result ->
                        loading = false
                        when (result) {
                            RewardedAds.Result.REWARDED -> onUnlocked()
                            RewardedAds.Result.CLOSED_EARLY -> message = R.string.adgate_skipped
                            RewardedAds.Result.UNAVAILABLE -> {
                                Toast.makeText(context, context.getString(R.string.adgate_unavailable), Toast.LENGTH_LONG).show()
                                onUnlocked()
                            }
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = colorScheme.onPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.adgate_loading), fontFamily = BitchatFontFamily)
                } else {
                    Icon(Icons.Filled.PlayCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.adgate_watch), fontFamily = BitchatFontFamily, fontWeight = FontWeight.Bold)
                }
            }
            OutlinedButton(
                onClick = onShowPro,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, colorScheme.primary)
            ) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.adgate_pro), fontFamily = BitchatFontFamily, color = colorScheme.primary)
            }
        }
    }
}

/** Kitty hopping gently, for the gate, the Pro page and the anti-spam tutorial. */
@Composable
internal fun HoppingKitty(height: androidx.compose.ui.unit.Dp) {
    val hop = rememberInfiniteTransition(label = "kittyHop").animateFloat(
        0f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "hop"
    )
    ChatbitLogo(height = height, dressed = true, modifier = Modifier.graphicsLayer { translationY = -hop.value * 10f })
}

private data class Plan(val name: Int, val fallbackPrice: Int, val note: Int, val product: String?, val basePlan: String?)

private val PLANS = listOf(
    Plan(R.string.pro_plan_yearly, R.string.pro_price_yearly, R.string.pro_note_yearly, ProStatus.SUB_PRO, "yearly"),
    Plan(R.string.pro_plan_monthly, R.string.pro_price_monthly, R.string.pro_note_monthly, ProStatus.SUB_PRO, "monthly"),
    Plan(R.string.pro_plan_founder, R.string.pro_price_founder, R.string.pro_note_founder, ProStatus.SUB_FOUNDER, "yearly"),
    Plan(R.string.pro_plan_event, R.string.pro_price_event, R.string.pro_note_event, ProStatus.EVENT_PASS, null),
    Plan(R.string.pro_plan_team, R.string.pro_price_team, R.string.pro_note_team, null, null),
)

/** chatbit Pro: what it gives, the prices we set, purchase through Google Play. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val isPro by ProStatus.isPro.collectAsState()
    val products by ProStatus.products.collectAsState()
    BitchatBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HoppingKitty(56.dp)
                Column {
                    Text(stringResource(R.string.pro_title), fontFamily = BitchatFontFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colorScheme.primary)
                    Text(stringResource(R.string.pro_tagline), fontFamily = BitchatFontFamily, fontSize = 13.sp, lineHeight = 18.sp, color = colorScheme.onSurface)
                }
            }
            if (isPro) {
                Text(stringResource(R.string.pro_active), fontFamily = BitchatFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = okColor())
            }
            Feature(R.string.pro_feat_noads, soon = false)
            Feature(R.string.pro_feat_rooms, soon = true)
            Feature(R.string.pro_feat_admin, soon = true)
            Feature(R.string.pro_feat_history, soon = true)
            Feature(R.string.pro_feat_relay, soon = true)

            PLANS.forEach { plan ->
                val price = plan.product?.let { ProStatus.price(it, plan.basePlan) } ?: stringResource(plan.fallbackPrice)
                val onSale = plan.product != null && products.containsKey(plan.product)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, if (plan === PLANS[0]) colorScheme.primary else colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(plan.name), fontFamily = BitchatFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                            Text(price, fontFamily = BitchatFontFamily, fontSize = 14.sp, color = colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(plan.note), fontFamily = BitchatFontFamily, fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        }
                        if (plan.product != null && !isPro) {
                            Button(
                                onClick = {
                                    val activity = context.findActivity()
                                    val started = activity != null && onSale && ProStatus.buy(activity, plan.product, plan.basePlan)
                                    if (!started) Toast.makeText(context, context.getString(R.string.pro_unavailable), Toast.LENGTH_LONG).show()
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                            ) { Text(stringResource(R.string.pro_choose), fontFamily = BitchatFontFamily) }
                        }
                    }
                }
            }
            Text(stringResource(R.string.pro_pay_note), fontFamily = BitchatFontFamily, fontSize = 12.sp, color = colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { ProStatus.refresh() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.pro_restore), fontFamily = BitchatFontFamily)
            }
        }
    }
}

@Composable
private fun Feature(text: Int, soon: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(if (soon) Icons.Filled.Schedule else Icons.Filled.CheckCircle, contentDescription = null,
            tint = if (soon) colorScheme.onSurfaceVariant else colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(stringResource(text), fontFamily = BitchatFontFamily, fontSize = 14.sp, color = colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (soon) Text(
            stringResource(R.string.pro_soon), fontFamily = BitchatFontFamily, fontSize = 11.sp, color = colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(CircleShape).background(colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

private val FLOOD_STEPS = listOf(
    R.string.tuto_flood_1, R.string.tuto_flood_2, R.string.tuto_flood_3, R.string.tuto_flood_4, R.string.tuto_flood_5
)

/**
 * Kitty walks through the anti-spam card, step by step: what to tick and why. The last step
 * offers to tick the recommended boxes at once.
 */
@Composable
internal fun FloodTutorial(onDone: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    var step by remember { mutableIntStateOf(0) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.primary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                HoppingKitty(44.dp)
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut() },
                    label = "floodTutorial"
                ) { s ->
                    Text(stringResource(FLOOD_STEPS[s]), fontFamily = BitchatFontFamily, fontSize = 13.sp, lineHeight = 19.sp, color = colorScheme.onSurface)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // progress dots
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FLOOD_STEPS.indices.forEach { i ->
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (i == step) colorScheme.primary else colorScheme.outlineVariant))
                    }
                }
                TextButton(onClick = onDone) { Text(stringResource(R.string.tour_skip), fontFamily = BitchatFontFamily) }
                if (step < FLOOD_STEPS.lastIndex) {
                    Button(onClick = { step++ }, shape = RoundedCornerShape(18.dp)) { Text(stringResource(R.string.tour_next), fontFamily = BitchatFontFamily) }
                } else {
                    Button(onClick = {
                        BitchatShield.setFloodEnabled(true)
                        BitchatShield.setBlockBots(true)
                        BitchatShield.setBlockLinks(true)
                        BitchatShield.setBlockZalgo(true)
                        onDone()
                    }, shape = RoundedCornerShape(18.dp)) { Text(stringResource(R.string.tuto_flood_tick_all), fontFamily = BitchatFontFamily) }
                }
            }
        }
    }
}
