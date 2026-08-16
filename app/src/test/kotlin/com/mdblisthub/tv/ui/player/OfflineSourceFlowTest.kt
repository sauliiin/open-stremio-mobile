package com.mdblisthub.tv.ui.player

import com.mdblisthub.tv.core.model.PlayableStream
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSourceFlowTest {
    @Test
    fun `collects every source before automatic validation and preserves phases`() = runBlocking {
        val events = mutableListOf<String>()
        val smallFirst = stream("small-first", "4.90 GB")
        val large = stream("large", "5.01 GB")
        val smallLate = stream("small-late", "800 MB")

        runOfflineSourceFlow(
            candidates = flow {
                emit(smallFirst)
                events += "late-addon-start"
                emit(large)
                emit(smallLate)
                events += "discovery-complete"
            },
            validateAutomatic = { sources ->
                events += "automatic"
                assertEquals(listOf(smallFirst, smallLate), sources)
                false
            },
            validateFallback = { sources ->
                events += "fallback"
                assertEquals(listOf(large), sources)
            },
        )

        assertEquals(
            listOf("late-addon-start", "discovery-complete", "automatic", "fallback"),
            events,
        )
    }

    @Test
    fun `automatic success never opens fallback`() = runBlocking {
        var fallbackCalled = false

        runOfflineSourceFlow(
            candidates = flow {
                emit(stream("exact-limit", "5 GB"))
                emit(stream("unknown", null))
            },
            validateAutomatic = { sources ->
                assertEquals(listOf("exact-limit"), sources.map { it.key })
                true
            },
            validateFallback = { fallbackCalled = true },
        )

        assertFalse(fallbackCalled)
    }

    @Test
    fun `without eligible source validates the complete fallback once`() = runBlocking {
        var automaticCalled = false
        val fallback = listOf(stream("unknown", null), stream("large", "7.2 GB"))

        runOfflineSourceFlow(
            candidates = flow { fallback.forEach { emit(it) } },
            validateAutomatic = {
                automaticCalled = true
                false
            },
            validateFallback = { assertEquals(fallback, it) },
        )

        assertFalse(automaticCalled)
        assertTrue(fallback.isNotEmpty())
    }

    private fun stream(key: String, size: String?) = PlayableStream(
        key = key,
        addon = "test",
        title = key,
        size = size,
        url = "https://example.com/$key.mkv",
    )
}
