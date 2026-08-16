package com.mdblisthub.tv.player

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.DownloaderFactory
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import com.mdblisthub.tv.core.model.MediaType
import com.mdblisthub.tv.core.model.PlayableStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.Call
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

/** The durable state shown by the detail screen for one offline title. */
enum class OfflineStatus { QUEUED, DOWNLOADING, COMPLETED, FAILED, REMOVING, STOPPED }

/**
 * Everything needed to identify and later play a download without consulting
 * either Room or the network. It travels inside Media3's DownloadRequest, so
 * the registration and the cached bytes are committed atomically.
 */
data class OfflineMetadata(
    val type: MediaType,
    val tmdbId: Int,
    val season: Int?,
    val episode: Int?,
    val title: String,
    val backdropUrl: String?,
    val stream: PlayableStream,
) {
    val id: String get() = OfflineDownloads.id(type, tmdbId, season, episode)

    companion object {
        internal fun decode(bytes: ByteArray): OfflineMetadata? = decodeOfflineMetadata(bytes)
    }
}

data class OfflineDownload(
    val metadata: OfflineMetadata,
    val status: OfflineStatus,
    val percentDownloaded: Float,
    val bytesDownloaded: Long,
    val contentLength: Long,
    internal val request: DownloadRequest,
) {
    val completed: Boolean get() = status == OfflineStatus.COMPLETED
}

/**
 * Permanent Media3 download cache.
 *
 * This deliberately lives under filesDir and uses a NoOpCacheEvictor. The
 * normal playback cache lives under cacheDir and may be reclaimed; treating
 * those temporary spans as an offline copy would make the promise disappear
 * under storage pressure.
 */
@OptIn(UnstableApi::class)
object OfflineDownloads : DownloadManager.Listener {
    private const val DIRECTORY = "offline-media"
    private const val INDEX_SUFFIX = "offline"
    private const val ID_PREFIX = "offline:"

    @Volatile private var initialized = false
    private lateinit var appContext: Context
    private lateinit var databaseProvider: StandaloneDatabaseProvider
    private lateinit var cache: SimpleCache
    private lateinit var manager: DownloadManager
    private lateinit var callFactory: Call.Factory
    private val segmentExecutor: ExecutorService = Executors.newFixedThreadPool(3)
    private val _downloads = MutableStateFlow<Map<String, OfflineDownload>>(emptyMap())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressTickerScheduled = false
    private val progressTicker = object : Runnable {
        override fun run() {
            if (!initialized) {
                progressTickerScheduled = false
                return
            }

            // DownloadManager.Listener reports lifecycle changes, but not one
            // callback for every byte counter update. Read the manager's live
            // DownloadProgress objects while work is active so Compose does
            // not remain stuck on the percentage from STATE_DOWNLOADING.
            val current = manager.currentDownloads
            if (current.isNotEmpty()) {
                _downloads.value = _downloads.value.toMutableMap().apply {
                    current.forEach { download ->
                        download.toOfflineDownload()?.let { put(download.request.id, it) }
                    }
                }
            }

            if (current.any { it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED }) {
                mainHandler.postDelayed(this, PROGRESS_UPDATE_MS)
            } else {
                progressTickerScheduled = false
            }
        }
    }

    @Synchronized
    fun initialize(context: Context, playbackCallFactory: Call.Factory) {
        if (initialized) return
        appContext = context.applicationContext
        callFactory = playbackCallFactory
        databaseProvider = StandaloneDatabaseProvider(appContext)
        cache = SimpleCache(
            File(appContext.filesDir, DIRECTORY),
            NoOpCacheEvictor(),
            databaseProvider,
        )
        val index = DefaultDownloadIndex(databaseProvider, INDEX_SUFFIX)
        manager = DownloadManager(
            appContext,
            index,
            HeaderAwareDownloaderFactory(cache, callFactory, segmentExecutor),
        ).apply {
            maxParallelDownloads = 2
            addListener(this@OfflineDownloads)
            resumeDownloads()
        }
        initialized = true
        refreshAll()
    }

    fun id(type: MediaType, tmdbId: Int, season: Int?, episode: Int?): String =
        "$ID_PREFIX${type.mdblist}:$tmdbId:${season ?: 0}:${episode ?: 0}"

    fun observe(type: MediaType, tmdbId: Int, season: Int?, episode: Int?): Flow<OfflineDownload?> {
        val id = id(type, tmdbId, season, episode)
        return _downloads.map { it[id] }.distinctUntilChanged()
    }

