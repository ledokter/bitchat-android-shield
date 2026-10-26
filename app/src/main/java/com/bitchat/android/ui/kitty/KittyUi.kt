package com.bitchat.android.ui.kitty

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.R
import com.bitchat.android.game.Badge
import com.bitchat.android.game.KittyGame
import com.bitchat.android.game.KittyOutfit
import com.bitchat.android.game.KittyRank
import com.bitchat.android.ui.ChatbitLogo
import com.bitchat.android.ui.theme.BitchatFontFamily
import kotlinx.coroutines.delay

// MARK: - Speech bubble with kitty

/** Kitty with a speech bubble on its right. */
@Composable
fun KittySays(text: String, modifier: Modifier = Modifier, kittyHeight: androidx.compose.ui.unit.Dp = 40.dp) {
    val wiggle = rememberInfiniteTransition(label = "kittyWiggle").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "w"
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChatbitLogo(
            height = kittyHeight, dressed = true,
            modifier = Modifier.graphicsLayer {
                val t = wiggle.value
                rotationZ = if (t in 0.8f..0.9f) kotlin.math.sin((t - 0.8f) * 62.8f) * 8f else 0f
            }
        )
        Text(
            text,
            fontFamily = BitchatFontFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 11.dp, vertical = 8.dp)
        )
    }
}

/** Bouncing pointing finger (👆 or 👇). */
@Composable
fun PointingFinger(up: Boolean, modifier: Modifier = Modifier) {
    val bob = rememberInfiniteTransition(label = "finger").animateFloat(
        0f, 1f, infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b"
    )
    val d = with(LocalDensity.current) { 7.dp.toPx() }
    Text(
        if (up) "👆" else "👇", fontSize = 30.sp,
        modifier = modifier.graphicsLayer { translationY = (if (up) -1 else 1) * bob.value * d }
    )
}

// MARK: - First-time tips inside screens

/** A one-time kitty tip shown the first time a screen is opened. */
@Composable
fun KittyTip(id: String, text: String, modifier: Modifier = Modifier) {
    val seen by KittyGame.seenTips.collectAsState()
    AnimatedVisibility(visible = id !in seen, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                KittySays(text)
                TextButton(onClick = { KittyGame.markTipSeen(id) }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.kitty_got_it), fontFamily = BitchatFontFamily)
                }
            }
        }
    }
}

// MARK: - Guided tour on the main screen

/** Screen positions of the header items the tour points at. */
object KittyAnchors {
    val rects: SnapshotStateMap<String, Rect> = mutableStateMapOf()
    const val LOGO = "logo"
    const val CHANNEL = "channel"
    const val GLOBE = "globe"
    const val ROOMS = "rooms"
    const val PEOPLE = "people"
}

fun Modifier.kittyAnchor(id: String): Modifier = composed {
    DisposableEffect(id) { onDispose { KittyAnchors.rects.remove(id) } }
    onGloballyPositioned { KittyAnchors.rects[id] = it.boundsInWindow() }
}

private data class TourStep(val anchor: String?, val text: Int)

private val TOUR = listOf(
    TourStep(KittyAnchors.LOGO, R.string.tour_logo),
    TourStep(KittyAnchors.CHANNEL, R.string.tour_channel),
    TourStep(KittyAnchors.GLOBE, R.string.tour_globe),
    TourStep(KittyAnchors.ROOMS, R.string.tour_rooms),
    TourStep(KittyAnchors.PEOPLE, R.string.tour_people),
    TourStep(null, R.string.tour_badges)
)

const val TOUR_TIP = "tour"

