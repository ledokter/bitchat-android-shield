package com.bitchat.android.ui

import android.content.Context
import com.bitchat.android.geohash.GeohashChannel
import com.bitchat.android.geohash.GeohashChannelLevel
import com.bitchat.android.geohash.LocationChannelManager
import com.bitchat.android.model.BitchatMessage

internal fun BitchatMessage.isFromSelf(
    currentUserNickname: String,
    myPeerId: String,
): Boolean =
    senderPeerID == myPeerId ||
        sender == currentUserNickname ||
        sender.startsWith("$currentUserNickname#")

internal fun normalizeMessageUrl(rawUrl: String): String =
    if (
        rawUrl.startsWith("http://", ignoreCase = true) ||
        rawUrl.startsWith("https://", ignoreCase = true)
    ) {
        rawUrl
    } else {
        "https://$rawUrl"
    }

// chatbit: links open in Tor Browser only (see TorLinks)
internal fun openMessageUrl(context: Context, rawUrl: String): Boolean =
    com.bitchat.android.net.TorLinks.open(context, normalizeMessageUrl(rawUrl))

internal fun channelForGeohash(geohash: String): GeohashChannel {
    val level = when (geohash.length) {
        in 0..2 -> GeohashChannelLevel.REGION
        in 3..4 -> GeohashChannelLevel.PROVINCE
        5 -> GeohashChannelLevel.CITY
        6 -> GeohashChannelLevel.NEIGHBORHOOD
        else -> GeohashChannelLevel.BLOCK
    }
    return GeohashChannel(level, geohash.lowercase())
}

internal fun navigateToGeohash(context: Context, geohash: String): Boolean =
    runCatching {
        val locationManager = LocationChannelManager.getInstance(context)
        locationManager.selectManual(channelForGeohash(geohash))
    }.isSuccess