    /** Reads the durable index, so a cold player never races manager initialisation. */
    suspend fun completed(
        type: MediaType,
        tmdbId: Int,
        season: Int?,
        episode: Int?,
    ): OfflineDownload? = withContext(Dispatchers.IO) {
        ensureInitialized()
        runCatching { manager.downloadIndex.getDownload(id(type, tmdbId, season, episode)) }
            .getOrNull()
            ?.toOfflineDownload()
            ?.takeIf { it.completed }
    }

    fun enqueue(context: Context, metadata: OfflineMetadata) {
        ensureInitialized()
        val url = metadata.stream.url?.takeIf { it.isNotBlank() } ?: return
        val uri = Uri.parse(url)
        val requestBuilder = DownloadRequest.Builder(metadata.id, uri)
            .setData(metadata.encode())

        val contentType = Util.inferContentType(uri)
        Util.getAdaptiveMimeTypeForContentType(contentType)?.let(requestBuilder::setMimeType)
        // Adaptive requests use their segment URIs as cache identities and do
        // not allow a custom key. Progressive links can use the stable offline
        // id instead of a signed URL that may expire tomorrow.
        if (contentType == C.CONTENT_TYPE_OTHER) requestBuilder.setCustomCacheKey(metadata.id)

        DownloadService.sendAddDownload(
            context.applicationContext,
            OfflineDownloadService::class.java,
            requestBuilder.build(),
            /* foreground = */ true,
        )
    }

    fun remove(context: Context, type: MediaType, tmdbId: Int, season: Int?, episode: Int?) =
        remove(context, id(type, tmdbId, season, episode))

    fun remove(context: Context, id: String) {
        ensureInitialized()
        DownloadService.sendRemoveDownload(
            context.applicationContext,
            OfflineDownloadService::class.java,
            id,
            /* foreground = */ true,
        )
    }

    internal fun downloadManager(): DownloadManager {
        ensureInitialized()
        return manager
    }

    /** A cache-only source: a miss is an error, never an accidental network read. */
    internal fun cacheOnlyDataSourceFactory(): DataSource.Factory {
        ensureInitialized()
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(null)
            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)
    }

    override fun onInitialized(downloadManager: DownloadManager) {
        refreshAll()
        ensureProgressTicker()
    }

    override fun onDownloadChanged(
        downloadManager: DownloadManager,
        download: Download,
        finalException: Exception?,
    ) {
        val item = download.toOfflineDownload() ?: return
        _downloads.value = _downloads.value.toMutableMap().apply { put(download.request.id, item) }
        ensureProgressTicker()
    }

    override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
        _downloads.value = _downloads.value - download.request.id
    }

    private fun refreshAll() {
        if (!initialized) return
        val loaded = linkedMapOf<String, OfflineDownload>()
        runCatching {
            manager.downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.download.toOfflineDownload()?.let { loaded[it.metadata.id] = it }
                }
            }
        }
        _downloads.value = loaded
    }

    private fun Download.toOfflineDownload(): OfflineDownload? {
        if (!request.id.startsWith(ID_PREFIX)) return null
        val metadata = OfflineMetadata.decode(request.data) ?: return null
        val status = when (state) {
            Download.STATE_QUEUED, Download.STATE_RESTARTING -> OfflineStatus.QUEUED
            Download.STATE_DOWNLOADING -> OfflineStatus.DOWNLOADING
            Download.STATE_COMPLETED -> OfflineStatus.COMPLETED
            Download.STATE_FAILED -> OfflineStatus.FAILED
            Download.STATE_REMOVING -> OfflineStatus.REMOVING
            else -> OfflineStatus.STOPPED
        }
        val expectedBytes = contentLength.takeIf { it > 0 }
            ?: metadata.stream.sizeBytes
        val calculatedPercent = expectedBytes
            ?.takeIf { it > 0 && bytesDownloaded > 0 }
            ?.let { bytesDownloaded * 100f / it }
        val reportedPercent = percentDownloaded.takeIf { it >= 0f }
        return OfflineDownload(
            metadata = metadata,
            status = status,
            percentDownloaded = max(reportedPercent ?: 0f, calculatedPercent ?: 0f)
                .coerceIn(0f, 100f),
            bytesDownloaded = bytesDownloaded,
            contentLength = expectedBytes ?: C.LENGTH_UNSET.toLong(),
            request = request,
        )
    }

    private fun ensureProgressTicker() {
        if (progressTickerScheduled) return
        progressTickerScheduled = true
        mainHandler.post(progressTicker)
    }

    private fun ensureInitialized() = check(initialized) {
        "OfflineDownloads must be initialized from Application.onCreate"
    }

    private const val PROGRESS_UPDATE_MS = 500L
}

