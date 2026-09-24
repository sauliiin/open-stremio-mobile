package com.mdblisthub.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who gets the link, and when the worker has to let go of a download already
 * in flight.
 *
 * The rule itself is unchanged and deliberately so — see [PrefetchPolicy]. It
 * is the *timing* that had no test and turned out to need one: standing down
 * only ever stopped the next chunk, and only ever as fast as the position
 * ticker noticed, so a player that had just run out of road went on sharing
 * the link with a 16MB download for as long as that read took.
 */
class PrefetchPolicyTest {

    private val none = PrefetchPolicy.Effect.NONE
    private val cancel = PrefetchPolicy.Effect.CANCEL_IN_FLIGHT

    private fun policy() = PrefetchPolicy(starvingBufferMs = 15_000L)

    // ------------------------------------------------ the rule, unchanged

    @Test
    fun `a loading player with a thin buffer owns the link`() {
        val policy = policy()
        assertEquals(cancel, policy.onSample(loading = true, bufferedAheadMs = 4_000))
        assertFalse(policy.mayStartChunk)
    }

    /**
     * The case the paired condition exists for. A player capped in *bytes*
     * holds fewer seconds as the bitrate rises, so a full, idle buffer can sit
     * below the threshold for an entire film — and a lone threshold would
     * switch the prefetcher off on exactly the release that needs it.
     */
    @Test
    fun `a satisfied player with a thin buffer does not own the link`() {
        val policy = policy()
        assertEquals(none, policy.onSample(loading = false, bufferedAheadMs = 11_000))
        assertTrue(policy.mayStartChunk)
    }

    @Test
    fun `a loading player with a deep buffer shares the link`() {
        val policy = policy()
        assertEquals(none, policy.onSample(loading = true, bufferedAheadMs = 40_000))
        assertTrue(policy.mayStartChunk)
    }

    @Test
    fun `nothing is fetched before anything is known`() {
        assertFalse(policy().mayStartChunk)
    }

    // ----------------------------------------- the timing, which had no test

    /**
     * Standing down is not enough: a `CacheWriter` mid-chunk keeps the second
     * connection open until that whole read completes, which is exactly long
     * enough to keep a starving player starving.
     */
    @Test
    fun `a player that starts starving gets the chunk in flight back`() {
        val policy = policy()
        policy.onSample(loading = false, bufferedAheadMs = 40_000)
        assertEquals(cancel, policy.onSample(loading = true, bufferedAheadMs = 6_000))
    }

    /**
     * And it must not wait for the next position tick to find out. The loading
     * event carries no buffer reading, so it is judged against the last real
     * one rather than a guess.
     */
    @Test
    fun `a loading event acts on the buffer last measured`() {
        val policy = policy()
        policy.onSample(loading = false, bufferedAheadMs = 5_000)
        assertTrue("a satisfied player leaves the link free", policy.mayStartChunk)

        assertEquals(cancel, policy.onLoadingChanged(true))
        assertFalse(policy.mayStartChunk)
    }

    @Test
    fun `a loading event over a deep buffer changes nothing`() {
        val policy = policy()
        policy.onSample(loading = false, bufferedAheadMs = 40_000)
        assertEquals(none, policy.onLoadingChanged(true))
        assertTrue(policy.mayStartChunk)
    }

    /** With no reading yet, a loader that wants bytes is given the benefit of the doubt. */
    @Test
    fun `a loading event before any sample yields the link`() {
        val policy = policy()
        assertEquals(cancel, policy.onLoadingChanged(true))
        assertFalse(policy.mayStartChunk)
    }

    @Test
    fun `a stopped picture takes the link immediately`() {
        val policy = policy()
        policy.onSample(loading = false, bufferedAheadMs = 40_000)
        assertTrue(policy.mayStartChunk)
        assertEquals(cancel, policy.onPlayerStalled())
        assertFalse(policy.mayStartChunk)
    }

    /**
     * A stall clears the history too. Otherwise the next loading event would
     * be judged against a 40-second reading taken before the picture stopped,
     * and hand the link straight back.
     */
    @Test
    fun `a stall forgets the buffer measured before it`() {
        val policy = policy()
        policy.onSample(loading = false, bufferedAheadMs = 40_000)
        policy.onPlayerStalled()
        assertEquals(cancel, policy.onLoadingChanged(true))
    }

    // ------------------------------------------------------- seeks and resets

    @Test
    fun `a seek drops the chunk fetched for where the viewer left`() {
        assertEquals(cancel, policy().onSeek())
    }

    /**
     * And takes the history with it: a buffer measured at the old position
     * says nothing about the new one, and after a skip it is always smaller.
     */
    @Test
    fun `a seek forgets the buffer measured before it`() {
        val policy = policy()
        policy.onSample(loading = false, bufferedAheadMs = 40_000)
        policy.onSeek()
        assertEquals(cancel, policy.onLoadingChanged(true))
    }

    @Test
    fun `a new source starts with no history and no claim on the link`() {
        val policy = policy()
        policy.onSample(loading = false, bufferedAheadMs = 40_000)
        policy.reset()
        assertFalse(policy.mayStartChunk)
    }
}
