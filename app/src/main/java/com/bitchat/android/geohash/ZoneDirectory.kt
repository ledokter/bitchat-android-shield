package com.bitchat.android.geohash

import android.content.Context
import java.text.Normalizer

/**
 * Offline directory of named places (continent > country > city > district) with their geohash,
 * so a zone can be found by name: zoomed in on the map, place names are not visible.
 * Data: assets/zones.tsv, built by tools/build_zone_directory.py from GeoNames (CC BY 4.0).
 * Nothing is looked up online.
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

    class Data(
        val continentOf: Map<String, String>,
        /** cities (not districts) of each country, largest first */
        val citiesByCountry: Map<String, List<Place>>,
        private val all: List<Place>
    ) {
        fun countries(continent: String) = citiesByCountry.keys.filter { continentOf[it] == continent }

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

    fun load(context: Context): Data {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val continentOf = HashMap<String, String>()
            val all = ArrayList<Place>(42000)
            context.assets.open("zones.tsv").bufferedReader().useLines { lines ->
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
                    }
                }
            }
            val byCountry = all.filter { it.parent == null }.groupBy { it.country }
            return Data(continentOf, byCountry, all).also { cached = it }
        }
    }

    private val MARKS = Regex("\\p{Mn}+")
    internal fun normalize(s: String) = MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")
}