/** Supplies the request's own proxy headers to its manifest and every segment. */
@OptIn(UnstableApi::class)
private class HeaderAwareDownloaderFactory(
    private val cache: SimpleCache,
    private val callFactory: Call.Factory,
    private val executor: ExecutorService,
) : DownloaderFactory {
    override fun createDownloader(request: DownloadRequest): Downloader {
        val headers = OfflineMetadata.decode(request.data)?.stream?.headers.orEmpty()
        val upstream = OkHttpDataSource.Factory(callFactory).apply {
            setDefaultRequestProperties(headers)
        }
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
        return DefaultDownloaderFactory(cacheFactory, executor).createDownloader(request)
    }
}

private fun OfflineMetadata.encode(): ByteArray {
    val streamJson = JSONObject()
        .put("key", stream.key)
        .put("addon", stream.addon)
        .put("title", stream.title)
        .put("detail", stream.detail)
        .put("quality", stream.quality)
        .put("size", stream.size)
        .put("url", stream.url)
        .put("filename", stream.filename)
        .put("headers", JSONObject(stream.headers))
    return JSONObject()
        .put("type", type.mdblist)
        .put("tmdbId", tmdbId)
        .put("season", season)
        .put("episode", episode)
        .put("title", title)
        .put("backdropUrl", backdropUrl)
        .put("stream", streamJson)
        .toString()
        .toByteArray(Charsets.UTF_8)
}

/** Kept outside the data class body so the JSON format stays an implementation detail. */
private fun decodeOfflineMetadata(bytes: ByteArray): OfflineMetadata? = runCatching {
    val root = JSONObject(bytes.toString(Charsets.UTF_8))
    val streamJson = root.getJSONObject("stream")
    val headersJson = streamJson.optJSONObject("headers") ?: JSONObject()
    val headers = buildMap {
        headersJson.keys().forEach { key -> put(key, headersJson.getString(key)) }
    }
    OfflineMetadata(
        type = MediaType.parse(root.getString("type")),
        tmdbId = root.getInt("tmdbId"),
        season = root.optInt("season").takeIf { root.has("season") && !root.isNull("season") && it > 0 },
        episode = root.optInt("episode").takeIf { root.has("episode") && !root.isNull("episode") && it > 0 },
        title = root.optString("title"),
        backdropUrl = root.optString("backdropUrl").takeIf { it.isNotBlank() && it != "null" },
        stream = PlayableStream(
            key = streamJson.optString("key"),
            addon = streamJson.optString("addon"),
            title = streamJson.optString("title"),
            detail = streamJson.optString("detail").takeIf { it.isNotBlank() && it != "null" },
            quality = streamJson.optString("quality").takeIf { it.isNotBlank() && it != "null" },
            size = streamJson.optString("size").takeIf { it.isNotBlank() && it != "null" },
            url = streamJson.optString("url").takeIf { it.isNotBlank() && it != "null" },
            filename = streamJson.optString("filename").takeIf { it.isNotBlank() && it != "null" },
            headers = headers,
        ),
    )
}.getOrNull()

/** Android service that keeps long film downloads alive outside the UI. */
@OptIn(UnstableApi::class)
class OfflineDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.offline_notification_channel,
    0,
) {
    private val notificationHelper by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }
    private val platformScheduler by lazy { PlatformScheduler(this, SCHEDULER_JOB_ID) }

    override fun getDownloadManager(): DownloadManager = OfflineDownloads.downloadManager()

    // Re-enters the service when connectivity returns or the process was
    // reclaimed, instead of leaving a queued film dormant until the app opens.
    override fun getScheduler(): Scheduler = platformScheduler

    override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int): Notification {
        val label = downloads.firstOrNull()
            ?.request
            ?.data
            ?.let(::decodeOfflineMetadata)
            ?.title
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.offline_notification_progress)
        return notificationHelper.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            launchPendingIntent(),
            label,
            downloads,
            notMetRequirements,
        )
    }

    private fun launchPendingIntent(): PendingIntent? = packageManager
        .getLaunchIntentForPackage(packageName)
        ?.let { intent ->
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

    private companion object {
        const val NOTIFICATION_ID = 4102
        const val SCHEDULER_JOB_ID = 4103
        const val CHANNEL_ID = "offline_downloads"
    }
}
