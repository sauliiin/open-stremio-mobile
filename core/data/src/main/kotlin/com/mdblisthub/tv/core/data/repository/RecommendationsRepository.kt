package com.mdblisthub.tv.core.data.repository

import com.mdblisthub.tv.core.data.SessionStore
import com.mdblisthub.tv.core.data.repository.source.wholeBucket
import com.mdblisthub.tv.core.model.CoreText
import com.mdblisthub.tv.core.model.MediaItem
import com.mdblisthub.tv.core.model.MediaType
import com.mdblisthub.tv.core.model.RecommendationRow
import com.mdblisthub.tv.core.model.TmdbImages
import com.mdblisthub.tv.core.network.ApiConfig
import com.mdblisthub.tv.core.network.MdblistApi
import com.mdblisthub.tv.core.network.TmdbApi
import com.mdblisthub.tv.core.network.dto.BucketEntryDto
import com.mdblisthub.tv.core.network.dto.BucketResponseDto
import com.mdblisthub.tv.core.network.dto.TmdbSearchResultDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/** Personalised recommendation rows and the home-screen spotlight pool. */
class RecommendationsRepository(
    private val mdblistApi: MdblistApi,
    private val tmdbApi: TmdbApi,
    private val media: MediaRepository,
    private val session: SessionStore,
) {
    suspend fun becauseYouWatched(): List<RecommendationRow> = coroutineScope {
        val key = session.currentKey()
        if (key.isBlank()) return@coroutineScope emptyList()

        val watched = runCatching { watched(key) }.getOrNull().orEmpty()
        if (watched.isEmpty()) return@coroutineScope emptyList()

        val alreadyWatched = watched.mapTo(HashSet()) { it.key() }
        watched.take(SEED_ROWS)
            .map { (type, tmdbId) -> async { rowFor(type, tmdbId, alreadyWatched) } }
            .mapNotNull { it.await() }
            .filter { it.items.size >= MIN_ROW_SIZE }
    }

    /**
     * Builds one shuffled hero pool from recent-watch recommendations.
     * Only unwatched films with reliable ratings and both forms of artwork
     * survive, so every result is suitable for a full-bleed mobile hero.
     */
    suspend fun spotlight(): List<MediaItem> {
        val key = session.currentKey()
        val watched = if (key.isBlank()) emptyList() else runCatching { watched(key) }.getOrNull().orEmpty()
        val personalized = buildSpotlight(watched) { type, tmdbId ->
            runCatching {
                tmdbApi.recommendations(
                    type.tmdb,
                    tmdbId,
                    ApiConfig.TMDB_KEY,
                    ApiConfig.LANGUAGE,
                ).results
            }.getOrNull().orEmpty()
        }
        if (personalized.isNotEmpty()) return personalized

        return runCatching {
            tmdbApi.discoverMovie(
                ApiConfig.TMDB_KEY,
                ApiConfig.LANGUAGE,
                keywords = "",
            ).results
                .filter { it.voteAverage > MIN_SPOTLIGHT_SCORE }
                .filter { it.voteCount >= MIN_SPOTLIGHT_VOTES }
                .filter { !it.backdropPath.isNullOrBlank() && !it.posterPath.isNullOrBlank() }
                .map { it.toMediaItem(MediaType.MOVIE) }
                .distinctBy { it.tmdbId }
                .shuffled()
        }.getOrDefault(emptyList())
    }

    /** The complete watched bucket, most recent first, shared by both queries. */
    private suspend fun watched(apiKey: String): List<Pair<MediaType, Int>> = watchedMutex.withLock {
        val cached = watchedCache
        if (cached != null && System.currentTimeMillis() - watchedFetchedAt < WATCHED_TTL_MS) {
            return@withLock cached
        }

        val resolved = mdblistApi
            .wholeBucket("${MDBLIST_ROOT}sync/watched", apiKey)
            .watchedTitles()
        watchedCache = resolved
        watchedFetchedAt = System.currentTimeMillis()
        resolved
    }

    private val watchedMutex = Mutex()
    private var watchedCache: List<Pair<MediaType, Int>>? = null
    private var watchedFetchedAt = 0L

    private suspend fun rowFor(
        type: MediaType,
        tmdbId: Int,
        alreadyWatched: Set<String>,
    ): RecommendationRow? {
        media.ensureDetail(type, tmdbId)
        val seedTitle = media.observeDetail(type, tmdbId).first()?.title ?: return null
        val results = runCatching {
            tmdbApi.recommendations(type.tmdb, tmdbId, ApiConfig.TMDB_KEY, ApiConfig.LANGUAGE).results
        }.getOrNull() ?: return null

        val items = results
            .filter { !it.posterPath.isNullOrBlank() }
            .map { it.toMediaItem(type) }
            .filter { it.key() !in alreadyWatched }
            .take(PER_ROW)

        if (items.isEmpty()) return null
        return RecommendationRow(seedTitle = seedTitle, items = items)
    }
}

