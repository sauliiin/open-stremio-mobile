package com.mdblisthub.tv.ui.home

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.mdblisthub.tv.core.ui.theme.HubColors

/** Small in-app trailer preview that never changes the hero or shelf layout. */
@Composable
internal fun FocusedTrailerPip(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val url by viewModel.focusedTrailerUrl.collectAsStateWithLifecycle()
    url?.let { TrailerPipSurface(url = it, modifier = modifier) }
}

@OptIn(UnstableApi::class)
@Composable
private fun TrailerPipSurface(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var firstFrameRendered by remember(url) { mutableStateOf(false) }
    val previewAlpha by animateFloatAsState(
        targetValue = if (firstFrameRendered) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "trailer-pip-fade",
    )
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_OFF
            volume = TRAILER_VOLUME
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }

            override fun onPlayerError(error: PlaybackException) {
                firstFrameRendered = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .alpha(previewAlpha)
            .shadow(12.dp, PIP_SHAPE)
            .clip(PIP_SHAPE)
            .background(Color.Black)
            .border(1.dp, HubColors.Border.copy(alpha = 0.9f), PIP_SHAPE),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                TextureView(viewContext).also(player::setVideoTextureView)
            },
            onRelease = { texture -> player.clearVideoTextureView(texture) },
        )
    }
}

private val PIP_SHAPE = RoundedCornerShape(14.dp)
private const val TRAILER_VOLUME = 0.65f
