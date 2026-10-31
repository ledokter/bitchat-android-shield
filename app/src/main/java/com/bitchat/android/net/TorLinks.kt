package com.bitchat.android.net

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.bitchat.android.security.SecureClipboard
import com.bitchat.android.security.SecurityPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * chatbit: a web link leaves the app only through Tor Browser, never through the phone's default
 * browser (which would reveal the real IP of whoever taps it).
 *
 * Tor Browser (16+) accepts no link from other apps: no http(s) VIEW filter, its own scheme is not
 * exported. So chatbit copies the link to its secure clipboard and opens Tor Browser, where the
 * person pastes it. With the setting off, or without Tor Browser, nothing opens: the person may
 * copy the link at their own risk, or get Tor Browser from its official sources.
 */
object TorLinks {
    /** Tor Project builds, stable first (declared in the manifest's <queries>). */
    private val PACKAGES = listOf("org.torproject.torbrowser", "org.torproject.torbrowser_alpha")
    const val GUARDIAN_FDROID = "https://guardianproject.info/fdroid/"
    const val TORPROJECT_ANDROID = "https://www.torproject.org/download/#android"

    enum class Reason {
        /** "Open links with Tor Browser" is switched off: chatbit opens nothing. */
        OFF,
        /** Tor Browser is not installed. */
        MISSING,
        /** First link with Tor Browser installed: explain copy, paste and the secure clipboard. */
        PASTE
    }

    /** A link waiting on the person: the dialog shown depends on [reason]. */
    data class Pending(val url: String, val reason: Reason)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    /** Tor Browser was opened with the link on the clipboard: the app reminds to paste it. */
    private val _copied = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val copied: SharedFlow<Unit> = _copied

    fun dismiss() {
        _pending.value = null
    }

    fun installedPackage(context: Context): String? = PACKAGES.firstOrNull { pkg ->
        try {
            if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            else @Suppress("DEPRECATION") context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * A link was tapped. True when Tor Browser was opened with the link in the clipboard; false
     * when the person must decide first (see [pending]).
     */
    fun open(context: Context, url: String): Boolean {
        val reason = when {
            !SecurityPreferences.linksViaTor.value -> Reason.OFF
            installedPackage(context) == null -> Reason.MISSING
            !SecurityPreferences.linksTorExplained.value -> Reason.PASTE
            else -> return copyAndOpenTorBrowser(context, url)
        }
        _pending.value = Pending(url, reason)
        return false
    }

    /** Copies [url] to the secure clipboard and opens Tor Browser, where the person pastes it. */
    fun copyAndOpenTorBrowser(context: Context, url: String): Boolean {
        _pending.value = null
        SecurityPreferences.setLinksTorExplained(true)
        val launch = installedPackage(context)?.let { context.packageManager.getLaunchIntentForPackage(it) }
        if (launch == null) {
            _pending.value = Pending(url, Reason.MISSING)
            return false
        }
        SecureClipboard.copy(context, "link", url)
        return try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            _copied.tryEmit(Unit)
            true
        } catch (_: ActivityNotFoundException) {
            _pending.value = Pending(url, Reason.MISSING)
            false
        }
    }

    /** The person's own choice: the link goes to the clipboard, to paste wherever they want. */
    fun copy(context: Context, url: String) {
        // same rules as every chatbit copy (cleared after 60 s when that setting is on)
        SecureClipboard.copy(context, "link", url)
        _pending.value = null
    }

    /**
     * The only pages chatbit opens outside Tor: the official places to get Tor Browser itself
     * (there is no Tor Browser yet to open them with).
     */
    fun openSource(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        _pending.value = null
    }
}