/** First-launch tour: kitty points at each header button in turn. Place it over the whole screen. */
@Composable
fun KittyTour() {
    val seen by KittyGame.seenTips.collectAsState()
    if (TOUR_TIP in seen) return
    val steps = TOUR.filter { it.anchor == null || KittyAnchors.rects.containsKey(it.anchor) }
    var index by remember { mutableIntStateOf(0) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val step = steps.getOrNull(index) ?: run { KittyGame.markTipSeen(TOUR_TIP); return }
    val target = step.anchor?.let { KittyAnchors.rects[it] }
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { index++ }
    ) {
        if (target != null) {
            val r = target.translate(-origin)
            val pad = with(density) { 6.dp.toPx() }
            Box(
                Modifier
                    .offset { IntOffset((r.left - pad).toInt(), (r.top - pad).toInt()) }
                    .size(with(density) { (r.width + 2 * pad).toDp() }, with(density) { (r.height + 2 * pad).toDp() })
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
            val fingerSize = with(density) { 36.dp.toPx() }
            PointingFinger(
                up = true,
                modifier = Modifier.offset { IntOffset((r.center.x - fingerSize / 2).toInt(), (r.bottom + pad).toInt()) }
            )
        }
        val top = target?.let { with(density) { (it.translate(-origin).bottom).toDp() } + 56.dp } ?: 0.dp
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (target == null) Modifier.align(Alignment.Center) else Modifier.padding(top = top))
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            KittySays(stringResource(step.text), kittyHeight = 56.dp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { KittyGame.markTipSeen(TOUR_TIP) }) {
                    Text(stringResource(R.string.tour_skip), color = Color.White, fontFamily = BitchatFontFamily)
                }
                Button(onClick = { index++ }) {
                    Text(
                        stringResource(if (index == steps.lastIndex) R.string.tour_done else R.string.tour_next),
                        fontFamily = BitchatFontFamily
                    )
                }
            }
            Text(
                "${index + 1} / ${steps.size}", color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

// MARK: - Badge unlocked popup

@Composable
fun KittyBadgePopup() {
    var shown by remember { mutableStateOf<Badge?>(null) }
    LaunchedEffect(Unit) {
        KittyGame.unlocked.collect { badge ->
            shown = badge
            delay(5000)
            if (shown == badge) shown = null
        }
    }
    Box(Modifier.fillMaxSize().padding(top = 72.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible = shown != null, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            val badge = shown ?: return@AnimatedVisibility
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(8.dp),
                modifier = Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChatbitLogo(height = 48.dp, outfit = badge.outfit)
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(stringResource(R.string.badge_unlocked), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontFamily = BitchatFontFamily)
                        Text("${badge.emoji} ${stringResource(badge.title)}", fontWeight = FontWeight.Bold, fontFamily = BitchatFontFamily)
                        Text(stringResource(R.string.outfit_unlocked, stringResource(badge.outfit.title)), fontSize = 12.sp, fontFamily = BitchatFontFamily)
                    }
                    TextButton(onClick = { KittyGame.wear(badge.outfit); shown = null }) {
                        Text(stringResource(R.string.outfit_wear), fontFamily = BitchatFontFamily)
                    }
                }
            }
        }
    }
}

// MARK: - Collection sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KittyCollectionSheet(onDismiss: () -> Unit) {
    val badges by KittyGame.badges.collectAsState()
    val outfit by KittyGame.outfit.collectAsState()
    val peers by KittyGame.peersMet.collectAsState()
    val shares by KittyGame.shares.collectAsState()
    val places by KittyGame.places.collectAsState()
    val caches by KittyGame.caches.collectAsState()
    val score = peers + 3 * shares
    val rank = KittyRank.of(score)
    val next = KittyRank.next(score)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ChatbitLogo(height = 84.dp, dressed = true)
            Text(stringResource(rank.title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontFamily = BitchatFontFamily)
            if (next != null) {
                LinearProgressIndicator(
                    progress = { (score - rank.minScore).toFloat() / (next.minScore - rank.minScore) },
                    modifier = Modifier.fillMaxWidth(0.7f)
                )
                Text(stringResource(R.string.rank_next, next.minScore - score, stringResource(next.title)), fontSize = 12.sp, fontFamily = BitchatFontFamily, textAlign = TextAlign.Center)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("📡", peers, R.string.stat_peers)
                Stat("📲", shares, R.string.stat_shares)
                Stat("🌍", places, R.string.stat_places)
                Stat("🗺️", caches, R.string.stat_caches)
            }
            Text(stringResource(R.string.kitty_badges), fontWeight = FontWeight.Bold, fontFamily = BitchatFontFamily, modifier = Modifier.fillMaxWidth())
            Badge.entries.forEach { b ->
                val owned = b in badges
                Row(
                    Modifier.fillMaxWidth().alpha(if (owned) 1f else 0.45f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(if (owned) b.emoji else "🔒", fontSize = 24.sp)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(b.title), fontWeight = FontWeight.Bold, fontFamily = BitchatFontFamily, fontSize = 14.sp)
                        Text(stringResource(b.desc), fontSize = 12.sp, fontFamily = BitchatFontFamily)
                    }
                    ChatbitLogo(height = 30.dp, outfit = b.outfit)
                }
            }
            Text(stringResource(R.string.kitty_outfits), fontWeight = FontWeight.Bold, fontFamily = BitchatFontFamily, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutfitChip(null, outfit == null, true)
                KittyOutfit.entries.forEach { o -> OutfitChip(o, outfit == o, KittyGame.hasOutfit(o)) }
            }
            Text(stringResource(R.string.kitty_private_note), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = BitchatFontFamily, textAlign = TextAlign.Center)
            TextButton(onClick = { KittyGame.replayTips(); onDismiss() }) {
                Text(stringResource(R.string.kitty_replay_tour), fontFamily = BitchatFontFamily)
            }
        }
    }
}

