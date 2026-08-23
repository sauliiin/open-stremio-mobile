package com.mdblisthub.tv.ui.home

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.mdblisthub.tv.R
import com.mdblisthub.tv.core.model.MediaDetail
import com.mdblisthub.tv.core.model.MediaItem
import com.mdblisthub.tv.core.model.MediaType
import com.mdblisthub.tv.core.ui.component.HubSkeletonBlock
import com.mdblisthub.tv.core.ui.theme.HubColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPOTLIGHT_AUTO_SCROLL_MS = 8_000L

/**
 * A mobile-first full-bleed carousel following NuvioMobile's home hero:
 * backdrop, deep bottom fade, centred logo/copy, white details pill and dots.
 */
@Composable
internal fun NuvioSpotlightHero(
    viewModel: HomeViewModel,
    onOpen: (MediaItem) -> Unit,
    requestInitialFocus: Boolean,
    onInitialFocusHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by viewModel.spotlight.collectAsStateWithLifecycle()
    val detail by viewModel.spotlightDetail.collectAsStateWithLifecycle()
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    val buttonFocusRequester = remember { FocusRequester() }
    val heroHeight = nuvioSpotlightHeroHeight()

    LaunchedEffect(pagerState.currentPage, items.size) {
        viewModel.selectSpotlight(pagerState.currentPage)
        if (items.size <= 1) return@LaunchedEffect
        delay(SPOTLIGHT_AUTO_SCROLL_MS)
        if (!pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
        }
    }

    LaunchedEffect(requestInitialFocus, items.isNotEmpty()) {
        if (requestInitialFocus && items.isNotEmpty()) {
            buttonFocusRequester.requestFocus()
            onInitialFocusHandled()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            val item = items[page]
            AsyncImage(
                model = item.backdropUrl ?: item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            HubColors.Background.copy(alpha = 0.02f),
                            HubColors.Background.copy(alpha = 0.12f),
                            HubColors.Background.copy(alpha = 0.38f),
                            HubColors.Background.copy(alpha = 0.84f),
                            HubColors.Background,
                        ),
                    ),
                ),
        )

        val current = items[pagerState.currentPage.coerceIn(items.indices)]
        SpotlightContent(
            item = current,
            detail = detail?.takeIf { it.tmdbId == current.tmdbId && it.type == current.type },
            onOpen = { onOpen(current) },
            focusRequester = buttonFocusRequester,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )

        if (items.size > 1) {
            val indicatorIndices = spotlightIndicatorWindow(
                itemCount = items.size,
                currentPage = pagerState.currentPage,
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                indicatorIndices.forEach { index ->
                    val active = index == pagerState.currentPage
                    val width by animateDpAsState(
                        targetValue = if (active) 32.dp else 8.dp,
                        animationSpec = tween(220),
                        label = "spotlight-indicator-width",
                    )
                    Box(
                        Modifier
                            .width(width)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (active) 0.92f else 0.35f))
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                    )
                }
            }
        }
    }
}

@Composable
internal fun NuvioSpotlightSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(nuvioSpotlightHeroHeight())
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
    ) {
        HubSkeletonBlock(Modifier.fillMaxSize(), cornerRadius = 0.dp)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, HubColors.Background.copy(alpha = 0.94f)),
                    ),
                ),
        )
    }
}

@Composable
private fun SpotlightContent(
    item: MediaItem,
    detail: MediaDetail?,
    onOpen: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val logo = detail?.logoUrl?.takeIf { it.isNotBlank() }
        if (logo != null) {
            AsyncImage(
                model = logo,
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .widthIn(max = 300.dp)
                    .aspectRatio(2.6f),
            )
        } else {
            Text(
                text = item.title,
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SpotlightMeta(
                stringResource(
                    if (item.type == MediaType.MOVIE) R.string.home_type_movie else R.string.home_type_show,
                ),
            )
            (item.genres.firstOrNull() ?: detail?.genres?.firstOrNull())?.let { genre ->
                SpotlightDot()
                SpotlightMeta(genre)
            }
            (item.year ?: detail?.year)?.let { year ->
                SpotlightDot()
                SpotlightMeta(year.toString())
            }
        }

        Spacer(Modifier.height(14.dp))
        SpotlightDetailsButton(
            text = stringResource(R.string.home_view_details),
            onClick = onOpen,
            modifier = Modifier.focusRequester(focusRequester),
        )
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun SpotlightMeta(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SpotlightDot() {
    Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)))
}

@Composable
private fun SpotlightDetailsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(160),
        label = "spotlight-button-scale",
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(Color.White)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) HubColors.Accent else Color.Transparent,
                shape = RoundedCornerShape(40.dp),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 28.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF090A0F),
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Keeps a large personalised pool from producing a row of dozens of dots.
 *
 * The window advances in pages rather than following the selected item one
 * position at a time. A centred moving window made the active pill reach the
 * fourth slot and then appear stuck there while the hero itself kept moving.
 * With fixed groups it visibly travels across every dot before the next group
 * replaces it.
 */
internal fun spotlightIndicatorWindow(
    itemCount: Int,
    currentPage: Int,
    maxVisible: Int = 7,
): IntRange {
    if (itemCount <= 0 || maxVisible <= 0) return IntRange.EMPTY
    if (itemCount <= maxVisible) return 0 until itemCount
    val page = currentPage.coerceIn(0, itemCount - 1)
    val first = (page / maxVisible) * maxVisible
    return first until minOf(first + maxVisible, itemCount)
}

@Composable
internal fun nuvioSpotlightHeroHeight() = with(LocalConfiguration.current) {
    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        (screenHeightDp * 0.78f).dp.coerceIn(240.dp, 420.dp)
    } else {
        (screenHeightDp * 0.82f).dp.coerceIn(360.dp, 760.dp)
    }
}
