package com.bitchat.android.game

import android.content.Context
import android.content.SharedPreferences
import com.bitchat.android.R
import com.bitchat.android.security.SecurePrefs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

/** Outfits kitty can wear in the header; each one is the reward of a [Badge]. */
enum class KittyOutfit(val title: Int) {
    SCARF(R.string.outfit_scarf),
    BERET(R.string.outfit_beret),
    CROWN(R.string.outfit_crown),
    EXPLORER(R.string.outfit_explorer),
    SUNGLASSES(R.string.outfit_sunglasses),
    TOR_HOOD(R.string.outfit_tor_hood),
    PIRATE(R.string.outfit_pirate)
}

enum class Badge(val emoji: String, val title: Int, val desc: Int, val outfit: KittyOutfit) {
    FIRST_MESH("📡", R.string.badge_first_mesh, R.string.badge_first_mesh_desc, KittyOutfit.SCARF),
    FIRST_ROOM("🔒", R.string.badge_first_room, R.string.badge_first_room_desc, KittyOutfit.BERET),
    AMBASSADOR("📲", R.string.badge_ambassador, R.string.badge_ambassador_desc, KittyOutfit.CROWN),
    GLOBETROTTER("🌍", R.string.badge_globetrotter, R.string.badge_globetrotter_desc, KittyOutfit.EXPLORER),
    SECRET_AGENT("🕶️", R.string.badge_secret_agent, R.string.badge_secret_agent_desc, KittyOutfit.SUNGLASSES),
    TOR_GHOST("🧅", R.string.badge_tor_ghost, R.string.badge_tor_ghost_desc, KittyOutfit.TOR_HOOD),
    GEOCACHER("🗺️", R.string.badge_geocacher, R.string.badge_geocacher_desc, KittyOutfit.PIRATE)
}

/** Network rank from the score (phones met on the mesh + 3 per app shared). */
enum class KittyRank(val minScore: Int, val title: Int) {
    KITTEN(0, R.string.rank_kitten),
    ALLEY_CAT(5, R.string.rank_alley_cat),
    ROOF_CAT(20, R.string.rank_roof_cat),
    LEGEND(50, R.string.rank_legend);

    companion object {
        fun of(score: Int) = entries.last { score >= it.minScore }
        fun next(score: Int) = entries.firstOrNull { it.minScore > score }
    }
}

/**
 * chatbit's little game: badges, network counters, outfits and the kitty guide, all kept on the
 * phone only (encrypted prefs, wiped by panic). Nothing is ever sent. People met and places
 * visited are stored as short salted hashes, never as identifiers.
 */
object KittyGame {
    private const val PREFS = "chatbit_game"
    const val GLOBETROTTER_GOAL = 10
    private const val MAX_SEEN = 2000

    private var prefs: SharedPreferences? = null

    private val _badges = MutableStateFlow<Set<Badge>>(emptySet())
    val badges: StateFlow<Set<Badge>> = _badges.asStateFlow()

    private val _outfit = MutableStateFlow<KittyOutfit?>(null)
    val outfit: StateFlow<KittyOutfit?> = _outfit.asStateFlow()

    private val _peersMet = MutableStateFlow(0)
    val peersMet: StateFlow<Int> = _peersMet.asStateFlow()

    private val _shares = MutableStateFlow(0)
    val shares: StateFlow<Int> = _shares.asStateFlow()

    private val _places = MutableStateFlow(0)
    val places: StateFlow<Int> = _places.asStateFlow()

    private val _caches = MutableStateFlow(0)
    val caches: StateFlow<Int> = _caches.asStateFlow()

    private val _unlocked = MutableSharedFlow<Badge>(extraBufferCapacity = 8)
    /** Emits each badge the moment it is earned (for the celebration popup). */
    val unlocked: SharedFlow<Badge> = _unlocked.asSharedFlow()

    private val _seenTips = MutableStateFlow<Set<String>>(emptySet())
    val seenTips: StateFlow<Set<String>> = _seenTips.asStateFlow()

