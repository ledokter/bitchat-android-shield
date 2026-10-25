package com.bitchat.android.ui.globe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.bitchat.android.net.OkHttpProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan

/** A point of interest on the globe (live activity cell, geocache…); [weight] in 0..1 sets its size. */
data class GlobeMarker(
    val lat: Double,
    val lon: Double,
    val weight: Float,
    val color: Color,
    val geohash: String,
    val label: String = ""
)

internal fun DrawScope.drawMarkers(
    markers: List<GlobeMarker>,
    cx: Float, cy: Float, r: Float, cLat: Double, cLon: Double, density: Float
) {
    for (m in markers) {
        val p = GlobeMath.project(m.lat, m.lon, cLat, cLon) ?: continue
        val center = Offset(cx + p.x * r, cy + p.y * r)
        val radius = (3f + 9f * m.weight.coerceIn(0f, 1f)) * density
        drawCircle(m.color.copy(alpha = 0.25f), radius * 2.2f, center)
        drawCircle(m.color.copy(alpha = 0.9f), radius, center)
    }
}

/** Marker nearest to a screen point, within [maxDistPx]. */
internal fun nearestMarker(
    markers: List<GlobeMarker>, x: Float, y: Float,
    cx: Float, cy: Float, r: Float, cLat: Double, cLon: Double, maxDistPx: Float
): GlobeMarker? {
    var best: GlobeMarker? = null
    var bestD = maxDistPx
    for (m in markers) {
        val p = GlobeMath.project(m.lat, m.lon, cLat, cLon) ?: continue
        val d = Offset(cx + p.x * r - x, cy + p.y * r - y).getDistance()
        if (d < bestD) { bestD = d; best = m }
    }
    return best
}

/**
 * Night side of the Earth, from the current subsolar point, with a soft twilight band.
 * Sampled on a coarse screen grid: cheap and smooth enough for a phone.
 */
internal fun DrawScope.drawNightShading(
    cx: Float, cy: Float, r: Float, cLat: Double, cLon: Double, nowMs: Long = System.currentTimeMillis()
) {
    val (sunLat, sunLon) = subsolarPoint(nowMs)
    val sLat = Math.toRadians(sunLat)
    val n = 44
    val step = 2f * r / n
    for (i in 0 until n) {
        for (j in 0 until n) {
            val sx = -1.0 + (i + 0.5) * 2.0 / n
            val sy = -1.0 + (j + 0.5) * 2.0 / n
            if (sx * sx + sy * sy > 1.0) continue
            val ll = GlobeMath.unproject(sx, sy, cLat, cLon) ?: continue
            val lat = Math.toRadians(ll.first)
            val dLon = Math.toRadians(ll.second - sunLon)
            val sinElev = sin(lat) * sin(sLat) + cos(lat) * cos(sLat) * cos(dLon)
            // full night below -0.1, day above +0.05
            val night = ((0.05 - sinElev) / 0.15).coerceIn(0.0, 1.0).toFloat()
            if (night <= 0f) continue
            drawRect(
                Color(0xFF020818).copy(alpha = 0.55f * night),
                topLeft = Offset(cx - r + i * step, cy - r + j * step),
                size = Size(step + 0.6f, step + 0.6f)
            )
        }
    }
}

/** Approximate subsolar latitude/longitude (good to about a degree, plenty for shading). */
internal fun subsolarPoint(nowMs: Long): Pair<Double, Double> {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = nowMs }
    val day = cal.get(Calendar.DAY_OF_YEAR)
    val hours = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0
    val decl = 23.44 * sin(2 * PI * (284 + day) / 365.0)
    val lon = GlobeMath.normalizeLon((12.0 - hours) * 15.0)
    return decl to lon
}

/**
 * Optional street layer: OpenStreetMap raster tiles fetched through the Tor-routed HTTP client,
 * drawn once the globe is zoomed in. Off by default; only the viewed area is requested.
 */
class GlobeTileLayer(private val userAgent: String) {
    /** Bumped when a tile arrives so the globe redraws. */
    val version = mutableIntStateOf(0)
    private val cache = LruCache<String, Bitmap>(48)
    private val pending = HashSet<String>()
    private val failed = HashSet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permits = Semaphore(3)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun close() {
        cache.evictAll()
    }

    private fun request(z: Int, x: Int, y: Int) {
        val key = "$z/$x/$y"
        synchronized(pending) { if (key in pending || key in failed) return; pending.add(key) }
        scope.launch {
            permits.withPermit {
                val bmp = runCatching {
                    val req = Request.Builder()
                        .url("https://tile.openstreetmap.org/$key.png")
                        .header("User-Agent", userAgent)
                        .build()
                    OkHttpProvider.httpClient().newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) null else resp.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    }
                }.getOrNull()
                synchronized(pending) {
                    pending.remove(key)
                    if (bmp == null) failed.add(key)
                }
                if (bmp != null) {
                    cache.put(key, bmp)
                    version.intValue++
                }
            }
        }
    }

    internal fun draw(scope: DrawScope, cx: Float, cy: Float, r: Float, cLat: Double, cLon: Double) {
        version.intValue // read: redraw when tiles arrive
        // tile zoom so that one tile is about 256 px on screen
        val z = floor(ln(2 * PI * r / 256.0) / ln(2.0)).toInt().coerceIn(0, 18)
        if (z < 5) return
        val n = 1 shl z
        val cxT = ((cLon + 180.0) / 360.0 * n).toInt()
        val cyT = ((1.0 - asinh(tan(Math.toRadians(cLat))) / PI) / 2.0 * n).toInt()
        val span = (max(scope.size.width, scope.size.height) / 256f / 2f).toInt() + 2
        scope.drawIntoCanvas { canvas ->
            for (dx in -span..span) for (dy in -span..span) {
                val tx = Math.floorMod(cxT + dx, n)
                val ty = cyT + dy
                if (ty < 0 || ty >= n) continue
                val corners = listOf(tx to ty, tx + 1 to ty, tx + 1 to ty + 1, tx to ty + 1).map { (x, y) ->
                    GlobeMath.project(tileLat(y, n), tileLon(x, n), cLat, cLon)
                }
                if (corners.any { it == null }) continue
                val bmp = cache.get("$z/$tx/$ty")
                if (bmp == null) { request(z, tx, ty); continue }
                val dst = FloatArray(8)
                corners.forEachIndexed { i, p -> dst[2 * i] = cx + p!!.x * r; dst[2 * i + 1] = cy + p.y * r }
                val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
                val m = Matrix()
                m.setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, dst, 0, 4)
                canvas.nativeCanvas.drawBitmap(bmp, m, paint)
            }
        }
    }

    private fun tileLon(x: Int, n: Int) = x.toDouble() / n * 360.0 - 180.0
    private fun tileLat(y: Int, n: Int) = Math.toDegrees(atan(sinh(PI * (1 - 2.0 * y / n))))
}
