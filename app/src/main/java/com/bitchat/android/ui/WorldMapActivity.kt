package com.bitchat.android.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.bitchat.android.R
import com.bitchat.android.geohash.Geohash
import com.bitchat.android.nostr.NostrEvent
import com.bitchat.android.nostr.NostrFilter
import com.bitchat.android.nostr.NostrKind
import com.bitchat.android.nostr.NostrRelayManager
import com.bitchat.android.ui.globe.GlobeColors
import com.bitchat.android.ui.globe.GlobeMarker
import com.bitchat.android.ui.globe.GlobeState
import com.bitchat.android.ui.globe.GlobeTileLayer
import com.bitchat.android.ui.globe.GlobeView
import com.bitchat.android.ui.globe.LandData
import com.bitchat.android.ui.theme.BitchatFontFamily
import com.bitchat.android.ui.theme.BitchatTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.math.max

/**
 * World map in the glub.chat style: a spinning globe with day/night shading, live channel activity
 * and geocaches, optional street tiles through Tor. Tapping a spot offers to join its geohash
 * channel (the result is returned like [GeohashPickerActivity]).
 *
 * Relay data is only requested while this screen is open: the last 15 minutes of public channel
 * events on the relays chabit is already connected to, and recent geocaches.
 */
class WorldMapActivity : ComponentActivity() {

    private enum class Layer { ACTIVITY, GEOCACHES }

    private val activity = MutableStateFlow<Map<String, Int>>(emptyMap())   // geohash prefix -> events
    private val geocaches = MutableStateFlow<Map<String, String>>(emptyMap()) // geohash -> name
    private val seen = HashSet<String>()
    private val subscriptions = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (com.bitchat.android.security.SecurityPreferences.blockScreenshots.value) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        subscribe()

