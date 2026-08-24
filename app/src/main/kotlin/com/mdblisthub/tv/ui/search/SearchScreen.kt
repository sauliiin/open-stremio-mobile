package com.mdblisthub.tv.ui.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import com.mdblisthub.tv.R
import com.mdblisthub.tv.core.data.DataGraph
import com.mdblisthub.tv.core.model.MediaItem
import com.mdblisthub.tv.core.ui.component.PosterCard
import com.mdblisthub.tv.core.ui.component.PosterActionOverlayHost
import com.mdblisthub.tv.core.ui.component.PosterOverlayAction
import com.mdblisthub.tv.core.ui.component.PosterOverlayRequest
import com.mdblisthub.tv.core.ui.component.HubGlassCard
import com.mdblisthub.tv.core.ui.component.HubScreenHeading
import com.mdblisthub.tv.core.ui.component.HubSkeletonBlock
import com.mdblisthub.tv.core.ui.theme.HubColors
import com.mdblisthub.tv.core.ui.theme.HubDimens
import com.mdblisthub.tv.core.ui.theme.HubTokens

@Composable
fun SearchScreen(
    graph: DataGraph,
    onOpenTitle: (MediaItem) -> Unit,
    onPlay: (MediaItem) -> Unit,
    onChooseSource: (MediaItem) -> Unit,
) {
    val viewModel = viewModel<SearchViewModel>(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = SearchViewModel(graph) as T
    })

    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val watchedIds by viewModel.watchedIds.collectAsStateWithLifecycle()
    val playLabel = stringResource(R.string.media_options_play)
    val sourceLabel = stringResource(R.string.media_options_select_source)
    val infoLabel = stringResource(R.string.media_options_info)
    val watchedLabel = stringResource(R.string.media_options_mark_watched)
    val unwatchedLabel = stringResource(R.string.media_options_mark_unwatched)

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = HubDimens.ScreenPaddingHorizontal, vertical = HubDimens.ScreenPaddingVertical),
        verticalArrangement = Arrangement.spacedBy(HubDimens.RowSpacing)
    ) {
        HubScreenHeading(
            title = stringResource(R.string.search_title),
            subtitle = stringResource(R.string.search_subtitle),
        )
        
        SearchBar(
            query = query,
            onQueryChange = viewModel::onQueryChange,
            modifier = Modifier.focusRequester(focusRequester)
        )

        if (isLoading && results.isEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(HubDimens.PosterWidth),
                verticalArrangement = Arrangement.spacedBy(HubDimens.RowSpacing),
                horizontalArrangement = Arrangement.spacedBy(HubDimens.CardSpacing),
                contentPadding = PaddingValues(bottom = HubTokens.Size.contentBottomClearance),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(10) {
                    HubSkeletonBlock(
                        Modifier
                            .fillMaxWidth()
                            .height(HubDimens.PosterHeight),
                    )
                }
            }
        } else if (results.isEmpty() && query.isNotBlank() && !isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SearchStateCard(
                    title = stringResource(R.string.search_no_results),
                    description = stringResource(R.string.search_try_another),
                )
            }
        } else if (results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SearchStateCard(
                    title = stringResource(R.string.search_start_title),
                    description = stringResource(R.string.search_start_hint),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(HubDimens.PosterWidth),
                verticalArrangement = Arrangement.spacedBy(HubDimens.RowSpacing),
                horizontalArrangement = Arrangement.spacedBy(HubDimens.CardSpacing),
                contentPadding = PaddingValues(bottom = HubTokens.Size.contentBottomClearance),
                modifier = Modifier.fillMaxSize()
            ) {
                // Keyed so that refining a query re-uses the cards for titles
                // that survive it instead of rebuilding — and, more visibly on
                // a remote, so focus stays on the card it was on rather than
                // jumping to whatever now occupies that index.
                items(results, key = { it.key }) { item ->
                    PosterCard(
                        item = item,
                        onClick = { onOpenTitle(item) },
                        isWatched = item.tmdbId in watchedIds,
                        onLongClick = { anchor ->
                            val watched = item.tmdbId in watchedIds
                            PosterActionOverlayHost.show(
                                PosterOverlayRequest(
                                    anchor = anchor,
                                    title = item.title,
                                    actions = listOf(
                                        PosterOverlayAction(playLabel, Icons.Default.PlayArrow) { onPlay(item) },
                                        PosterOverlayAction(sourceLabel, Icons.Default.Tune) { onChooseSource(item) },
                                        PosterOverlayAction(infoLabel, Icons.Default.Info) { onOpenTitle(item) },
                                        PosterOverlayAction(
                                            if (watched) unwatchedLabel else watchedLabel,
                                            if (watched) Icons.Default.Undo else Icons.Default.CheckCircle,
                                        ) { viewModel.setWatched(item, !watched) },
                                    ),
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(if (isFocused) HubColors.Accent else HubColors.Border)
    val cornerRadius = if (HubColors.isCyberpunk) 0.dp else HubTokens.Radius.lg

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = HubColors.Text),
        cursorBrush = SolidColor(HubColors.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(HubColors.Surface.copy(alpha = HubTokens.Opacity.glassStrong))
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        decorationBox = { innerTextField ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Search, contentDescription = null, tint = if (isFocused) HubColors.Accent else HubColors.TextDim)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.search_placeholder), color = HubColors.TextDim, style = MaterialTheme.typography.bodyMedium)
                    }
                    innerTextField()
                }
            }
        }
    )
}

@Composable
private fun SearchStateCard(
    title: String,
    description: String,
) {
    HubGlassCard(
        modifier = Modifier.fillMaxWidth(0.88f),
    ) {
        Column(
            modifier = Modifier.padding(HubTokens.Space.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HubTokens.Space.sm),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = HubColors.Accent,
                modifier = Modifier.padding(bottom = HubTokens.Space.xs),
            )
            Text(title, style = MaterialTheme.typography.titleLarge, color = HubColors.Text)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = HubColors.TextDim)
        }
    }
}
