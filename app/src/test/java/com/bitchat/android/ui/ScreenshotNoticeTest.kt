package com.bitchat.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenshotNoticeTest {
    @Test
    fun recognisesTheBitchatNotice() {
        assertEquals("anon42", screenshotNoticeSender("* anon42 took a screenshot *"))
        assertEquals("cat#1a2b", screenshotNoticeSender("  * cat#1a2b took a screenshot *  "))
    }

    @Test
    fun ignoresOrdinaryText() {
        assertNull(screenshotNoticeSender("anon42 took a screenshot"))
        assertNull(screenshotNoticeSender("* anon42 gives a warm hug *"))
        assertNull(screenshotNoticeSender("did you see? * x took a screenshot *"))
    }
}
