package com.mdblisthub.tv.ui.home

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@Composable
internal fun FocusedHeroTrailer(viewModel: HomeViewModel) {
    val url by viewModel.focusedTrailerUrl.collectAsStateWithLifecycle()
    url?.let { HeroTrailerSurface(it, Modifier.fillMaxSize()) }
}

@OptIn(UnstableApi::class)
@Composable
private fun HeroTrailerSurface(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var ready by remember(url) { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (ready) 1f else 0f,
        animationSpec = tween(900),
        label = "home-trailer-fade",
    )
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 0.35f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { ready = true }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        modifier = modifier.alpha(alpha),
        factory = { ctx -> TextureView(ctx).also(player::setVideoTextureView) },
        onRelease = { player.clearVideoTextureView(it) },
    )
}