internal fun BucketResponseDto.watchedTitles(): List<Pair<MediaType, Int>> {
    val movieEntries = movies.map { it to MediaType.MOVIE }
    val showEntries = shows.map { it to MediaType.SHOW }
    return (movieEntries + showEntries)
        .sortedByDescending { (entry, _) -> entry.lastWatchedAt.orEmpty() }
        .mapNotNull { (entry, type) -> entry.tmdbId()?.let { type to it } }
}

/** Pure spotlight rules, separated from account, database and network state. */
internal suspend fun buildSpotlight(
    watched: List<Pair<MediaType, Int>>,
    recommendationsFor: suspend (MediaType, Int) -> List<TmdbSearchResultDto>,
): List<MediaItem> {
    if (watched.isEmpty()) return emptyList()
    val alreadyWatched = watched.mapTo(HashSet()) { it.key() }

    val recent = spotlightFrom(watched.take(SEED_ROWS), alreadyWatched, recommendationsFor)
    if (recent.isNotEmpty()) return recent

    // TMDB recommendations are same-type. If the five latest seeds are all
    // series, reach back to the latest films before giving up on a film hero.
    val movieSeeds = watched.filter { (type, _) -> type == MediaType.MOVIE }.take(SEED_ROWS)
    return spotlightFrom(movieSeeds, alreadyWatched, recommendationsFor)
}

private suspend fun spotlightFrom(
    seeds: List<Pair<MediaType, Int>>,
    alreadyWatched: Set<String>,
    recommendationsFor: suspend (MediaType, Int) -> List<TmdbSearchResultDto>,
): List<MediaItem> = coroutineScope {
    seeds
        .map { (type, tmdbId) -> async { recommendationsFor(type, tmdbId) } }
        .flatMap { it.await() }
        .filter { it.voteAverage > MIN_SPOTLIGHT_SCORE }
        .filter { it.voteCount >= MIN_SPOTLIGHT_VOTES }
        .filter { !it.backdropPath.isNullOrBlank() && !it.posterPath.isNullOrBlank() }
        .map { it.toMediaItem(MediaType.MOVIE) }
        .filter { it.type == MediaType.MOVIE }
        .filter { it.key() !in alreadyWatched }
        .distinctBy { it.tmdbId }
        .shuffled()
}

private fun BucketEntryDto.tmdbId(): Int? = movie?.ids?.tmdb ?: show?.ids?.tmdb ?: ids?.tmdb ?: id
private fun MediaItem.key() = "${type.mdblist}:$tmdbId"
private fun Pair<MediaType, Int>.key() = "${first.mdblist}:$second"

private fun TmdbSearchResultDto.toMediaItem(fallbackType: MediaType): MediaItem {
    val type = mediaType?.let { MediaType.fromTmdb(it) } ?: fallbackType
    val date = releaseDate?.takeIf { it.isNotBlank() } ?: firstAirDate?.takeIf { it.isNotBlank() }
    return MediaItem(
        tmdbId = id,
        type = type,
        title = title ?: name ?: CoreText.untitled,
        year = date?.take(4)?.toIntOrNull(),
        posterUrl = TmdbImages.url(posterPath, TmdbImages.POSTER_CARD),
        backdropUrl = TmdbImages.url(backdropPath, TmdbImages.BACKDROP_FANART),
        score = voteAverage.takeIf { it > 0 }?.let { (it * 10).roundToInt() },
    )
}

private const val MDBLIST_ROOT = "https://api.mdblist.com/"
private const val WATCHED_TTL_MS = 5 * 60 * 1_000L
private const val SEED_ROWS = 5
private const val PER_ROW = 20
private const val MIN_ROW_SIZE = 4
private const val MIN_SPOTLIGHT_SCORE = 6.0
private const val MIN_SPOTLIGHT_VOTES = 50
