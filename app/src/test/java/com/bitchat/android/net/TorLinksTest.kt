package com.bitchat.android.net

import com.bitchat.android.security.SecurityPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TorLinksTest {
    private val context = RuntimeEnvironment.getApplication()

    @After
    fun reset() {
        SecurityPreferences.setLinksViaTor(true)
        TorLinks.dismiss()
    }

    @Test
    fun `without Tor Browser no link opens`() {
        assertFalse(TorLinks.open(context, "https://example.org"))
        assertEquals(TorLinks.Refused("https://example.org", torOff = false), TorLinks.refused.value)
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun `with the setting off chatbit does not even try`() {
        SecurityPreferences.setLinksViaTor(false)
        assertFalse(TorLinks.open(context, "https://example.org"))
        assertTrue(TorLinks.refused.value!!.torOff)
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun `with Tor Browser the link opens there and only there`() {
        shadowOf(context.packageManager).installPackage(
            android.content.pm.PackageInfo().apply { packageName = "org.torproject.torbrowser" }
        )
        assertTrue(TorLinks.open(context, "https://example.org"))
        val intent = shadowOf(context).nextStartedActivity
        assertEquals("org.torproject.torbrowser", intent.`package`)
        assertEquals("https://example.org", intent.dataString)
        assertNull(TorLinks.refused.value)
    }
}
