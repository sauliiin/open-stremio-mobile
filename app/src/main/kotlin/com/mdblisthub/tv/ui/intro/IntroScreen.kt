package com.mdblisthub.tv.ui.intro

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mdblisthub.tv.R
import kotlinx.coroutines.delay

/**
 * Full-screen, one-shot opening video shown over the app while it starts.
 *
 * A straight port of the TV build's own `IntroScreen`, minus the parts that
 * only made sense for a D-pad: there is no focus to request and no key event
 * to swallow here, since the video sits over a phone screen a thumb can just
 * tap through via the system back gesture — which [BackHandler] below is
 * already listening for.
 */
@OptIn(UnstableApi::class)
@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(
                MediaItem.fromUri(
                    "android.resource://${context.packageName}/${R.raw.intro}",
                ),
            )
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }

    BackHandler(onBack = onFinished)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onFinished()
            }

            override fun onPlayerError(error: PlaybackException) = onFinished()
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // A local file should become ready almost immediately. Do not leave the
    // viewer on black forever if a device cannot decode this particular MP4.
    LaunchedEffect(player) {
        delay(READY_TIMEOUT_MS)
        if (player.playbackState != Player.STATE_READY) onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    this.player = player
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                }
            },
            onRelease = { view -> view.player = null },
        )
    }
}

private const val READY_TIMEOUT_MS = 5_000L
