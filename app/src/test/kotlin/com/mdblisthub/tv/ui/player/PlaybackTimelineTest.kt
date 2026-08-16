package com.mdblisthub.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimelineTest {
    @Test
    fun `maps the touch area to the complete movie duration`() {
        val duration = 120_000L

        assertEquals(0L, seekPositionAt(-100f, 100f, 10f, duration))
        assertEquals(0L, seekPositionAt(10f, 100f, 10f, duration))
        assertEquals(60_000L, seekPositionAt(50f, 100f, 10f, duration))
        assertEquals(duration, seekPositionAt(90f, 100f, 10f, duration))
        assertEquals(duration, seekPositionAt(200f, 100f, 10f, duration))
    }

    @Test
    fun `invalid and collapsed geometry stays bounded`() {
        assertEquals(0L, seekPositionAt(50f, 100f, 10f, 0L))
        assertEquals(0L, seekPositionAt(50f, 0f, 10f, 120_000L))
        assertEquals(60_000L, seekPositionAt(5f, 10f, 10f, 120_000L))
    }
}