@Composable
private fun Stat(emoji: String, value: Int, label: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$emoji $value", fontWeight = FontWeight.Bold, fontFamily = BitchatFontFamily)
        Text(stringResource(label), fontSize = 11.sp, fontFamily = BitchatFontFamily)
    }
}

@Composable
private fun OutfitChip(outfit: KittyOutfit?, selected: Boolean, owned: Boolean) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(enabled = owned) { KittyGame.wear(outfit) }
            .alpha(if (owned) 1f else 0.35f)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ChatbitLogo(height = 34.dp, outfit = outfit)
        Text(
            if (outfit == null) stringResource(R.string.outfit_none) else stringResource(outfit.title),
            fontSize = 10.sp, fontFamily = BitchatFontFamily
        )
    }
}

// MARK: - Animated kitty reactions

val KITTY_REACTIONS = listOf("😻", "😹", "😺", "😽", "🙀", "😾")

private val REACTION = Regex("^([😻😹😺😽🙀😾😸😼😿]{1,3})(?:\\s+@(\\S+))?$")

/** The cat emojis and the optional @target when [content] is a kitty reaction, else null. */
fun parseKittyReaction(content: String): Pair<String, String?>? =
    REACTION.matchEntire(content.trim())?.let { it.groupValues[1] to it.groupValues[2].ifEmpty { null } }

/** Big animated cat: hearts beat, laughs shake, angry ones scratch, surprised ones jump. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KittyReaction(sender: String, emojis: String, target: String?, onLongPress: (() -> Unit)?) {
    val t = rememberInfiniteTransition(label = "reaction").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "r"
    )
    val first = emojis.codePoints().findFirst().orElse(0)
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("<@$sender>", fontFamily = BitchatFontFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        Text(
            emojis, fontSize = 34.sp,
            modifier = Modifier.graphicsLayer {
                val v = t.value
                val wave = kotlin.math.sin(v * 2 * Math.PI).toFloat()
                when (first) {
                    0x1F63B, 0x1F63D -> { val s = 1f + 0.15f * kotlin.math.abs(wave); scaleX = s; scaleY = s } // love, kiss: heartbeat
                    0x1F639, 0x1F638 -> rotationZ = 10f * kotlin.math.sin(v * 8 * Math.PI).toFloat()            // laugh: shake
                    0x1F63E, 0x1F63C -> { rotationZ = -15f * wave; translationX = 6f * wave }                       // angry: scratch
                    0x1F640 -> translationY = -18f * kotlin.math.abs(wave)                                          // surprised: jump
                    else -> translationX = 2f * kotlin.math.sin(v * 20 * Math.PI).toFloat()                         // purr
                }
            }
        )
        if (target != null) Text("→ @$target", fontFamily = BitchatFontFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
