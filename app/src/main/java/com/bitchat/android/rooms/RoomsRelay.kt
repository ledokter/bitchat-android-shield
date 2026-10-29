package com.bitchat.android.rooms

import android.content.Context
import android.util.Log
import com.bitchat.android.net.OkHttpProvider
import com.bitchat.android.nostr.NostrEvent
import com.bitchat.android.nostr.NostrIdentity
import com.bitchat.android.server.ChatbitServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/**
 * Connection to the chatbit rooms relay (onion service, through Tor). The relay only serves
 * genuine chatbit apps: it asks for NIP-42 AUTH, answered with a throwaway identity and the
 * server pass. Free rooms hold 10 members; a Pro member lifts the limit and keeps 30 days of
 * messages for the others (see the server).
 */
class RoomsRelay(
    private val context: Context,
    private val onEvent: (NostrEvent) -> Unit
) {
    enum class Status { OFF, CONNECTING, READY, NO_PASS }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ArrayDeque<String>()
    @Volatile private var tags: List<String> = emptyList()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var ready = false
    private var authId: String? = null
    private var retry: Job? = null
    private var backoffMs = RETRY_MIN_MS

    private val _status = MutableStateFlow(Status.OFF)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Last refusal from the relay for a message of ours ("blocked: free rooms hold 10 members…"). */
    private val _refusal = MutableStateFlow<String?>(null)
    val refusal: StateFlow<String?> = _refusal.asStateFlow()

    /** Rooms to follow; connects when there is at least one, closes when none. */
    @Synchronized
    fun follow(roomTags: List<String>) {
        tags = roomTags
        when {
            roomTags.isEmpty() -> close()
            socket == null -> connect()
            ready -> subscribe()
        }
    }

    @Synchronized
    fun send(event: NostrEvent) {
        _refusal.value = null
        val frame = JSONArray().put("EVENT").put(JSONObject(event.toJsonString())).toString()
        if (ready) socket?.send(frame) else {
            if (pending.size >= MAX_PENDING) pending.removeFirst()
            pending.addLast(frame)
            if (socket == null && tags.isNotEmpty()) connect()
        }
    }

    @Synchronized
    fun close() {
        retry?.cancel()
        socket?.close(1000, null)
        socket = null
        ready = false
        pending.clear()
        _status.value = Status.OFF
    }

    @Synchronized
    private fun connect() {
        if (socket != null) return
        _status.value = Status.CONNECTING
        val request = Request.Builder().url(ChatbitServer.RELAY_URL).build()
        socket = OkHttpProvider.webSocketClient().newWebSocket(request, Listener())
    }

    private fun subscribe() {
        val filter = JSONObject()
            .put("kinds", JSONArray().put(ROOM_KIND))
            .put("#h", JSONArray(tags))
            .put("since", System.currentTimeMillis() / 1000 - HISTORY_S)
        socket?.send(JSONArray().put("REQ").put(SUB_ID).put(filter).toString())
    }

    private fun authenticate(ws: WebSocket, challenge: String) = scope.launch {
        val pass = ChatbitServer.pass(context)
        if (pass == null) {
            _status.value = Status.NO_PASS
            ws.close(1000, null)
            return@launch
        }
        val identity = NostrIdentity.generate()
        val event = identity.signEvent(
            NostrEvent(
                pubkey = identity.publicKeyHex,
                createdAt = (System.currentTimeMillis() / 1000).toInt(),
                kind = AUTH_KIND,
                tags = listOf(listOf("relay", ChatbitServer.RELAY_URL), listOf("challenge", challenge), listOf("pass", pass.token)),
                content = ""
            )
        )
        authId = event.id
        ws.send(JSONArray().put("AUTH").put(JSONObject(event.toJsonString())).toString())
    }

    @Synchronized
    private fun onReady() {
        ready = true
        backoffMs = RETRY_MIN_MS
        _status.value = Status.READY
        subscribe()
        while (pending.isNotEmpty()) socket?.send(pending.removeFirst())
    }

    @Synchronized
    private fun onClosed(ws: WebSocket) {
        if (socket !== ws) return
        socket = null
        ready = false
        if (_status.value != Status.NO_PASS) _status.value = Status.CONNECTING
        if (tags.isEmpty()) return
        retry?.cancel()
        // without a pass (no Play, copy of the app), each try would ask Play Integrity again
        val wait = if (_status.value == Status.NO_PASS) NO_PASS_RETRY_MS else backoffMs
        retry = scope.launch {
            delay(wait)
            backoffMs = (backoffMs * 2).coerceAtMost(RETRY_MAX_MS)
            synchronized(this@RoomsRelay) { if (tags.isNotEmpty()) connect() }
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = runCatching { JSONArray(text) }.getOrNull() ?: return
            when (msg.optString(0)) {
                "AUTH" -> authenticate(webSocket, msg.optString(1))
                "OK" -> {
                    val ok = msg.optBoolean(2)
                    when {
                        msg.optString(1) == authId -> if (ok) onReady() else {
                            // the pass was refused (expired, server key changed): take a new one next time
                            ChatbitServer.invalidate()
                            webSocket.close(1000, null)
                        }
                        !ok -> _refusal.value = msg.optString(3)
                    }
                }
                "EVENT" -> msg.optJSONObject(2)?.let { NostrEvent.fromJsonString(it.toString()) }?.let(onEvent)
                "CLOSED" -> Log.w(TAG, "subscription closed: ${msg.optString(2)}")
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = this@RoomsRelay.onClosed(webSocket)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "relay unreachable: ${t.message}")
            this@RoomsRelay.onClosed(webSocket)
        }
    }

    private companion object {
        const val TAG = "RoomsRelay"
        const val SUB_ID = "chatbit-rooms"
        const val ROOM_KIND = 20000
        const val AUTH_KIND = 22242
        const val HISTORY_S = 30L * 24 * 3600
        const val MAX_PENDING = 50
        const val RETRY_MIN_MS = 5_000L
        const val RETRY_MAX_MS = 120_000L
        const val NO_PASS_RETRY_MS = 30 * 60_000L
    }
}
