package com.mdblisthub.tv.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class SpotlightHeroIndicatorTest {

    @Test
    fun `active page traverses every slot before indicator window advances`() {
        repeat(7) { page ->
            assertEquals(0 until 7, spotlightIndicatorWindow(itemCount = 15, currentPage = page))
        }
        assertEquals(7 until 14, spotlightIndicatorWindow(itemCount = 15, currentPage = 7))
        assertEquals(14 until 15, spotlightIndicatorWindow(itemCount = 15, currentPage = 14))
    }

    @Test
    fun `small pools keep one indicator per hero`() {
        assertEquals(0 until 6, spotlightIndicatorWindow(itemCount = 6, currentPage = 4))
    }
}
