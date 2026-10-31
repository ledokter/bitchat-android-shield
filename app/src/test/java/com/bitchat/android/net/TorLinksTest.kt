package com.bitchat.android.net

import com.bitchat.android.security.SecurityPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.os.Build
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class TorLinksTest {
    private val context = RuntimeEnvironment.getApplication()

    @After
    fun reset() {
        SecurityPreferences.setLinksViaTor(true)
        SecurityPreferences.setLinksTorExplained(false)
        TorLinks.dismiss()
    }

    @Test
    fun `without Tor Browser no link opens`() {
        assertFalse(TorLinks.open(context, "https://example.org"))
        assertEquals(TorLinks.Pending("https://example.org", TorLinks.Reason.MISSING), TorLinks.pending.value)
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun `with the setting off chatbit does not even try`() {
        SecurityPreferences.setLinksViaTor(false)
        assertFalse(TorLinks.open(context, "https://example.org"))
        assertEquals(TorLinks.Reason.OFF, TorLinks.pending.value!!.reason)
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun `with Tor Browser the link is copied and Tor Browser opens, explained the first time`() {
        val pm = shadowOf(context.packageManager)
        pm.installPackage(android.content.pm.PackageInfo().apply { packageName = "org.torproject.torbrowser" })
        val launch = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER).setPackage("org.torproject.torbrowser")
        pm.addResolveInfoForIntent(launch, android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply { packageName = "org.torproject.torbrowser"; name = "Home" }
        })
        // first link: the explanation, nothing opened yet
        assertFalse(TorLinks.open(context, "https://example.org"))
        assertEquals(TorLinks.Reason.PASTE, TorLinks.pending.value!!.reason)
        assertNull(shadowOf(context).nextStartedActivity)
        // "copy and open": Tor Browser starts, the link is on the clipboard, nothing else opens it
        assertTrue(TorLinks.copyAndOpenTorBrowser(context, "https://example.org"))
        assertEquals("org.torproject.torbrowser", shadowOf(context).nextStartedActivity.`package`)
        val clip = context.getSystemService(android.content.ClipboardManager::class.java).primaryClip!!
        assertEquals("https://example.org", clip.getItemAt(0).text.toString())
        // later links skip the explanation
        assertTrue(TorLinks.open(context, "https://example.net"))
        assertNull(TorLinks.pending.value)
    }
}
