package com.bitchat.android.geohash

import android.content.Context
import android.location.Address
import org.json.JSONObject
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Reverse geocoding without any network request: the nearest city of the world map's bundled
 * list (assets/world_cities.geojson). Coarse, but the position never leaves the phone.
 */
class OfflineGeocoderProvider(private val context: Context) : GeocoderProvider {

    private class City(val name: String, val lat: Double, val lon: Double)

    private val cities: List<City> by lazy {
        runCatching {
            val json = JSONObject(context.assets.open("world_cities.geojson").bufferedReader().use { it.readText() })
            val arr = json.getJSONArray("cities")
            List(arr.length()) { i ->
                val c = arr.getJSONObject(i)
                City(c.getString("n"), c.getDouble("lat"), c.getDouble("lon"))
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun getFromLocation(
        latitude: Double,
        longitude: Double,
        maxResults: Int,
        liveLocationToken: Long?
    ): List<Address> {
        val k = cos(Math.toRadians(latitude))
        val nearest = cities.minByOrNull { c ->
            val dLat = c.lat - latitude
            var dLon = c.lon - longitude
            if (dLon > 180) dLon -= 360 else if (dLon < -180) dLon += 360
            dLat * dLat + (dLon * k) * (dLon * k)
        } ?: return emptyList()
        var dLon = nearest.lon - longitude
        if (dLon > 180) dLon -= 360 else if (dLon < -180) dLon += 360
        val km = 111.0 * sqrt((nearest.lat - latitude).let { it * it } + (dLon * k).let { it * it })
        if (km > MAX_KM) return emptyList()
        return listOf(Address(Locale.getDefault()).apply {
            locality = nearest.name
            subAdminArea = nearest.name
            adminArea = nearest.name
            this.latitude = nearest.lat
            this.longitude = nearest.lon
        })
    }

    private companion object {
        const val MAX_KM = 150.0
    }
}
