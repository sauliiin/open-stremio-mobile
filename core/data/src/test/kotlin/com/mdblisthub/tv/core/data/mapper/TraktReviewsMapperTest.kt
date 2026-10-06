package com.mdblisthub.tv.core.data.mapper

import com.mdblisthub.tv.core.model.MediaType
import com.mdblisthub.tv.core.model.ReviewProvider
import com.mdblisthub.tv.core.network.dto.MdbInfoDto
import com.mdblisthub.tv.core.network.dto.MdbReviewDto
import com.mdblisthub.tv.core.network.dto.TmdbDetailDto
import com.mdblisthub.tv.core.network.dto.TraktCommentDto
import com.mdblisthub.tv.core.network.dto.TraktCommentUserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TraktReviewsMapperTest {
    private val long = "This one holds up on a rewatch and the ending still lands with the same weight it had the first time"

    @Test
    fun `direct Trakt comments replace the MDBList mirror, minus spoilers and shouts`() {
        val detail = buildDetailEntity(
            type = MediaType.MOVIE,
            tmdbId = 1,
            tmdb = TmdbDetailDto(title = "Title"),
            info = MdbInfoDto(
                reviews = listOf(
                    MdbReviewDto(author = "TMDB author", content = "TMDB review", providerId = 2),
                    MdbReviewDto(author = "Mirrored", content = "Mirror copy", providerId = 1),
                ),
            ),
            omdb = null,
            now = 0,
            traktComments = listOf(
                TraktCommentDto(comment = long, userRating = 9, user = TraktCommentUserDto("jenn", "Jenn")),
                TraktCommentDto(comment = "11/10", user = TraktCommentUserDto("short")),
                TraktCommentDto(comment = long, spoiler = true, user = TraktCommentUserDto("flagged")),
                TraktCommentDto(comment = "$long [spoiler]he dies[/spoiler]", user = TraktCommentUserDto("inline")),
                TraktCommentDto(comment = long, user = TraktCommentUserDto("noname", name = "")),
            ),
        )

        assertEquals(listOf("TMDB author", "Jenn", "noname"), detail.reviews.map { it.author })
        assertEquals(ReviewProvider.TMDB, detail.reviews[0].provider)
        assertEquals(ReviewProvider.TRAKT, detail.reviews[1].provider)
        assertEquals(9.0, detail.reviews[1].rating)
        assertNull(detail.reviews.find { it.content == "Mirror copy" })
    }

    @Test
    fun `falls back to the MDBList mirror when Trakt leaves nothing to show`() {
        val mirror = MdbInfoDto(
            reviews = listOf(MdbReviewDto(author = "Mirrored", content = "Mirror copy", providerId = 1)),
        )
        listOf(null, emptyList(), listOf(TraktCommentDto(comment = "Great movie"))).forEach { comments ->
            val detail = buildDetailEntity(
                type = MediaType.MOVIE,
                tmdbId = 1,
                tmdb = TmdbDetailDto(title = "Title"),
                info = mirror,
                omdb = null,
                now = 0,
                traktComments = comments,
            )
            assertEquals(listOf("Mirror copy"), detail.reviews.map { it.content })
            assertEquals(ReviewProvider.TRAKT, detail.reviews.single().provider)
        }
    }
}
