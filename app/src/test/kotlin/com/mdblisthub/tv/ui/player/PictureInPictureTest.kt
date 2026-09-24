package com.mdblisthub.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window shape, which is the one part of picture-in-picture that fails
 * invisibly.
 *
 * `setAspectRatio` throws on anything outside roughly 1:2.39–2.39:1, and the
 * releases that reach that boundary are exactly the ones a viewer is most
 * likely to be watching in a floating window — an ultra-wide remux, or a
 * vertical clip. Nothing about the failure is visible until it happens, and
 * then it is a crash on the home gesture.
 */
class PictureInPictureTest {

    private fun ratioOf(width: Int, height: Int): Double {
        val shape = PictureInPicture.aspectRatio(width, height)!!
        return shape.numerator.toDouble() / shape.denominator
    }

    /** Android's own bounds, with a hair of tolerance for the integer scaling. */
    private fun assertLegal(width: Int, height: Int) {
        val ratio = ratioOf(width, height)
        assertTrue("$width x $height gave $ratio, outside Android's range", ratio in (1.0 / 2.39)..2.39)
    }

    @Test
    fun `a decoder that has not reported a size yet has no shape`() {
        assertNull(PictureInPicture.aspectRatio(0, 0))
        assertNull(PictureInPicture.aspectRatio(1920, 0))
        assertNull(PictureInPicture.aspectRatio(-1920, 1080))
    }

    @Test
    fun `ordinary shapes pass through`() {
        assertEquals(16.0 / 9, ratioOf(1920, 1080), 0.001)
        assertEquals(4.0 / 3, ratioOf(640, 480), 0.001)
        assertEquals(2.35, ratioOf(2350, 1000), 0.001)
    }

    /**
     * The case the clamp exists for. A 2.76:1 Ultra Panavision transfer is a
     * real thing to watch, and passing its true ratio through is a crash.
     */
    @Test
    fun `an ultra-wide film is clamped instead of thrown`() {
        assertLegal(2760, 1000)
        assertTrue("must actually be narrowed", ratioOf(2760, 1000) < 2.76)
        assertLegal(3840, 800)
    }

    /**
     * And the other end. An ordinary 9:16 portrait clip is 0.5625, comfortably
     * inside the range and left alone — the clamp is for the extreme case, a
     * banner or a mis-tagged stream far taller than it is wide.
     */
    @Test
    fun `an extreme vertical shape is clamped instead of thrown`() {
        assertEquals("a portrait clip is legal as it is", 0.5625, ratioOf(1080, 1920), 0.001)

        assertLegal(100, 1000)
        assertTrue("must actually be widened", ratioOf(100, 1000) > 100.0 / 1000)
    }

    /** Whatever comes out of a decoder, the window can be built from it. */
    @Test
    fun `every plausible frame size yields a legal shape`() {
        val sizes = listOf(
            3840 to 2160, 1920 to 1080, 1280 to 720, 720 to 576, 640 to 480,
            2048 to 858, 4096 to 1716, 1920 to 804, 1920 to 1040,
            1 to 1, 10000 to 1, 1 to 10000, 1080 to 1350,
        )
        for ((width, height) in sizes) assertLegal(width, height)
    }

    /** A denominator of zero would be a divide-by-zero inside Rational. */
    @Test
    fun `the shape is always a usable fraction`() {
        for ((width, height) in listOf(1 to 10000, 10000 to 1, 1920 to 1080)) {
            val shape = PictureInPicture.aspectRatio(width, height)!!
            assertTrue("numerator must be positive", shape.numerator > 0)
            assertTrue("denominator must be positive", shape.denominator > 0)
        }
    }
}