        setContent {
            BitchatTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val colorScheme = MaterialTheme.colorScheme
                val dark = colorScheme.background.luminance() < 0.5f
                val globeState = remember { GlobeState(20.0, 10.0, 2, startZoomedOut = true) }
                LaunchedEffect(globeState) { globeState.attach(scope) }

                val land by produceState<List<LandData.Ring>?>(null) { value = withContext(Dispatchers.IO) { LandData.load(context) } }
                val borders by produceState(emptyList<LandData.Ring>()) { value = withContext(Dispatchers.IO) { LandData.loadBorders(context) } }
                val cities by produceState(emptyList<LandData.City>()) { value = withContext(Dispatchers.IO) { LandData.loadCities(context) } }
                val labelTypeface = remember { ResourcesCompat.getFont(context, R.font.geist_mono_medium) }
                val labelTypefaceBold = remember { ResourcesCompat.getFont(context, R.font.geist_mono_semibold) }

                var layer by remember { mutableStateOf(Layer.ACTIVITY) }
                var night by remember { mutableStateOf(true) }
                var spin by remember { mutableStateOf(true) }
                var streets by remember { mutableStateOf(false) }
                var selected by remember { mutableStateOf<GlobeMarker?>(null) }
                val tiles = remember { GlobeTileLayer("chabit/2.1 (offline-first messenger; https://github.com/ledokter/bitchat-android-shield)") }
                DisposableEffect(Unit) { onDispose { tiles.close() } }

                val counts by activity.collectAsState()
                val caches by geocaches.collectAsState()
                val accent = colorScheme.primary
                val markers = remember(layer, counts, caches, accent) {
                    when (layer) {
                        Layer.ACTIVITY -> {
                            val maxC = max(1, counts.values.maxOrNull() ?: 1)
                            counts.map { (gh, c) ->
                                val (lat, lon) = Geohash.decodeToCenter(gh)
                                GlobeMarker(lat, lon, (ln(1.0 + c) / ln(1.0 + maxC)).toFloat(), accent, gh, c.toString())
                            }
                        }
                        Layer.GEOCACHES -> caches.map { (gh, name) ->
                            val (lat, lon) = Geohash.decodeToCenter(gh)
                            GlobeMarker(lat, lon, 0.15f, Color(0xFFFFB300), gh, name)
                        }
                    }
                }

                // slow spin while nobody touches the globe
                LaunchedEffect(spin) {
                    while (spin) {
                        withFrameNanos { }
                        if (!globeState.isInteracting && !globeState.isAnimating) globeState.rotateBy(-0.7f, 0f)
                    }
                }

                val globeColors = remember(colorScheme, dark) {
                    if (dark) GlobeColors(
                        accent = colorScheme.primary, land = Color(0xFF1A2420),
                        coastline = colorScheme.primary.copy(alpha = 0.45f), border = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        oceanCenter = Color(0xFF0A1014), oceanEdge = Color(0xFF020406), atmosphere = colorScheme.primary,
                        graticule = colorScheme.onSurface.copy(alpha = 0.055f), grid = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        label = colorScheme.onSurfaceVariant, labelHalo = colorScheme.background, star = colorScheme.onSurface
                    ) else GlobeColors(
                        accent = colorScheme.primary, land = Color(0xFFD9E4DC),
                        coastline = colorScheme.primary.copy(alpha = 0.5f), border = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        oceanCenter = Color(0xFFEFF4F8), oceanEdge = Color(0xFFD6E0E8), atmosphere = colorScheme.primary,
                        graticule = colorScheme.onSurface.copy(alpha = 0.08f), grid = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        label = colorScheme.onSurfaceVariant, labelHalo = colorScheme.background, star = colorScheme.onSurfaceVariant
                    )
                }

                Box(Modifier.fillMaxSize().background(colorScheme.background)) {
                    land?.let { rings ->
                        GlobeView(
                            state = globeState, colors = globeColors, land = rings, borders = borders, cities = cities,
                            labelTypeface = labelTypeface, labelTypefaceBold = labelTypefaceBold,
                            modifier = Modifier.fillMaxSize(),
                            markers = markers,
                            nightShading = night,
                            tileLayer = if (streets) tiles else null,
                            onTap = { lat, lon, marker ->
                                spin = false
                                selected = marker ?: GlobeMarker(lat, lon, 0f, accent,
                                    Geohash.encode(lat, lon, globeState.precision.coerceIn(2, 7)))
                            }
                        )
                    }

                    // top bar: back, title, layers and toggles
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.rooms_back))
                            }
                            Text(stringResource(R.string.map_title), fontFamily = BitchatFontFamily, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip(stringResource(R.string.map_layer_activity), layer == Layer.ACTIVITY) { layer = Layer.ACTIVITY; selected = null }
                            Chip(stringResource(R.string.map_layer_geocaches), layer == Layer.GEOCACHES) { layer = Layer.GEOCACHES; selected = null }
                            Chip(stringResource(R.string.map_daynight), night) { night = !night }
                            Chip(stringResource(R.string.map_spin), spin) { spin = !spin }
                            Chip(stringResource(R.string.map_streets), streets) { streets = !streets }
                        }
                    }

                    // selected cell
                    selected?.let { m ->
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.surface,
                            tonalElevation = 4.dp
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("#${m.geohash}", fontFamily = BitchatFontFamily, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colorScheme.primary)
                                val detail = when {
                                    layer == Layer.GEOCACHES && m.label.isNotEmpty() -> stringResource(R.string.map_geocache, m.label)
                                    m.label.isNotEmpty() -> stringResource(R.string.map_cell_activity, m.label.toIntOrNull() ?: 0)
                                    else -> stringResource(R.string.map_cell_quiet)
                                }
                                Text(detail, fontFamily = BitchatFontFamily, fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                                Button(onClick = {
                                    setResult(Activity.RESULT_OK, Intent().putExtra(GeohashPickerActivity.EXTRA_RESULT_GEOHASH, m.geohash))
                                    finish()
                                }) { Text(stringResource(R.string.map_join, m.geohash), fontFamily = BitchatFontFamily) }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
        FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontFamily = BitchatFontFamily, fontSize = 12.sp) })
    }

    private fun subscribe() {
        val manager = NostrRelayManager.getInstance(applicationContext)
        val now = (System.currentTimeMillis() / 1000).toInt()
        subscriptions += manager.subscribe(
            filter = NostrFilter(kinds = listOf(NostrKind.EPHEMERAL_EVENT, NostrKind.GEOHASH_PRESENCE), since = now - 900, limit = 2000),
            handler = ::onActivity
        )
        subscriptions += manager.subscribe(
            filter = NostrFilter(kinds = listOf(GEOCACHE_KIND), limit = 400),
            handler = ::onGeocache
        )
    }

    private fun geohashOf(e: NostrEvent) =
        e.tags.firstOrNull { it.size >= 2 && it[0] == "g" }?.get(1)?.lowercase()
            ?.takeIf { gh -> gh.isNotEmpty() && gh.all { it in GEOHASH_ALPHABET } }

    private fun onActivity(e: NostrEvent) {
        if (!synchronized(seen) { seen.add(e.id) }) return
        val gh = geohashOf(e)?.take(4) ?: return
        val map = activity.value.toMutableMap()
        map[gh] = (map[gh] ?: 0) + 1
        activity.value = map
    }

    private fun onGeocache(e: NostrEvent) {
        if (!synchronized(seen) { seen.add(e.id) }) return
        val gh = geohashOf(e) ?: return
        val name = e.tags.firstOrNull { it.size >= 2 && (it[0] == "name" || it[0] == "title") }?.get(1) ?: "?"
        geocaches.value = geocaches.value + (gh to name.take(60))
    }

    override fun onDestroy() {
        val manager = NostrRelayManager.getInstance(applicationContext)
        subscriptions.forEach { runCatching { manager.unsubscribe(it) } }
        super.onDestroy()
    }

    companion object {
        /** NIP-CC geocache listing. */
        private const val GEOCACHE_KIND = 37516
        private const val GEOHASH_ALPHABET = "0123456789bcdefghjkmnpqrstuvwxyz"
    }
}
