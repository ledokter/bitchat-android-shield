package com.bitchat.android.nostr

import org.junit.Assert.assertTrue
import org.junit.Test

/** Replay of the 2026-10-29 attack seen by the bot: one throwaway key per message, same nickname, 4 texts. */
class FloodReplayTelegramTest {
    @Test
    fun `throwaway keys with one message each get flagged quickly`() = replay(FloodDetector())

    @Test
    fun `base rules stop the raid with the anti-spam off`() = replay(FloodDetector(FloodDetector.BASE_RULES))

    private fun replay(d: FloodDetector) {
        val texts = listOf("uhq_gouv > telegram !-1179", "uhq_gouv > telegram !-3885", "uhq_gouv > telegram !-3225", "uhq_gouv > telegram !-7005")
        val t0 = 1_000_000.0
        // the app has been running for a while (keys created after start count as "young")
        d.process("f".repeat(64), "hello", "regular", "u0", false, t0 - 600)
        var shown = 0
        for (i in 0 until 200) {
            val pk = "%064x".format(i + 1)
            d.process(pk, texts[i % 4], "BitChat Spam", "u0", false, t0 + i * 0.23)
            if (!d.isFlagged(pk)) shown++
        }
        println("messages shown before the group was flagged: $shown / 200")
        assertTrue("too many spam messages shown: $shown", shown <= 10)
    }
}
