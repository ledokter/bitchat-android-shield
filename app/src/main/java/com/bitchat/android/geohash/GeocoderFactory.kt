package com.bitchat.android.geohash

import android.content.Context

object GeocoderFactory {
    /** chatbit: place names come from the city list bundled in the app; no online geocoder. */
    fun get(context: Context): GeocoderProvider = OfflineGeocoderProvider(context)
}
