package com.bitchat.android.rooms

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.bitchat.android.nostr.NostrEvent
import com.bitchat.android.nostr.NostrFilter
import com.bitchat.android.nostr.NostrIdentity
import com.bitchat.android.nostr.NostrKind
import com.bitchat.android.nostr.NostrProofOfWork
import com.bitchat.android.nostr.NostrRelayManager
import com.bitchat.android.nostr.PoWPreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Private rooms: persisted list (app-private storage, wiped by panic), relay subscription on the
 * always-connected default relays (through Tor like everything else), sending and receiving.
 * Messages are kept in memory only.
 */
object PrivateRooms {
    private const val TAG = "PrivateRooms"
    private const val PREFS = "chatbit_private_rooms"
    private const val KEY_ROOMS = "rooms"
    private const val SUBSCRIPTION_ID = "chatbit-private-rooms"
    private const val MAX_MESSAGES = 500
    private const val MAX_TEXT = 2000

    data class Message(
        val id: String,
        val roomTag: String,
        val nickname: String,
        val senderPubkey: String,
        val text: String,
        val atMs: Long,
        val mine: Boolean
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val seen = LinkedHashSet<String>()

    private val _rooms = MutableStateFlow<List<PrivateRoom>>(emptyList())
    val rooms: StateFlow<List<PrivateRoom>> = _rooms.asStateFlow()

    private val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val messages: StateFlow<Map<String, List<Message>>> = _messages.asStateFlow()

    /** Invite opened from a chatbit://room link, waiting for the user to confirm. */
    private val _pendingInvite = MutableStateFlow<PrivateRoom.Invite?>(null)
    val pendingInvite: StateFlow<PrivateRoom.Invite?> = _pendingInvite.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _rooms.value = load()
        resubscribe()
    }

    fun offerInvite(invite: PrivateRoom.Invite?) {
        _pendingInvite.value = invite
        if (invite != null) _sheetOpen.value = true
    }

    /** Rooms sheet visibility (opened from the chat header or by an invitation link). */
    private val _sheetOpen = MutableStateFlow(false)
    val sheetOpen: StateFlow<Boolean> = _sheetOpen.asStateFlow()

    fun openSheet() {
        _sheetOpen.value = true
    }

    fun closeSheet() {
        _sheetOpen.value = false
        _pendingInvite.value = null
    }

    fun create(name: String): PrivateRoom {
        val room = PrivateRoom.create(name, NostrIdentity.generate().privateKeyHex)
        save(_rooms.value + room)
        return room
    }

    /** Joins (or returns the already joined room). */
    fun join(invite: PrivateRoom.Invite): PrivateRoom {
        _rooms.value.firstOrNull { it.id.contentEquals(invite.id) }?.let { return it }
        val room = PrivateRoom(invite.id, invite.key, invite.name, NostrIdentity.generate().privateKeyHex, System.currentTimeMillis())
        save(_rooms.value + room)
        return room
    }

    fun leave(room: PrivateRoom) {
        save(_rooms.value.filterNot { it == room })
        _messages.value = _messages.value - room.tag
    }

    /** Panic / auto-wipe: forget every room and message. */
    fun wipe() {
        if (::prefs.isInitialized) prefs.edit().clear().apply()
        _rooms.value = emptyList()
        _messages.value = emptyMap()
        _pendingInvite.value = null
        synchronized(seen) { seen.clear() }
        runCatching { NostrRelayManager.getInstance(appContext).unsubscribe(SUBSCRIPTION_ID) }
    }

