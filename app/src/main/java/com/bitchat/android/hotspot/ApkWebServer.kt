package com.bitchat.android.hotspot

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.bitchat.android.R
import com.bitchat.android.nostr.BitchatShield
import com.bitchat.android.nostr.ShieldLists
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/**
 * Offline mini-site served over the sharing hotspot: download chatbit and the anti-flood lists
 * (blocked and flagged keys, in the bitchat-bot formats). The page is written in the visitor's
 * language, taken from their browser's Accept-Language header. Based on NanoHTTPD.
 */
class ApkWebServer(
    private val context: Context,
    private val apkFile: File,
    private val port: Int = DEFAULT_PORT
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "ApkWebServer"
        const val DEFAULT_PORT = 9999

        private val LISTS = mapOf(
            "/lists/blocked.txt" to (ShieldLists.Kind.BLOCKED to false),
            "/lists/blocked.json" to (ShieldLists.Kind.BLOCKED to true),
            "/lists/flagged.txt" to (ShieldLists.Kind.FLAGGED to false),
            "/lists/flagged.json" to (ShieldLists.Kind.FLAGGED to true)
        )

        // the chatbit pixel-art cat, same grid as the launcher icon
        private const val LOGO_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 21" width="120" shape-rendering="crispEdges"><path fill="#fff" d="M3 1h1v1h-1zM3 2h2v1h-2zM3 3h3v1h-3zM3 4h4v1h-4zM3 5h5v1h-5zM9 5h6v1h-6zM18 5h1v1h-1zM6 6h12v1h-12zM4 7h16v1h-16zM3 8h18v1h-18zM3 9h18v1h-18zM2 10h20v1h-20zM2 11h4v1h-4zM8 11h8v1h-8zM18 11h4v1h-4zM4 12h2v1h-2zM8 12h8v1h-8zM18 12h2v1h-2zM2 13h4v1h-4zM8 13h8v1h-8zM18 13h4v1h-4zM4 14h7v1h-7zM13 14h7v1h-7zM3 15h18v1h-18zM4 16h16v1h-16zM4 17h16v1h-16zM6 18h12v1h-12zM9 19h6v1h-6z"/><path fill="#1a1a1a" d="M3 0h1v1h-1zM15 0h3v1h-3zM19 0h3v1h-3zM2 1h1v1h-1zM4 1h1v1h-1zM14 1h1v1h-1zM18 1h1v1h-1zM22 1h1v1h-1zM2 2h1v1h-1zM5 2h1v1h-1zM14 2h1v1h-1zM22 2h1v1h-1zM2 3h1v1h-1zM6 3h1v1h-1zM14 3h1v1h-1zM22 3h1v1h-1zM2 4h1v1h-1zM7 4h1v1h-1zM9 4h6v1h-6zM22 4h1v1h-1zM2 5h1v1h-1zM8 5h1v1h-1zM22 5h1v1h-1zM3 6h3v1h-3zM18 6h4v1h-4zM3 7h1v1h-1zM20 7h1v1h-1zM2 8h1v1h-1zM21 8h1v1h-1zM2 9h1v1h-1zM21 9h1v1h-1zM1 10h1v1h-1zM22 10h1v1h-1zM1 11h1v1h-1zM6 11h2v1h-2zM16 11h2v1h-2zM22 11h1v1h-1zM0 12h4v1h-4zM6 12h2v1h-2zM16 12h2v1h-2zM20 12h4v1h-4zM1 13h1v1h-1zM6 13h2v1h-2zM16 13h2v1h-2zM22 13h1v1h-1zM0 14h4v1h-4zM20 14h4v1h-4zM2 15h1v1h-1zM21 15h1v1h-1zM1 16h3v1h-3zM20 16h3v1h-3zM3 17h1v1h-1zM20 17h1v1h-1zM4 18h2v1h-2zM18 18h2v1h-2zM6 19h3v1h-3zM15 19h3v1h-3zM9 20h6v1h-6z"/><path fill="#ffc828" d="M11 14h2v1h-2z"/><path fill="#e83c78" d="M15 1h3v1h-3zM19 1h3v1h-3zM15 2h3v1h-3zM19 2h3v1h-3zM15 3h2v1h-2zM20 3h2v1h-2zM15 4h3v1h-3zM19 4h3v1h-3zM15 5h3v1h-3zM19 5h3v1h-3z"/><path fill="#a8144f" d="M18 2h1v1h-1zM17 3h3v1h-3zM18 4h1v1h-1z"/></svg>"""
    }

    private val appVersion: String by lazy {
        try {
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)?.versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        Log.d(TAG, "Request: ${session.method} $uri")
        return when {
            uri == "/chatbit.apk" || uri == "/bitchat.apk" -> serveApk()
            uri in LISTS -> serveList(uri)
            uri == "/favicon.ico" -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "")
            else -> newFixedLengthResponse(
                Response.Status.OK,
                "text/html; charset=utf-8",
                landingPage(localized(session.headers["accept-language"]))
            )
        }
    }

    private fun serveApk(): Response {
        return try {
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file not found")
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "APK not found")
            }
            val response = newFixedLengthResponse(
                Response.Status.OK,
                "application/vnd.android.package-archive",
                FileInputStream(apkFile),
                apkFile.length()
            )
            response.addHeader("Content-Disposition", "attachment; filename=\"chatbit-$appVersion.apk\"")
            response.addHeader("Accept-Ranges", "bytes")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Error serving APK", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error")
        }
    }

    private fun serveList(uri: String): Response {
        val (kind, json) = LISTS.getValue(uri)
        val keys = if (kind == ShieldLists.Kind.BLOCKED) {
            com.bitchat.android.ui.DataManager(context).apply { loadGeohashBlockedUsers() }.geohashBlockedUsers
        } else {
            BitchatShield.flaggedKeys()
        }
        val body = ShieldLists.export(kind, keys, { null }, json)
        val response = newFixedLengthResponse(
            Response.Status.OK,
            if (json) "application/json; charset=utf-8" else "text/plain; charset=utf-8",
            body
        )
        response.addHeader("Content-Disposition", "attachment; filename=\"${ShieldLists.fileName(kind, json)}\"")
        return response
    }

    /** Resources in the visitor's language (first Accept-Language tag), falling back to the phone's. */
    private fun localized(acceptLanguage: String?): Context {
        val tag = acceptLanguage?.split(',')?.firstOrNull()?.substringBefore(';')?.trim()
        if (tag.isNullOrEmpty()) return context
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(config)
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun landingPage(c: Context): String {
        fun t(id: Int, vararg args: Any) = esc(c.getString(id, *args))
        val sizeMb = apkFile.length() / 1024 / 1024
        val lang = esc(c.resources.configuration.locales[0].toLanguageTag())
        return """<!DOCTYPE html>
<html lang="$lang">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>chatbit</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:system-ui,-apple-system,Roboto,Arial,sans-serif;background:#ffe4ef;color:#1a1a1a;padding:16px;display:flex;justify-content:center}
main{background:#fff;border-radius:20px;padding:28px 20px;max-width:480px;width:100%;box-shadow:0 8px 30px rgba(194,24,91,.15)}
.logo{text-align:center;margin-bottom:8px}
h1{text-align:center;font-size:30px;color:#c2185b}
.sub{text-align:center;color:#555;margin:4px 0 22px}
a.btn{display:block;text-align:center;background:#c2185b;color:#fff;text-decoration:none;font-weight:600;font-size:18px;padding:16px;border-radius:14px}
.meta{text-align:center;color:#666;font-size:13px;margin:8px 0 0}
h2{font-size:17px;color:#c2185b;margin:22px 0 10px}
.step{display:flex;gap:12px;align-items:flex-start;background:#fff6fa;border:1px solid #f6c6da;border-radius:14px;padding:14px;margin:10px 0}
.num{flex:none;width:30px;height:30px;border-radius:50%;background:#c2185b;color:#fff;font-weight:700;display:flex;align-items:center;justify-content:center}
.step b{display:block;font-size:16px;margin-bottom:4px}
.step p{font-size:14px;line-height:1.5;color:#333}
.step .btn{margin-top:10px}
.tip{margin-top:8px;font-size:13px;line-height:1.5;background:#fff3cd;border-radius:10px;padding:8px 10px;color:#5c4400}
.ok{margin-top:6px;font-size:13px;color:#2e7d32}
.help{background:#f3f3f3;border-radius:14px;padding:12px 14px;margin-top:18px}
.help b{color:#c2185b}
.help ul{padding-left:18px;margin-top:6px;font-size:14px;line-height:1.55}
details{margin-top:18px}
summary{cursor:pointer;font-weight:600;color:#c2185b;font-size:15px}
.lists{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:10px}
.lists a{display:block;text-align:center;border:2px solid #c2185b;color:#c2185b;text-decoration:none;padding:10px 6px;border-radius:12px;font-size:14px}
p.note{font-size:13px;color:#555;margin-top:10px;line-height:1.5}
footer{text-align:center;font-size:12px;color:#888;margin-top:24px}
</style>
</head>
<body><main>
<div class="logo">$LOGO_SVG</div>
<h1>chatbit</h1>
<p class="sub">${t(R.string.about_tagline)}<br>${t(R.string.web_intro)}</p>
<div class="step"><span class="num">1</span><div><b>${t(R.string.web_s1_title)}</b>
<p>${t(R.string.web_s1_desc)}</p>
<a class="btn" href="/chatbit.apk">⬇ ${t(R.string.web_download)}</a>
<p class="meta">${t(R.string.web_version_size, esc(appVersion), sizeMb)}</p></div></div>
<div class="step"><span class="num">2</span><div><b>${t(R.string.web_s2_title)}</b>
<p>${t(R.string.web_s2_desc)}</p></div></div>
<div class="step"><span class="num">3</span><div><b>${t(R.string.web_s3_title)}</b>
<p>${t(R.string.web_s3_desc)}</p>
<p class="tip">🛡️ ${t(R.string.web_s3_play)}</p>
<p class="ok">✓ ${t(R.string.web_s3_undo)}</p></div></div>
<div class="step"><span class="num">4</span><div><b>${t(R.string.web_s4_title)}</b>
<p>${t(R.string.web_s4_desc)}</p></div></div>
<div class="help"><b>${t(R.string.web_help_title)}</b><ul>
<li>${t(R.string.web_help_1)}</li>
<li>${t(R.string.web_help_2)}</li>
<li>${t(R.string.web_help_3)}</li>
</ul></div>
<details><summary>${t(R.string.web_lists_title)}</summary>
<div class="lists">
<a href="/lists/blocked.txt">${t(R.string.flood_blocked_file, "txt")}</a>
<a href="/lists/blocked.json">${t(R.string.flood_blocked_file, "json")}</a>
<a href="/lists/flagged.txt">${t(R.string.flood_flagged_file, "txt")}</a>
<a href="/lists/flagged.json">${t(R.string.flood_flagged_file, "json")}</a>
</div>
<p class="note">${t(R.string.web_lists_import)}</p>
</details>
<footer>${t(R.string.web_footer)}</footer>
</main></body>
</html>"""
    }

    fun startServer() {
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d(TAG, "Web server started on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start web server", e)
            throw e
        }
    }

    fun stopServer() {
        try {
            stop()
            Log.d(TAG, "Web server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping web server", e)
        }
    }
}
