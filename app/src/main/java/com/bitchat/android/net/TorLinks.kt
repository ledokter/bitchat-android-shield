package com.bitchat.android.net

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.bitchat.android.security.SecureClipboard
import com.bitchat.android.security.SecurityPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * chatbit: a web link leaves the app only through Tor Browser, never through the phone's default
 * browser (which would reveal the real IP of whoever taps it). With the setting off, or without
 * Tor Browser, chatbit does not even try: the person may copy the link and paste it into a
 * browser of their choice, at their own risk, and is shown where to get Tor Browser.
 */
object TorLinks {
    /** Tor Project builds, stable first (declared in the manifest's <queries>). */
    private val PACKAGES = listOf("org.torproject.torbrowser", "org.torproject.torbrowser_alpha")
    const val GUARDIAN_FDROID = "https://guardianproject.info/fdroid/"
    const val TORPROJECT_ANDROID = "https://www.torproject.org/download/#android"

    /** A link chatbit refused to open: [torOff] = the setting is off, else Tor Browser is missing. */
    data class Refused(val url: String, val torOff: Boolean)

    private val _refused = MutableStateFlow<Refused?>(null)
    val refused: StateFlow<Refused?> = _refused.asStateFlow()

    fun dismiss() {
        _refused.value = null
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

    /** Opens [url] in Tor Browser; true when it did. Otherwise nothing opens (see [refused]). */
    fun open(context: Context, url: String): Boolean {
        if (!SecurityPreferences.linksViaTor.value) {
            _refused.value = Refused(url, torOff = true)
            return false
        }
        val pkg = installedPackage(context) ?: run {
            _refused.value = Refused(url, torOff = false)
            return false
        }
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: ActivityNotFoundException) {
            _refused.value = Refused(url, torOff = false)
            false
        }
    }

    /** The person's own choice: the link goes to the clipboard, to paste wherever they want. */
    fun copy(context: Context, url: String) {
        // same rules as every chatbit copy (cleared after 60 s when that setting is on)
        SecureClipboard.copy(context, "link", url)
        _refused.value = null
    }

    /**
     * The only pages chatbit opens outside Tor: the official places to get Tor Browser itself
     * (there is no Tor Browser yet to open them with).
     */
    fun openSource(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        _refused.value = null
    }
}
