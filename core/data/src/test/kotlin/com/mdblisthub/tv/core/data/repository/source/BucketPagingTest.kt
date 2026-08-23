package com.mdblisthub.tv.core.data.repository.source

import com.mdblisthub.tv.core.network.dto.BucketEntryDto
import com.mdblisthub.tv.core.network.dto.BucketPaginationDto
import com.mdblisthub.tv.core.network.dto.BucketResponseDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BucketPagingTest {
    @Test
    fun `follows cursors and merges every watched page`() = runBlocking {
        val asked = mutableListOf<String?>()
        val pages = mapOf(
            null to BucketResponseDto(
                movies = listOf(BucketEntryDto(id = 1)),
                pagination = BucketPaginationDto(hasMore = true, nextCursor = "two"),
            ),
            "two" to BucketResponseDto(movies = listOf(BucketEntryDto(id = 2))),
        )

        val merged = readAllBucketPages { cursor ->
            asked += cursor
            pages.getValue(cursor)
        }

        assertEquals(listOf(null, "two"), asked)
        assertEquals(listOf(1, 2), merged.movies.map { it.id })
        assertNull(merged.pagination)
    }

    @Test
    fun `malformed endless pagination stops at the safety ceiling`() = runBlocking {
        var calls = 0
        readAllBucketPages {
            calls++
            BucketResponseDto(
                pagination = BucketPaginationDto(hasMore = true, nextCursor = "same"),
            )
        }

        assertEquals(BUCKET_MAX_PAGES, calls)
    }
}
