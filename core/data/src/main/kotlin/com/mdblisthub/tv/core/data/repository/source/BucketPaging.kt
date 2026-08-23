package com.mdblisthub.tv.core.data.repository.source

import com.mdblisthub.tv.core.network.MdblistApi
import com.mdblisthub.tv.core.network.dto.BucketResponseDto

/** Reads an MDBList library bucket to the end and returns one merged result. */
internal suspend fun MdblistApi.wholeBucket(url: String, apiKey: String): BucketResponseDto =
    readAllBucketPages { cursor -> bucket(url, apiKey, BUCKET_PAGE, cursor) }

/** Cursor loop kept separate so pagination and its safety ceiling are testable. */
internal suspend fun readAllBucketPages(
    fetchPage: suspend (cursor: String?) -> BucketResponseDto,
): BucketResponseDto {
    var page = fetchPage(null)
    val movies = page.movies.toMutableList()
    val shows = page.shows.toMutableList()
    val episodes = page.episodes.toMutableList()

    var fetched = 1
    while (fetched < BUCKET_MAX_PAGES) {
        val pagination = page.pagination ?: break
        if (!pagination.hasMore) break
        val cursor = pagination.nextCursor?.takeIf { it.isNotBlank() } ?: break

        page = fetchPage(cursor)
        movies += page.movies
        shows += page.shows
        episodes += page.episodes
        fetched++
    }

    return BucketResponseDto(movies = movies, shows = shows, episodes = episodes)
}

internal const val BUCKET_PAGE = 1_000
internal const val BUCKET_MAX_PAGES = 5
