package com.bitchat.android.geohash

import android.content.Context
import android.util.Log
import com.bitchat.android.net.OkHttpProvider
import com.bitchat.android.server.ChatbitServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Directory of named places (continent > country > city > district) with their geohash, so a
 * zone can be found by name: zoomed in on the map, place names are not visible.
 * Data: zones.tsv from GeoNames (CC BY 4.0), served by the chatbit server to genuine apps (see
 * [ChatbitServer]) and downloaded once through Tor; searches then stay on the phone.
 */
object ZoneDirectory {

    /** GeoNames continent codes, in the order they are listed. */
    val CONTINENTS = listOf("EU", "AF", "NA", "SA", "AS", "OC", "AN")

    class Place(
        val name: String,
        val country: String,
        /** precision 7; shorter codes are prefixes of it */
        val geohash: String,
        val population: Int,
        val parent: Place?
    ) {
        internal val key = normalize(name)
        val districts = ArrayList<Place>(0)
    }

    /** A geohash tile (1 to 4 characters) holding inhabited places of a country, with its largest places. */
    class Tile(val code: String, val places: List<String>)

    class Data(
        val continentOf: Map<String, String>,
        /** cities (not districts) of each country, largest first */
        val citiesByCountry: Map<String, List<Place>>,
        /** tiles of each country, by code */
        private val tiles: Map<String, Map<String, Tile>>,
        private val all: List<Place>
    ) {
        fun countries(continent: String) = citiesByCountry.keys.filter { continentOf[it] == continent }

        fun tile(country: String, code: String): Tile? = tiles[country]?.get(code)

        /** Tiles one character longer than [parent] ("" gives the 1-character tiles) inside [country]. */
        fun subTiles(country: String, parent: String): List<Tile> =
            tiles[country].orEmpty().values.filter { it.code.length == parent.length + 1 && it.code.startsWith(parent) }
                .sortedBy { it.code }

        fun citiesIn(country: String, tile: String): List<Place> =
            citiesByCountry[country].orEmpty().filter { it.geohash.startsWith(tile) }

        /** Countries that have inhabited places in [code], for a code typed in the search field. */
        fun countriesWithTile(code: String): List<String> {
            val c = code.take(4)
            return tiles.filterValues { it.containsKey(c) }.keys.sorted()
        }

        /** Places whose name starts with [query] come first, then those containing it; larger first. */
        fun search(query: String, limit: Int = 80): List<Place> {
            val q = normalize(query.trim())
            if (q.length < 2) return emptyList()
            val starts = ArrayList<Place>()
            val contains = ArrayList<Place>()
            for (p in all) {
                when {
                    p.key.startsWith(q) -> starts += p
                    p.key.contains(q) -> contains += p
                }
            }
            return (starts.sortedByDescending { it.population } + contains.sortedByDescending { it.population }).take(limit)
        }
    }

    @Volatile private var cached: Data? = null

    private const val FILE = "zones.tsv"

    /** The directory, downloaded first if needed; null when the server cannot give it. */
    suspend fun obtain(context: Context): Data? = withContext(Dispatchers.IO) {
        cached ?: runCatching {
            val file = File(context.filesDir, FILE)
            if (!file.exists()) download(context, file)
            load(file)
        }.onFailure { Log.w("ZoneDirectory", "unavailable: ${it.message}") }.getOrNull()
    }

    private suspend fun download(context: Context, file: File) {
        val pass = ChatbitServer.pass(context) ?: error("no pass")
        val request = Request.Builder().url("${ChatbitServer.BASE_URL}/v1/zones")
            .header("Authorization", "Pass ${pass.token}").build()
        val part = File(file.path + ".part")
        OkHttpProvider.httpClient().newBuilder().readTimeout(120, TimeUnit.SECONDS).callTimeout(300, TimeUnit.SECONDS)
            .build().newCall(request).execute().use { response ->
                check(response.isSuccessful) { "zones ${response.code}" }
                GZIPInputStream(response.body!!.byteStream()).use { input -> part.outputStream().use(input::copyTo) }
            }
        check(part.renameTo(file)) { "cannot store the directory" }
    }

    private fun load(file: File): Data {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val continentOf = HashMap<String, String>()
            val all = ArrayList<Place>(42000)
            val tiles = HashMap<String, HashMap<String, Tile>>()
            var tileCountry: HashMap<String, Tile>? = null
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val f = line.split('\t')
                    when (f[0]) {
                        "C" -> continentOf[f[1]] = f[2]
                        "P" -> {
                            val parent = f[5].toInt().takeIf { it >= 0 }?.let { all[it] }
                            val p = Place(f[1], f[2], f[3], f[4].toInt(), parent)
                            parent?.districts?.add(p)
                            all += p
                        }
                        "K" -> tileCountry = HashMap<String, Tile>().also { tiles[f[1]] = it }
                        "T" -> tileCountry?.put(f[1], Tile(f[1], f[2].split('|')))
                    }
                }
            }
            val byCountry = all.filter { it.parent == null }.groupBy { it.country }
            return Data(continentOf, byCountry, tiles, all).also { cached = it }
        }
    }

    const val GEOHASH_ALPHABET = "0123456789bcdefghjkmnpqrstuvwxyz"

    /** A typed zone code such as "#u09" or "u09t", or null. */
    fun asCode(query: String): String? =
        query.trim().removePrefix("#").lowercase().takeIf { q -> q.length in 1..7 && q.all { it in GEOHASH_ALPHABET } }

    private val MARKS = Regex("\\p{Mn}+")
    internal fun normalize(s: String) = MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")
}
