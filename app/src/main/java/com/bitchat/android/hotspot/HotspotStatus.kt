package com.bitchat.android.hotspot

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bitchat.android.R
import com.bitchat.android.service.MeshServiceHolder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide view of the APK-sharing hotspot, so a hotspot left running is never forgotten:
 * the chat screen shows a banner with a stop button, an ongoing notification carries a stop
 * action, and the session stops by itself after [IDLE_TIMEOUT_MS] without visitors.
 * Optionally announces itself on the mesh so nearby users can bring friends who lack the app.
 */
object HotspotStatus {
    const val IDLE_TIMEOUT_MS = 60 * 60_000L
    const val ANNOUNCE_EVERY_MS = 10 * 60_000L
    const val ACTION_STOP = "STOP_HOTSPOT"
    /** Marker that starts every mesh announcement, so receivers can recognise it. */
    const val ANNOUNCE_MARKER = "📡 chatbit"

    private const val PREFS = "chatbit_hotspot"
    private const val KEY_ANNOUNCE = "announce_on_mesh"
    private const val CHANNEL_ID = "chatbit_hotspot"
    private const val NOTIFICATION_ID = 0x4854

    data class Live(
        val ssid: String,
        val password: String,
        val url: String,
        val since: Long,
        val stopsAt: Long,
        val peers: Int = 0,
        val downloads: Int = 0
    )

    private val _live = MutableStateFlow<Live?>(null)
    val live: StateFlow<Live?> = _live.asStateFlow()

    private val _stopRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stopRequests = _stopRequests.asSharedFlow()

    private val _announce = MutableStateFlow(false)
    val announce: StateFlow<Boolean> = _announce.asStateFlow()

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        _announce.value = prefs().getBoolean(KEY_ANNOUNCE, false)
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setAnnounce(on: Boolean) {
        _announce.value = on
        if (::appContext.isInitialized) prefs().edit().putBoolean(KEY_ANNOUNCE, on).apply()
        if (on) announceNow()
    }

    fun started(ssid: String, password: String, url: String) {
        val now = System.currentTimeMillis()
        _live.value = Live(ssid, password, url, since = now, stopsAt = now + IDLE_TIMEOUT_MS)
        showNotification()
    }

    fun peersChanged(peers: Int) {
        _live.value = _live.value?.let { if (peers > 0) it.copy(peers = peers, stopsAt = deadline()) else it.copy(peers = 0) }
    }

    fun onDownload() {
        _live.value = _live.value?.let { it.copy(downloads = it.downloads + 1, stopsAt = deadline()) }
    }

    /** Pushes the automatic stop back by a full [IDLE_TIMEOUT_MS]. */
    fun extend() {
        _live.value = _live.value?.copy(stopsAt = deadline())
    }

    private fun deadline() = System.currentTimeMillis() + IDLE_TIMEOUT_MS

    fun requestStop() {
        _stopRequests.tryEmit(Unit)
    }

    fun ended() {
        _live.value = null
        if (::appContext.isInitialized) NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
    }

    /** Public mesh message telling nearby people how to join the hotspot. Plain text, so any bitchat client can read it. */
    fun announceNow() {
        val live = _live.value ?: return
        if (!_announce.value || !::appContext.isInitialized) return
        val text = appContext.getString(R.string.hs_announce_message, ANNOUNCE_MARKER, live.ssid, live.password, live.url)
        runCatching {
            val unified = MeshServiceHolder.unifiedMeshService
            if (unified != null) unified.sendMessage(text, emptyList(), null)
            else MeshServiceHolder.meshService?.sendMessage(text)
        }
    }

    private fun showNotification() {
        if (!::appContext.isInitialized) return
        val ctx = appContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, ctx.getString(R.string.hs_notif_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, HotspotActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK),
            flags
        )
        val stop = PendingIntent.getActivity(
            ctx, 2,
            Intent(ctx, HotspotActivity::class.java).setAction(ACTION_STOP)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            flags
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.hs_active))
            .setContentText(ctx.getString(R.string.hs_notif_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, ctx.getString(R.string.hs_stop_short), stop)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification) }
    }
}
