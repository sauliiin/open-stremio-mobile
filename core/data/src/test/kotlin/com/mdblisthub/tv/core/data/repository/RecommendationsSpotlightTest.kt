package com.mdblisthub.tv.core.data.repository

import com.mdblisthub.tv.core.model.MediaType
import com.mdblisthub.tv.core.network.dto.BucketEntryDto
import com.mdblisthub.tv.core.network.dto.BucketTitleDto
import com.mdblisthub.tv.core.network.dto.MdbIdsDto
import com.mdblisthub.tv.core.network.dto.TmdbSearchResultDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationsSpotlightTest {
    private fun recommendation(
        id: Int,
        score: Double = 8.0,
        votes: Int = 500,
        mediaType: String? = "movie",
    ) = TmdbSearchResultDto(
        id = id,
        mediaType = mediaType,
        title = "Film $id",
        posterPath = "/poster.jpg",
        backdropPath = "/backdrop.jpg",
        voteAverage = score,
        voteCount = votes,
    )

    @Test
    fun `spotlight excludes watched and weakly rated recommendations`() = runBlocking {
        val watched = listOf(MediaType.MOVIE to 10, MediaType.MOVIE to 20)
        val result = buildSpotlight(watched) { _, _ ->
            listOf(
                recommendation(10),
                recommendation(30, score = 5.9),
                recommendation(40, votes = 12),
                recommendation(50),
            )
        }

        assertEquals(listOf(50), result.map { it.tmdbId })
    }

    @Test
    fun `series-only recent seeds fall back to the latest movie seed`() = runBlocking {
        val watched = buildList {
            repeat(5) { add(MediaType.SHOW to 100 + it) }
            add(MediaType.MOVIE to 200)
        }
        val result = buildSpotlight(watched) { type, _ ->
            if (type == MediaType.SHOW) {
                listOf(recommendation(300, mediaType = "tv"))
            } else {
                listOf(recommendation(400))
            }
        }

        assertEquals(listOf(400), result.map { it.tmdbId })
    }

    @Test
    fun `empty history has no spotlight`() = runBlocking {
        assertTrue(buildSpotlight(emptyList()) { _, _ -> emptyList() }.isEmpty())
    }

    @Test
    fun `watch history is ordered by last watched timestamp`() {
        val bucket = com.mdblisthub.tv.core.network.dto.BucketResponseDto(
            movies = listOf(
                BucketEntryDto(
                    lastWatchedAt = "2024-01-01T00:00:00Z",
                    movie = BucketTitleDto(ids = MdbIdsDto(tmdb = 1)),
                ),
                BucketEntryDto(
                    lastWatchedAt = "2026-01-01T00:00:00Z",
                    movie = BucketTitleDto(ids = MdbIdsDto(tmdb = 2)),
                ),
            ),
        )

        assertEquals(listOf(2, 1), bucket.watchedTitles().map { it.second })
    }
}
