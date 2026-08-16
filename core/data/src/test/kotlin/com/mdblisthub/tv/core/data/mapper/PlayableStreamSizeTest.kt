package com.mdblisthub.tv.core.data.mapper

import com.mdblisthub.tv.core.model.PlayableStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayableStreamSizeTest {
    @Test
    fun `parses megabytes and decimal gigabytes`() {
        assertEquals(606L * 1024 * 1024, stream("606 MB").sizeBytes)
        assertEquals((4.5 * 1024 * 1024 * 1024).toLong(), stream("4.5 GB").sizeBytes)
    }

    @Test
    fun `accepts decimal comma and leaves unknown sizes manual`() {
        assertEquals((1.25 * 1024 * 1024 * 1024).toLong(), stream("1,25 GB").sizeBytes)
        assertNull(stream(null).sizeBytes)
        assertNull(stream("unknown").sizeBytes)
    }

    private fun stream(size: String?) = PlayableStream(
        key = "test",
        addon = "test",
        title = "test",
        size = size,
        url = "https://example.com/movie.mkv",
    )
}