    fun send(room: PrivateRoom, nickname: String, text: String) {
        val body = text.trim().take(MAX_TEXT)
        if (body.isEmpty()) return
        scope.launch {
            try {
                val identity = NostrIdentity.fromPrivateKey(room.identityPrivateKeyHex)
                var event = NostrEvent(
                    pubkey = identity.publicKeyHex,
                    createdAt = (System.currentTimeMillis() / 1000).toInt(),
                    kind = NostrKind.EPHEMERAL_EVENT,
                    tags = listOf(listOf("h", room.tag)),
                    content = room.encrypt(PrivateRoom.encodeMessage(nickname, body))
                )
                val pow = PoWPreferenceManager.getCurrentSendSettings()
                if (pow.enabled && pow.difficulty > 0) {
                    NostrProofOfWork.mineEvent(event, pow.difficulty, 2_000_000)?.let { event = it }
                }
                val signed = identity.signEvent(event)
                remember(signed.id)
                add(Message(signed.id, room.tag, nickname, identity.publicKeyHex, body, System.currentTimeMillis(), mine = true))
                val relays = NostrRelayManager.defaultRelays()
                NostrRelayManager.getInstance(appContext).sendEvent(signed, relays)
            } catch (e: Exception) {
                Log.e(TAG, "Send failed: ${e.message}")
            }
        }
    }

    private fun resubscribe() {
        val manager = NostrRelayManager.getInstance(appContext)
        runCatching { manager.unsubscribe(SUBSCRIPTION_ID) }
        val tags = _rooms.value.map { it.tag }
        if (tags.isEmpty()) return
        manager.subscribe(
            filter = NostrFilter(
                kinds = listOf(NostrKind.EPHEMERAL_EVENT),
                since = (System.currentTimeMillis() / 1000).toInt() - 3600,
                tagFilters = mapOf("h" to tags)
            ),
            id = SUBSCRIPTION_ID,
            handler = ::onEvent,
            targetRelayUrls = NostrRelayManager.defaultRelays()
        )
    }

    private fun onEvent(event: NostrEvent) {
        if (!remember(event.id)) return
        val tag = event.tags.firstOrNull { it.size >= 2 && it[0] == "h" }?.get(1) ?: return
        val room = _rooms.value.firstOrNull { it.tag == tag } ?: return
        scope.launch {
            if (!event.isValidSignature()) return@launch
            val (nick, text) = room.decrypt(event.content)?.let(PrivateRoom::decodeMessage) ?: return@launch
            val mine = NostrIdentity.fromPrivateKey(room.identityPrivateKeyHex).publicKeyHex == event.pubkey
            if (mine) return@launch
            add(Message(event.id, tag, nick.ifBlank { "anon" }, event.pubkey, text.take(MAX_TEXT), event.createdAt * 1000L, mine = false))
        }
    }

    /** True when the id was not seen yet. */
    private fun remember(id: String): Boolean = synchronized(seen) {
        if (!seen.add(id)) return false
        if (seen.size > 5000) seen.remove(seen.first())
        true
    }

    private fun add(m: Message) {
        val map = _messages.value.toMutableMap()
        map[m.roomTag] = ((map[m.roomTag] ?: emptyList()) + m).sortedBy { it.atMs }.takeLast(MAX_MESSAGES)
        _messages.value = map
    }

    private fun save(rooms: List<PrivateRoom>) {
        _rooms.value = rooms
        val arr = JSONArray()
        for (r in rooms) {
            arr.put(JSONObject().apply {
                put("id", b64(r.id)); put("key", b64(r.key)); put("name", r.name)
                put("identity", r.identityPrivateKeyHex); put("joined", r.joinedAtMs)
            })
        }
        if (::prefs.isInitialized) prefs.edit().putString(KEY_ROOMS, arr.toString()).apply()
        if (::appContext.isInitialized) resubscribe()
    }

    private fun load(): List<PrivateRoom> = try {
        val arr = JSONArray(prefs.getString(KEY_ROOMS, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PrivateRoom(unb64(o.getString("id")), unb64(o.getString("key")), o.getString("name"),
                o.getString("identity"), o.optLong("joined"))
        }
    } catch (e: Exception) {
        Log.e(TAG, "Cannot load rooms: ${e.message}")
        emptyList()
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
