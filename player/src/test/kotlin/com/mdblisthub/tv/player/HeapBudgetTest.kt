package com.mdblisthub.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two numbers derived from the byte budget, both of which can be wrong in
 * ways nothing on screen would attribute to them: a back buffer that quietly
 * eats the forward one, and a resume threshold a device cannot pay.
 *
 * Everything here is pure arithmetic on a budget and a bitrate — the readings
 * those come *from* need a real device, which is a different question.
 */
class HeapBudgetTest {

    // --------------------------------------------------------- back buffer

    /**
     * A budget worth a second of film has no surplus to share, so the back
     * buffer is not merely reduced — it is refused. This is the case that used
     * to be handed a two-second floor it had no way to pay for.
     */
    @Test
    fun `back buffer is refused when the budget cannot cover the forward reserve`() {
        assertEquals(
            0L,
            HeapBudget.backBufferMs(targetBytes = 64 * 1024 * 1024, bytesPerSecond = 64L * 1024 * 1024),
        )
    }

    /**
     * 100MB at 4MB/s is 25 seconds of film. Twenty are reserved for the
     * forward buffer, leaving a five-second surplus — of which the back buffer
     * takes a fifth. Off the top it would have taken five whole seconds.
     */
    @Test
    fun `back buffer tracks twenty percent of the surplus`() {
        assertEquals(
            1_000L,
            HeapBudget.backBufferMs(targetBytes = 100 * 1024 * 1024, bytesPerSecond = 4L * 1024 * 1024),
        )
    }

    /** With room to spare it still reaches the ceiling, so nothing is lost on a big device. */
    @Test
    fun `back buffer still reaches its ceiling on a roomy budget`() {
        assertEquals(
            HeapBudget.MAX_BACK_BUFFER_MS,
            HeapBudget.backBufferMs(targetBytes = 512 * 1024 * 1024, bytesPerSecond = 512L * 1024),
        )
    }

    // ---------------------------------------------------- resume threshold

    /**
     * A device with room for its full cushion keeps it: a third of 112 seconds
     * is far past the ceiling and is clamped straight back to it. The override
     * must never make a comfortable device wait differently.
     */
    @Test
    fun `a roomy budget keeps the full per-device cushion`() {
        assertEquals(
            8_000L,
            HeapBudget.rebufferStartMs(
                targetBytes = 448 * 1024 * 1024,
                bytesPerSecond = 4L * 1024 * 1024,
                ceilingMs = 8_000,
            ),
        )
    }

    /**
     * 64MB at 4MB/s is sixteen seconds of film. Asking for eight of them back
     * before showing a frame is asking half the budget from a link that has
     * just faltered — the freeze only the skip button cleared.
     */
    @Test
    fun `a thin budget resumes on what it can actually rebuild`() {
        assertEquals(
            5_280L,
            HeapBudget.rebufferStartMs(
                targetBytes = 64 * 1024 * 1024,
                bytesPerSecond = 4L * 1024 * 1024,
                ceilingMs = 8_000,
            ),
        )
    }

    @Test
    fun `the resume threshold never drops below its floor`() {
        assertEquals(
            HeapBudget.MIN_REBUFFER_START_MS,
            HeapBudget.rebufferStartMs(
                targetBytes = 64 * 1024 * 1024,
                bytesPerSecond = 64L * 1024 * 1024,
                ceilingMs = 8_000,
            ),
        )
    }

    /**
     * The constrained tier's own ceiling is already at the floor. The clamp
     * must not invert there — a floor above the ceiling is an exception out of
     * `coerceIn`, on the one device class this whole mechanism is for.
     */
    @Test
    fun `a ceiling at or below the floor is not an inverted range`() {
        for (ceiling in listOf(2_500, 2_000, 1_000)) {
            val resume = HeapBudget.rebufferStartMs(
                targetBytes = 64 * 1024 * 1024,
                bytesPerSecond = 64L * 1024 * 1024,
                ceilingMs = ceiling,
            )
            assertTrue("resume $resume must not exceed the ceiling $ceiling", resume <= ceiling)
            assertTrue("resume must be positive", resume > 0)
        }
    }

    /** Whatever the inputs, the threshold stays inside the device's own bound. */
    @Test
    fun `the threshold never exceeds the per-device ceiling`() {
        val budgets = listOf(16, 64, 138, 256, 448).map { it * 1024 * 1024 }
        val rates = listOf(1L, 3L, 5L, 9L).map { it * 1024 * 1024 }
        for (budget in budgets) {
            for (rate in rates) {
                for (ceiling in listOf(2_500, 8_000)) {
                    val resume = HeapBudget.rebufferStartMs(budget, rate, ceiling)
                    assertTrue("$budget @ $rate under $ceiling gave $resume", resume <= ceiling)
                }
            }
        }
    }
}
