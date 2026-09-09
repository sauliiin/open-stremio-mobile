package com.mdblisthub.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerEdgeControlTest {

    @Test
    fun leftEdgeControlsBrightnessAndRightEdgeControlsVolume() {
        assertEquals(PlayerEdgeControl.BRIGHTNESS, edgeControlAt(x = 0f, width = 1_000f))
        assertEquals(PlayerEdgeControl.BRIGHTNESS, edgeControlAt(x = 300f, width = 1_000f))
        assertEquals(PlayerEdgeControl.VOLUME, edgeControlAt(x = 700f, width = 1_000f))
        assertEquals(PlayerEdgeControl.VOLUME, edgeControlAt(x = 1_000f, width = 1_000f))
    }

    @Test
    fun centerDoesNotClaimPlaybackGestures() {
        assertNull(edgeControlAt(x = 500f, width = 1_000f))
        assertNull(edgeControlAt(x = 0f, width = 0f))
    }
}