    fun init(context: Context) {
        val p = SecurePrefs.open(context, PREFS)
        prefs = p
        load(p)
    }

    private fun load(p: SharedPreferences) {
        _badges.value = p.getStringSet("badges", emptySet())!!.mapNotNull { n -> Badge.entries.find { it.name == n } }.toSet()
        _outfit.value = p.getString("outfit", null)?.let { n -> KittyOutfit.entries.find { it.name == n } }
        _peersMet.value = set(p, "peers").size
        _places.value = set(p, "places").size
        _caches.value = set(p, "caches").size
        _shares.value = p.getInt("shares", 0)
        _seenTips.value = p.getStringSet("tips", emptySet())!!.toSet()
    }

    private fun set(p: SharedPreferences, key: String): Set<String> = p.getStringSet(key, emptySet())!!

    private fun salt(p: SharedPreferences): String = p.getString("salt", null) ?: java.util.UUID.randomUUID().toString().also {
        p.edit().putString("salt", it).apply()
    }

    private fun digest(p: SharedPreferences, value: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt(p) + value).toByteArray())
            .take(6).joinToString("") { "%02x".format(it) }

    /** Adds a hashed value to a set; returns its new size, or null when it was already there. */
    @Synchronized
    private fun addTo(key: String, value: String): Int? {
        val p = prefs ?: return null
        val h = digest(p, value)
        val current = set(p, key)
        if (h in current || current.size >= MAX_SEEN) return null
        val next = current + h
        p.edit().putStringSet(key, next).apply()
        return next.size
    }

    @Synchronized
    fun award(badge: Badge) {
        val p = prefs ?: return
        if (badge in _badges.value) return
        val next = _badges.value + badge
        _badges.value = next
        p.edit().putStringSet("badges", next.map { it.name }.toSet()).apply()
        _unlocked.tryEmit(badge)
    }

    fun hasOutfit(outfit: KittyOutfit) = _badges.value.any { it.outfit == outfit }

    fun wear(outfit: KittyOutfit?) {
        if (outfit != null && !hasOutfit(outfit)) return
        _outfit.value = outfit
        prefs?.edit()?.putString("outfit", outfit?.name)?.apply()
    }

    // MARK: events from the app

    fun onMeshMessageSent() = award(Badge.FIRST_MESH)
    fun onPrivateRoom() = award(Badge.FIRST_ROOM)
    fun onDuressSet() = award(Badge.SECRET_AGENT)
    fun onTorRotated() = award(Badge.TOR_GHOST)

    /** A phone seen on the mesh, identified by its long-term key (only a salted hash is kept). */
    fun onPeerMet(stableKey: String) {
        addTo("peers", stableKey)?.let { _peersMet.value = it }
    }

    /** The app was downloaded from our hotspot page. */
    @Synchronized
    fun onAppShared() {
        val p = prefs ?: return
        val n = p.getInt("shares", 0) + 1
        p.edit().putInt("shares", n).apply()
        _shares.value = n
        award(Badge.AMBASSADOR)
    }

    fun onPlaceVisited(geohash: String) {
        addTo("places", geohash)?.let {
            _places.value = it
            if (it >= GLOBETROTTER_GOAL) award(Badge.GLOBETROTTER)
        }
    }

    /** Someone else's location note was read here: one stamp per place. */
    fun onCacheFound(geohash: String) {
        addTo("caches", geohash)?.let {
            _caches.value = it
            award(Badge.GEOCACHER)
        }
    }

    fun score() = _peersMet.value + 3 * _shares.value

    // MARK: kitty guide

    fun tipSeen(id: String) = id in _seenTips.value

    @Synchronized
    fun markTipSeen(id: String) {
        val next = _seenTips.value + id
        _seenTips.value = next
        prefs?.edit()?.putStringSet("tips", next)?.apply()
    }

    fun replayTips() {
        _seenTips.value = emptySet()
        prefs?.edit()?.remove("tips")?.apply()
    }

    /** Panic wipe: forget everything. */
    fun reset() {
        prefs?.edit()?.clear()?.commit()
        prefs?.let { load(it) }
    }
}
