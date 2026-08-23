package com.mdblisthub.tv.ui.home

import android.content.res.Configuration
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.isActive
import androidx.compose.foundation.verticalScroll

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.res.stringResource
import com.mdblisthub.tv.R
import com.mdblisthub.tv.core.data.DataGraph
import com.mdblisthub.tv.core.model.MediaItem
import com.mdblisthub.tv.core.model.MediaList
import com.mdblisthub.tv.core.model.MdblistHomeFeed
import com.mdblisthub.tv.core.model.AddonCatalog
import com.mdblisthub.tv.core.model.ResumePoint
import com.mdblisthub.tv.core.ui.component.FanartBackdrop
import com.mdblisthub.tv.core.ui.component.HubGlassCard
import com.mdblisthub.tv.core.ui.component.HubSkeletonBlock
import com.mdblisthub.tv.core.ui.component.MediaRow
import com.mdblisthub.tv.core.ui.component.PosterActionOverlayHost
import com.mdblisthub.tv.core.ui.component.PosterOverlayAction
import com.mdblisthub.tv.core.ui.component.PosterOverlayRequest
import com.mdblisthub.tv.core.ui.theme.HubColors
import com.mdblisthub.tv.core.ui.theme.HubDimens
import com.mdblisthub.tv.core.ui.theme.HubTokens
import com.mdblisthub.tv.core.model.MediaDetail
import com.mdblisthub.tv.core.model.HubThemeVariant
import coil3.compose.AsyncImage
import com.mdblisthub.tv.ui.component.AnimatedBrandTitle
import com.mdblisthub.tv.ui.component.HubButton
import com.mdblisthub.tv.ui.hubViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Where a focused row parks: 30% down the viewport, the same pivot Compose's own (internal) TV spec uses. */
private const val ROW_PIVOT = 0.3f

private sealed interface EditableListTarget {
    val displayName: String

    data class Mdblist(val list: MediaList) : EditableListTarget {
        override val displayName: String get() = list.name
    }

    data class Stremio(val catalog: AddonCatalog) : EditableListTarget {
        override val displayName: String get() = catalog.name
    }

    data class Feed(val feed: MdblistHomeFeed) : EditableListTarget {
        override val displayName: String get() = feed.name
    }
}

/**
 * How the column scrolls when focus moves between rows.
 *
 * Compose's default already animates — it is a `spring` — so the jerkiness
 * was never a missing animation. It is that the default scrolls the *minimum*
 * distance needed to reveal the row, so every row settles at whatever height
 * happens to work out and each press travels a different amount. The eye
 * reads that as stumbling rather than gliding.
 *
 * The fix is a fixed landing point. Compose ships exactly this as
 * `PivotBringIntoViewSpec`, in its Android source set because it exists for
 * televisions — but it is `internal`, so the geometry is restated here.
 *
 * There used to be a `scrollAnimationSpec` override here — a stiff, non-bouncy
 * spring, chosen because the pivot makes *every* press scroll and a held
 * direction key needs an animation that retargets from wherever it is rather
 * than restarting. Compose has since deprecated that member outright
 * ("Animation spec customization is no longer supported") and no longer reads
 * it anywhere, so the override was doing nothing but emitting a warning. The
 * scroll it now uses is the framework's own, which is already a spring with
 * exactly that retargeting behaviour; only the landing point below was ever
 * the part Compose could not supply.
 */
@OptIn(ExperimentalFoundationApi::class)
private class RowPivotScroll(
    private val variant: HubThemeVariant,
    private val normalFirstRowOffsetPx: Float,
    private val heroScrollDistance: (offset: Float, size: Float) -> Float? = { _, _ -> null },
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // A button near the bottom of the spotlight belongs to the hero, not
        // to a shelf. Keep the whole hero at its one valid resting position.
        heroScrollDistance(offset, size)?.let { return it }

        val pivot = when {
            variant == HubThemeVariant.NORMAL -> normalFirstRowOffsetPx
            // The focused child is the card, not the whole shelf. This offset
            // equals the shelf heading plus its gap, so that heading lands at
            // the viewport top and every preceding shelf remains clipped.
            variant == HubThemeVariant.PRIMEFLY -> 0.11f * containerSize
            variant == HubThemeVariant.NETFLIXY -> 0.18f * containerSize
            else -> ROW_PIVOT * containerSize
        }

        // A row tall enough that parking it at the pivot would hang its
        // bottom off-screen is aligned to the bottom edge instead — parking
        // it would otherwise hide the very cards being brought into view.
        val target = if (size <= containerSize && containerSize - pivot < size) {
            containerSize - size
        } else {
            pivot
        }

        // The container clamps this at both ends, which is what keeps the
        // hero panel visible at the top of the list: the first row wants to
        // move *down* to reach the pivot, and there is nowhere to scroll.
        return offset - target
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    graph: DataGraph,
    onOpenTitle: (MediaItem) -> Unit,
    onOpenAddons: () -> Unit,
    onResume: (ResumePoint) -> Unit,
    initialEditMode: Boolean = false,
) {
    val viewModel = hubViewModel { HomeViewModel(graph) }
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val allLists by viewModel.allLists.collectAsStateWithLifecycle()
    val feeds by viewModel.feeds.collectAsStateWithLifecycle()
    val allFeeds by viewModel.allFeeds.collectAsStateWithLifecycle()
    val addonCatalogs by viewModel.addonCatalogs.collectAsStateWithLifecycle()
    val allAddonCatalogs by viewModel.allAddonCatalogs.collectAsStateWithLifecycle()
    val resumePoints by viewModel.resumePoints.collectAsStateWithLifecycle()
    // `focused`, `focusedBackdropUrl` and `focusedDetail` are deliberately NOT
    // collected here. Reading them at this level made this ~500-line composable
    // the recomposition scope for every single card the D-pad passes over.
    // `HeroPanel` and the backdrop collect them themselves, so the scope that
    // invalidates is the one that actually displays the value.
    val becauseYouWatched by viewModel.becauseYouWatched.collectAsStateWithLifecycle()
    val spotlight by viewModel.spotlight.collectAsStateWithLifecycle()
    val spotlightLoaded by viewModel.spotlightLoaded.collectAsStateWithLifecycle()
    val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
    val watchedIds by viewModel.watchedIds.collectAsStateWithLifecycle()
    val watchedEpisodes by viewModel.watchedEpisodes.collectAsStateWithLifecycle()
    val initialSyncComplete by viewModel.initialSyncComplete.collectAsStateWithLifecycle()
    val mdblistLinked by graph.auth.mdblistLinked.collectAsStateWithLifecycle(initialValue = false)
    val deletedListIds by graph.session.deletedListIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val themeVariant = HubColors.variant
    val isNormalTheme = themeVariant == HubThemeVariant.NORMAL
    val isSpotlightTheme = isNormalTheme || themeVariant == HubThemeVariant.CYBERPUNK
    var initialSpotlightFocusPending by remember { mutableStateOf(false) }

    // Hoisted so the reorder-reveal below can convert a row index into a
    // `LazyColumn` index. These two decide how many fixed items sit above the
    // row list, and stating them once is what keeps that arithmetic from
    // drifting out of step with the items themselves.
    val hasSpotlightHero = isSpotlightTheme && (!spotlightLoaded || spotlight.isNotEmpty())
    val hasResumeItem = resumePoints.isNotEmpty() && !isEditMode
    val homeListState = rememberLazyListState()
    val rowToReveal by viewModel.rowToReveal.collectAsStateWithLifecycle()

    LaunchedEffect(initialEditMode) {
        viewModel.setEditMode(initialEditMode)
    }

    /**
     * Follows a row that edit mode just moved.
     *
     * Without this the reorder appears to do nothing: the write lands, the
     * rows come back in the new order, but a `LazyColumn` anchors its scroll to
     * whatever was at the top — and these rows are a poster carousel tall, so
     * two of them barely fit. The moved row leaves the viewport, and the only
     * way to see where it went is to scroll after it, which is exactly the work
     * reordering was supposed to save.
     *
     * Scrolls only when the destination is not already fully on screen, so a
     * move between two visible rows stays where it is instead of snapping.
     */
    LaunchedEffect(rowToReveal) {
        val reveal = rowToReveal ?: return@LaunchedEffect
        val leading = (if (hasSpotlightHero) 1 else 0) +
            (if (hasResumeItem) 1 else 0)
        val targetIndex = leading + reveal.rowIndex
        val layout = homeListState.layoutInfo
        val visible = layout.visibleItemsInfo.firstOrNull { it.index == targetIndex }
        val fullyVisible = visible != null &&
            visible.offset >= layout.viewportStartOffset &&
            visible.offset + visible.size <= layout.viewportEndOffset
        if (!fullyVisible) {
            homeListState.animateScrollToItem(targetIndex)
        }
        viewModel.onRowRevealed()
    }

    LaunchedEffect(themeVariant) {
        initialSpotlightFocusPending = isSpotlightTheme
        if (isSpotlightTheme) viewModel.ensureSpotlight()
    }

    DisposableEffect(lifecycleOwner, viewModel, themeVariant) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDynamicRows()
                if (isSpotlightTheme) initialSpotlightFocusPending = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val extraCatalogs = remember(allLists, addonCatalogs, deletedListIds) {
        val representedListIds = allLists.mapTo(deletedListIds.toMutableSet()) { it.id }
        addonCatalogs.filterNot { catalog ->
            catalog.mdblistMirrorListId()?.let { it in representedListIds } == true
        }
    }
    val homeRows = remember(lists, allLists, feeds, allFeeds, extraCatalogs) {
        val feedsStillUseDefaultPlacement = allFeeds.none { it.position != null }
        val existingRowOffset = if (feedsStillUseDefaultPlacement) feeds.size else 0
        val lastStoredPosition = maxOf(
            allLists.maxOfOrNull { it.position } ?: -1,
            allFeeds.mapNotNull { it.position }.maxOrNull() ?: -1,
            extraCatalogs.mapNotNull { it.position }.maxOrNull() ?: -1,
        )
        var nextCatalogPosition = lastStoredPosition + 1
        val feedRows = feeds.mapIndexed { index, feed ->
            HomeMediaRow.Feed(
                feed,
                if (feedsStillUseDefaultPlacement) index else feed.position ?: index,
            )
        }
        val directRows = lists.map {
            HomeMediaRow.Mdblist(it, it.position + existingRowOffset)
        }
        val catalogRows = extraCatalogs.map { catalog ->
            HomeMediaRow.Stremio(
                catalog,
                (catalog.position ?: nextCatalogPosition++) + existingRowOffset,
            )
        }
        (feedRows + directRows + catalogRows)
            .sortedBy { it.position }
            .mapIndexed { index, row ->
                when (row) {
                    is HomeMediaRow.Mdblist -> row.copy(position = index)
                    is HomeMediaRow.Feed -> row.copy(position = index)
                    is HomeMediaRow.Stremio -> row.copy(position = index)
                }
            }
    }
    val openCatalogItem: (MediaItem) -> Unit = { item ->
        if (item.tmdbId > 0) {
            onOpenTitle(item)
        } else {
            item.imdbId?.let { imdbId ->
                scope.launch {
                    graph.media.resolveImdb(item.type, imdbId).onSuccess { tmdbId ->
                        onOpenTitle(item.copy(tmdbId = tmdbId))
                    }
                }
            }
        }
    }
    val resumePlayback: (ResumePoint) -> Unit = { point ->
        if ((point.tmdbId ?: 0) > 0) {
            onResume(point)
        } else {
            point.imdbId?.let { imdbId ->
                scope.launch {
                    graph.media.resolveImdb(point.type, imdbId).onSuccess { tmdbId ->
                        onResume(point.copy(tmdbId = tmdbId))
                    }
                }
            }
        }
    }

    var renameTarget by remember { mutableStateOf<EditableListTarget?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<EditableListTarget?>(null) }
    val resumeRemoveLabel = stringResource(R.string.home_delete)
    val emptyStateFocusRequester = remember { FocusRequester() }
    val normalFirstRowOffsetPx = with(LocalDensity.current) { 32.dp.toPx() }
    val spotlightHeroHeightPx = with(LocalDensity.current) { nuvioSpotlightHeroHeight().toPx() }
    val rowPivotScroll = remember(
        themeVariant,
        normalFirstRowOffsetPx,
        spotlightHeroHeightPx,
        hasSpotlightHero,
    ) {
        RowPivotScroll(themeVariant, normalFirstRowOffsetPx) { offset, size ->
            val scrolledOff = homeListState.firstVisibleItemScrollOffset.toFloat()
            when {
                !hasSpotlightHero -> null
                homeListState.firstVisibleItemIndex != 0 -> null
                offset < -scrolledOff - 1f -> null
                offset + size > spotlightHeroHeightPx - scrolledOff + 1f -> null
                else -> -scrolledOff
            }
        }
    }
    val onInitialSpotlightFocusHandled = { initialSpotlightFocusPending = false }

    renameTarget?.let { target ->
        Dialog(onDismissRequest = { renameTarget = null }) {
            HubGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                strong = true,
            ) {
                Column(
                    modifier = Modifier.padding(HubTokens.Space.xxl),
                    verticalArrangement = Arrangement.spacedBy(HubTokens.Space.lg),
                ) {
                Text(stringResource(R.string.home_rename_list), style = MaterialTheme.typography.titleLarge, color = HubColors.Text)
                BasicTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = HubColors.Text),
                    cursorBrush = SolidColor(HubColors.Accent2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HubColors.Background, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HubButton(
                        text = stringResource(R.string.home_save),
                        primary = true,
                        enabled = renameValue.isNotBlank(),
                        onClick = {
                            when (target) {
                                is EditableListTarget.Mdblist ->
                                    viewModel.renameList(target.list, renameValue)
                                is EditableListTarget.Stremio ->
                                    viewModel.renameCatalog(target.catalog, renameValue)
                                is EditableListTarget.Feed ->
                                    viewModel.renameFeed(target.feed, renameValue)
                            }
                            renameTarget = null
                        },
                    )
                    HubButton(
                        text = stringResource(R.string.home_cancel),
                        onClick = { renameTarget = null },
                    )
                }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        Dialog(onDismissRequest = { deleteTarget = null }) {
            HubGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                strong = true,
            ) {
                Column(
                    modifier = Modifier.padding(HubTokens.Space.xxl),
                    verticalArrangement = Arrangement.spacedBy(HubTokens.Space.lg),
                ) {
                Text(stringResource(R.string.home_delete_list_question), style = MaterialTheme.typography.titleLarge, color = HubColors.Text)
                Text(
                    when (target) {
                        is EditableListTarget.Mdblist ->
                            stringResource(R.string.home_delete_mdblist_body, target.displayName)
                        is EditableListTarget.Stremio ->
                            stringResource(R.string.home_delete_addon_body, target.displayName)
                        is EditableListTarget.Feed ->
                            stringResource(R.string.home_delete_feed_body, target.displayName)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = HubColors.TextDim,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HubButton(
                        text = stringResource(R.string.home_delete),
                        primary = true,
                        onClick = {
                            when (target) {
                                is EditableListTarget.Mdblist -> viewModel.deleteList(target.list)
                                is EditableListTarget.Stremio -> viewModel.deleteCatalog(target.catalog)
                                is EditableListTarget.Feed -> viewModel.deleteFeed(target.feed)
                            }
                            deleteTarget = null
                        },
                    )
                    HubButton(
                        text = stringResource(R.string.home_cancel),
                        onClick = { deleteTarget = null },
                    )
                }
                }
            }
        }
    }

    val menuLists = stringResource(R.string.menu_lists)
    val menuListsDone = stringResource(R.string.menu_lists_done)
    val resumeCards = remember(resumePoints) { resumePoints.map { it.toCardItem() } }

    Box(Modifier.fillMaxSize()) {
        // The fanart follows focus, the way Estuary does it: whatever the
        // remote is pointing at fills the screen behind the rows.
        FocusedBackdrop(viewModel)

        if ((HubColors.isNetflixLayout || HubColors.isPrimefly) && !isEditMode) {
            FocusedTrailerPip(
                viewModel = viewModel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 18.dp)
                    .width(176.dp)
                    .zIndex(2f),
            )
        }

        Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f)) {

            if (!mdblistLinked && lists.isEmpty() && feeds.isEmpty() && resumePoints.isEmpty() && extraCatalogs.isEmpty()) {
                LaunchedEffect(Unit) {
                    emptyStateFocusRequester.requestFocus()
                }
                Column(
                    modifier = Modifier.fillMaxSize().padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        stringResource(R.string.home_empty_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = HubColors.Text,
                    )
                    Text(
                        stringResource(R.string.home_empty_desc),
                        style = MaterialTheme.typography.bodyLarge,
                        color = HubColors.TextDim,
                    )
                    HubButton(
                        text = stringResource(R.string.home_empty_button),
                        primary = true,
                        onClick = onOpenAddons,
                        modifier = Modifier.focusRequester(emptyStateFocusRequester),
                    )
                }
                return@Row
            }

            // Gated on the sync flag, not on the row lists. The old condition
            // was `lists.isNotEmpty() && allLists.isEmpty()`, and `lists` is
            // `allLists` with the hidden ones filtered out — a subset can
            // never be non-empty while its superset is empty, so this branch
            // was unreachable and the message never appeared once.
            if (!initialSyncComplete && allLists.isEmpty()) {
                HomeLoadingSkeleton()
                return@Row
            }

            if (
                lists.isEmpty() && feeds.isEmpty() && resumePoints.isEmpty() &&
                extraCatalogs.isEmpty() && !hasSpotlightHero
            ) {
                val hasHiddenRows = allLists.isNotEmpty() || allFeeds.isNotEmpty() || allAddonCatalogs.isNotEmpty()
                LaunchedEffect(hasHiddenRows) { emptyStateFocusRequester.requestFocus() }
                Column(
                    modifier = Modifier.fillMaxSize().padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        stringResource(
                            if (hasHiddenRows) R.string.home_no_visible_rows else R.string.home_no_lists,
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        color = HubColors.Text,
                    )
                    Text(
                        stringResource(
                            if (hasHiddenRows) {
                                R.string.home_no_visible_rows_desc
                            } else {
                                R.string.home_no_lists_desc
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = HubColors.TextDim,
                    )
                    HubButton(
                        text = stringResource(
                            if (hasHiddenRows) R.string.home_edit_lists else R.string.home_empty_button,
                        ),
                        primary = true,
                        onClick = if (hasHiddenRows) viewModel::toggleEditMode else onOpenAddons,
                        modifier = Modifier.focusRequester(emptyStateFocusRequester),
                    )
                }
                return@Row
            }

            Column(Modifier.fillMaxSize()) {
                if (HubColors.isNetflixLayout || HubColors.isPrimefly) {
                    // A fixed, phone-appropriate height rather than the TV
                    // build's `weight(1f)`: on a screen far taller than it is
                    // wide, giving the hero whatever space the shelves below
                    // don't need left it filling most of the screen with a
                    // bottom-anchored title and empty backdrop above it. Fixed
                    // height keeps the logo near the top and gives the shelves
                    // the rest of the screen, so more than a sliver of one row
                    // is ever visible without scrolling.
                    // Sized to the hero's own content (logo + metadata +
                    // fixed-height synopsis above), not a round number: this
                    // is what makes the shelf below land exactly on a row
                    // boundary — two full rows, nothing peeking — instead of
                    // guessing at a height and hoping a row lines up.
                    //
                    // Landscape gets its own, much smaller number: the phone
                    // is on its side, so the *whole screen* is roughly what
                    // the portrait hero alone used to claim. Reusing the
                    // portrait height left zero — sometimes negative — room
                    // for the shelf underneath, which is what made touch
                    // navigation reach nothing: there was no card on screen
                    // to tap. A compact hero (see [HeroPanelContent]) is what
                    // this height is sized to fit.
                    val configuration = LocalConfiguration.current
                    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val heroHeight = when {
                        // Primefly's landscape cards are short and wide, so a
                        // bare compact hero left
                        // room for two full rows below it — the synopsis lost
                        // out to a row nobody asked to see twice. Taller here
                        // trades that second row back for the synopsis this
                        // theme is supposed to have, landing on one full row
                        // instead of two.
                        isLandscape && HubColors.isPrimefly -> 170.dp
                        // Netflixy's landscape card remains compact enough to
                        // fit beside the hero while preserving poster shape.
                        isLandscape -> 160.dp
                        HubColors.isPrimefly -> 240.dp
                        else -> 330.dp
                    }
                    Box(Modifier.fillMaxWidth().height(heroHeight)) {
                        HeroPanel(viewModel)
                    }
                }

                CompositionLocalProvider(LocalBringIntoViewSpec provides rowPivotScroll) {
                    LazyColumn(
                        state = homeListState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        // Tighter than HubDimens.RowSpacing on purpose — with the
                        // smaller posters, this is what keeps two rows of a list on
                        // screen together instead of one full row plus a sliver of
                        // the next.
                        verticalArrangement = Arrangement.spacedBy(
                            if (HubColors.isPrimefly) 6.dp else 14.dp,
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            // Primefly positions the whole viewport instead;
                            // padding here would be consumed when focus moves.
                            top = if (HubColors.isPrimefly) 0.dp else 12.dp,
                            // Room to park the last row at the pivot instead of
                            // stopping short with it pinned to the bottom edge.
                            bottom = HubDimens.ScreenPaddingVertical * 8,
                        ),
                    ) {
                        if (hasSpotlightHero) {
                            item(key = "spotlight") {
                                if (spotlightLoaded) {
                                    NuvioSpotlightHero(
                                        viewModel = viewModel,
                                        onOpen = onOpenTitle,
                                        requestInitialFocus = initialSpotlightFocusPending && !isEditMode,
                                        onInitialFocusHandled = onInitialSpotlightFocusHandled,
                                    )
                                } else {
                                    NuvioSpotlightSkeleton()
                                }
                            }
                        }

                if (hasResumeItem) {
                    item(key = "resume") {
                        MediaRow(
                            title = stringResource(R.string.home_resume_row),
                            items = resumeCards,
                            // `card` alone cannot say which episode this is —
                            // `toCardItem()` drops season/episode, so two
                            // in-progress episodes of the same show produce
                            // equal `MediaItem`s, and `indexOf` on those would
                            // always resolve to the *first* one regardless of
                            // which card was actually pressed. `resumeCards`
                            // and `resumePoints` are the same list at the same
                            // indices (see their construction above), so only
                            // the position — not the card's own equality —
                            // can say correctly which point this was.
                            onItemFocused = viewModel::onFocused,
                            key = { index, item -> resumePoints.getOrNull(index)?.key ?: item.key },
                            onItemClickIndexed = { index, _ ->
                                resumeCards.getOrNull(index)?.let(openCatalogItem)
                            },
                            onItemLongClickIndexed = { index, _, anchor ->
                                resumePoints.getOrNull(index)?.let { point ->
                                    PosterActionOverlayHost.show(
                                        PosterOverlayRequest(
                                            anchor = anchor,
                                            title = point.title,
                                            actions = listOf(
                                                PosterOverlayAction(
                                                    label = resumeRemoveLabel,
                                                    icon = Icons.Default.Delete,
                                                    isDestructive = true,
                                                    onSelected = { viewModel.removeResumePoint(point) },
                                                ),
                                            ),
                                        ),
                                    )
                                }
                            },
                            progressPercent = { index, _ -> resumePoints.getOrNull(index)?.progress },
                            isWatched = { _, item -> watchedIds.contains(item.tmdbId) },
                            requireDoubleTapToOpen = HubColors.isNetflixLayout || HubColors.isPrimefly,
                            requestInitialFocus = isSpotlightTheme &&
                                !hasSpotlightHero &&
                                initialSpotlightFocusPending &&
                                !isEditMode,
                            onInitialFocusHandled = onInitialSpotlightFocusHandled,
                        )
                    }
                }

                itemsIndexed(
                    homeRows,
                    key = { _, row ->
                        when (row) {
                            is HomeMediaRow.Mdblist -> "mdblist-${row.list.id}"
                            is HomeMediaRow.Feed -> row.feed.key
                            is HomeMediaRow.Stremio -> "addon-catalog-${catalogRowIdentity(row.catalog)}"
                        }
                    },
                ) { index, row ->
                    val requestInitialFocus = isSpotlightTheme &&
                        !hasSpotlightHero &&
                        initialSpotlightFocusPending &&
                        !isEditMode &&
                        resumePoints.isEmpty() &&
                        index == 0
                    when (row) {
                        is HomeMediaRow.Mdblist -> {
                            val list = row.list
                            val itemFlow = remember(list.id) { viewModel.itemsFor(list.id) }
                            ListRow(
                                list = list,
                                itemFlow = itemFlow,
                                isEditMode = isEditMode,
                                onToggleVisibility = { viewModel.toggleListVisibility(list) },
                                canMoveUp = index > 0,
                                canMoveDown = index < homeRows.lastIndex,
                                onMoveUp = { viewModel.moveRow(homeRows, index, -1) },
                                onMoveDown = { viewModel.moveRow(homeRows, index, 1) },
                                onRename = {
                                    renameTarget = EditableListTarget.Mdblist(list)
                                    renameValue = list.name
                                },
                                onDelete = {
                                    deleteTarget = EditableListTarget.Mdblist(list)
                                },
                                onEnsure = { viewModel.ensureItems(list.id) },
                                onItemClick = onOpenTitle,
                                onItemFocused = viewModel::onFocused,
                                onReachedEnd = { viewModel.loadMore(list.id) },
                                isWatched = { _, item -> watchedIds.contains(item.tmdbId) },
                                requestInitialFocus = requestInitialFocus,
                                onInitialFocusHandled = onInitialSpotlightFocusHandled,
                            )
                        }
                        is HomeMediaRow.Stremio -> {
                            val catalog = row.catalog
                            val itemFlow = remember(catalog.addonBase, catalog.key) {
                                viewModel.itemsForCatalog(catalog)
                            }
                            AddonCatalogRow(
                                catalog = catalog,
                                itemFlow = itemFlow,
                                isEditMode = isEditMode,
                                onToggleVisibility = {
                                    viewModel.toggleCatalogVisibility(catalog)
                                },
                                canMoveUp = index > 0,
                                canMoveDown = index < homeRows.lastIndex,
                                onMoveUp = { viewModel.moveRow(homeRows, index, -1) },
                                onMoveDown = { viewModel.moveRow(homeRows, index, 1) },
                                onRename = {
                                    renameTarget = EditableListTarget.Stremio(catalog)
                                    renameValue = catalog.name
                                },
                                onDelete = {
                                    deleteTarget = EditableListTarget.Stremio(catalog)
                                },
                                onEnsure = { viewModel.ensureCatalog(catalog) },
                                onItemClick = openCatalogItem,
                                onItemFocused = viewModel::onFocused,
                                isWatched = { _, item -> watchedIds.contains(item.tmdbId) },
                                requestInitialFocus = requestInitialFocus,
                                onInitialFocusHandled = onInitialSpotlightFocusHandled,
                            )
                        }
                        is HomeMediaRow.Feed -> {
                            val feed = row.feed
                            val cards = feed.items.map { it.media }
                            MediaRow(
                                title = feed.name,
                                items = cards,
                                isEditMode = isEditMode,
                                hidden = feed.hidden,
                                onToggleVisibility = {
                                    viewModel.toggleFeedVisibility(feed)
                                },
                                canMoveUp = index > 0,
                                canMoveDown = index < homeRows.lastIndex,
                                onMoveUp = { viewModel.moveRow(homeRows, index, -1) },
                                onMoveDown = { viewModel.moveRow(homeRows, index, 1) },
                                onRename = {
                                    renameTarget = EditableListTarget.Feed(feed)
                                    renameValue = feed.name
                                },
                                onDelete = {
                                    deleteTarget = EditableListTarget.Feed(feed)
                                },
                                key = { itemIndex, item ->
                                    val feedItem = feed.items.getOrNull(itemIndex)
                                    "${item.key}:${feedItem?.season ?: 0}:${feedItem?.episode ?: 0}"
                                },
                                onItemClickIndexed = { _, item ->
                                    openCatalogItem(item)
                                },
                                onItemFocused = viewModel::onFocused,
                                isWatched = { itemIndex, item ->
                                    val feedItem = feed.items.getOrNull(itemIndex)
                                    if (feedItem?.season != null && feedItem.episode != null) {
                                        watchedEpisodes.contains("${item.tmdbId}:${feedItem.season}:${feedItem.episode}")
                                    } else {
                                        watchedIds.contains(item.tmdbId)
                                    }
                                },
                                requireDoubleTapToOpen = HubColors.isNetflixLayout || HubColors.isPrimefly,
                                requestInitialFocus = requestInitialFocus,
                                onInitialFocusHandled = onInitialSpotlightFocusHandled,
                            )
                        }
                    }
                }

                // "Porque você assistiu" — always last, since it is built from
                // the five most recent watches rather than an MDBList row.
                if (!isEditMode) {
                    itemsIndexed(
                        becauseYouWatched,
                        key = { _, row -> "byw-${row.seedTitle}" },
                    ) { index, row ->
                        MediaRow(
                            title = stringResource(R.string.home_because_you_watched, row.seedTitle),
                            items = row.items,
                            onItemClick = onOpenTitle,
                            onItemFocused = viewModel::onFocused,
                            requireDoubleTapToOpen = HubColors.isNetflixLayout || HubColors.isPrimefly,
                            requestInitialFocus = isSpotlightTheme &&
                                !hasSpotlightHero &&
                                initialSpotlightFocusPending &&
                                resumePoints.isEmpty() &&
                                homeRows.isEmpty() &&
                                index == 0,
                            onInitialFocusHandled = onInitialSpotlightFocusHandled,
                        )
                    }
                }
                }
            }
            }
        }

        }
        if (homeRows.isNotEmpty() || allLists.isNotEmpty() || allFeeds.isNotEmpty() || allAddonCatalogs.isNotEmpty()) {
            HubButton(
                text = if (isEditMode) menuListsDone else menuLists,
                primary = isEditMode,
                onClick = viewModel::toggleEditMode,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = HubDimens.ScreenPaddingHorizontal),
            )
        }
    }
}

@Composable
private fun AddonCatalogRow(
    catalog: AddonCatalog,
    itemFlow: StateFlow<List<MediaItem>>,
    isEditMode: Boolean,
    onToggleVisibility: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onEnsure: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onItemFocused: (MediaItem) -> Unit,
    isWatched: ((Int, MediaItem) -> Boolean)? = null,
    requestInitialFocus: Boolean = false,
    onInitialFocusHandled: () -> Unit = {},
) {
    val items by itemFlow.collectAsStateWithLifecycle()
    LaunchedEffect(catalog.addonBase, catalog.key) { onEnsure() }
    MediaRow(
        title = catalog.name,
        items = items,
        isEditMode = isEditMode,
        hidden = catalog.hidden,
        onToggleVisibility = onToggleVisibility,
        canMoveUp = canMoveUp,
        canMoveDown = canMoveDown,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onRename = onRename,
        onDelete = onDelete,
        onItemClick = onItemClick,
        onItemFocused = onItemFocused,
        isWatched = isWatched,
        requireDoubleTapToOpen = HubColors.isNetflixLayout || HubColors.isPrimefly,
        requestInitialFocus = requestInitialFocus,
        onInitialFocusHandled = onInitialFocusHandled,
    )
}

private const val MDBLIST_CATALOG_HOST = "stremio-mdblist.baby-beamup.club"

private fun catalogRowIdentity(catalog: AddonCatalog): String =
    "${catalog.addonBase.trimEnd('/')}|${catalog.key}"

/** Returns the source MDBList id only for manifests generated by its catalog bridge. */
private fun AddonCatalog.mdblistMirrorListId(): Long? {
    val uri = runCatching { java.net.URI(addonBase) }.getOrNull() ?: return null
    if (!uri.host.orEmpty().contains(MDBLIST_CATALOG_HOST, ignoreCase = true)) return null

    val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
    val marker = segments.indexOfLast { it.equals("mdblist", ignoreCase = true) }
    if (marker < 2) return null
    return segments.getOrNull(marker - 2)?.toLongOrNull()
}

/**
 * One row, collecting its own items.
 *
 * `LazyColumn` only collects rows near the viewport. The ViewModel retains
 * each visited row's last Room emission, so returning upward restores its
 * geometry immediately instead of flashing through an empty 0dp item.
 */
@Composable
private fun ListRow(
    list: MediaList,
    itemFlow: StateFlow<List<MediaItem>>,
    isEditMode: Boolean = false,
    onToggleVisibility: () -> Unit = {},
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEnsure: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onItemFocused: (MediaItem) -> Unit,
    isWatched: ((Int, MediaItem) -> Boolean)? = null,
    onReachedEnd: () -> Unit,
    requestInitialFocus: Boolean = false,
    onInitialFocusHandled: () -> Unit = {},
) {
    val items by itemFlow.collectAsStateWithLifecycle()

    LaunchedEffect(list.id) { onEnsure() }

    MediaRow(
        title = list.name,
        items = items,
        isEditMode = isEditMode,
        hidden = list.hidden,
        onToggleVisibility = onToggleVisibility,
        canMoveUp = canMoveUp,
        canMoveDown = canMoveDown,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onRename = onRename,
        onDelete = onDelete,
        onItemClick = onItemClick,
        onItemFocused = onItemFocused,
        isWatched = isWatched,
        onReachedEnd = onReachedEnd,
        requireDoubleTapToOpen = HubColors.isNetflixLayout || HubColors.isPrimefly,
        requestInitialFocus = requestInitialFocus,
        onInitialFocusHandled = onInitialFocusHandled,
    )
}

@Composable
private fun AutoScrollText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val scrollState = androidx.compose.foundation.rememberScrollState()
    LaunchedEffect(text) {
        scrollState.scrollTo(0)
        kotlinx.coroutines.delay(4000)
        while (isActive) {
            val max = scrollState.maxValue
            if (max > 0) {
                // 80ms/px rather than 40 — at the old speed a long synopsis
                // read as a blur scrolling past, not text anyone could
                // actually read while glancing at the hero panel.
                scrollState.animateScrollTo(max, animationSpec = androidx.compose.animation.core.tween(durationMillis = max * 80, easing = androidx.compose.animation.core.LinearEasing))
                kotlinx.coroutines.delay(4000)
                scrollState.animateScrollTo(0, animationSpec = androidx.compose.animation.core.tween(durationMillis = 800))
                kotlinx.coroutines.delay(3000)
            } else {
                kotlinx.coroutines.delay(1000)
            }
        }
    }
    Text(
        text = text,
        style = style,
        color = color,
        modifier = modifier.verticalScroll(scrollState)
    )
}

/**
 * The full-bleed artwork, collecting the focused item's backdrop itself.
 *
 * Split out for the recomposition scope, not for tidiness: the URL changes
 * every time focus settles on a different card, and read from `HomeScreen` it
 * invalidated the whole screen for what is one `AsyncImage`.
 */
@Composable
private fun FocusedBackdrop(viewModel: HomeViewModel) {
    val url by viewModel.focusedBackdropUrl.collectAsStateWithLifecycle()
    FanartBackdrop(url = url)
}

/** Same reasoning as [FocusedBackdrop] — see the note in `HomeScreen`. */
@Composable
private fun HeroPanel(viewModel: HomeViewModel) {
    val item by viewModel.focused.collectAsStateWithLifecycle()
    val itemDetail by viewModel.focusedDetail.collectAsStateWithLifecycle()
    HeroPanelContent(item, itemDetail)
}

@Composable
private fun HeroPanelContent(item: MediaItem?, itemDetail: MediaDetail?) {
    if (HubColors.isNetflixLayout || HubColors.isPrimefly) {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Bottom padding big enough to read as a gap before the
                // shelf's own row title, not the 4dp leftover from the TV
                // build — that was sized for the hero's `weight(1f)` box,
                // which always had slack under the synopsis. This box is
                // sized exactly to its content instead, so without real
                // padding here the synopsis's last line sits flush against
                // the shelf title with nothing between them.
                .padding(
                    start = HubDimens.ScreenPaddingHorizontal,
                    end = HubDimens.ScreenPaddingHorizontal,
                    bottom = if (isLandscape) 6.dp else 16.dp,
                )
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (item == null) {
                AnimatedBrandTitle(
                    style = MaterialTheme.typography.headlineLarge,
                )
                return@Column
            }

            // Landscape squeezes the hero down to a strip barely taller than
            // one line of text — plenty of room to read a clearlogo at its
            // usual size on a TV panel, not on a phone turned sideways. The
            // artwork itself is already doing the "this is a known title"
            // job at that size; the title text is what stays legible, so
            // landscape shows it instead of the logo rather than alongside
            // an illegibly shrunk one.
            val logoUrl = itemDetail?.logoUrl.takeUnless { isLandscape }
            // Only reached in portrait now — landscape never has a `logoUrl`
            // to draw, so there is no cramped size to size for here anymore.
            val logoHeight = 100.dp
            val logoBottomPadding = when {
                isLandscape && HubColors.isPrimefly -> 4.dp
                isLandscape -> 8.dp
                HubColors.isPrimefly -> 4.dp
                else -> 20.dp
            }
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = item.title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.height(logoHeight).padding(bottom = logoBottomPadding),
                    alignment = Alignment.BottomStart,
                )
            } else {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = HubColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = logoBottomPadding)
                )
            }

            HeroMetadataRow(
                item = item,
                detail = itemDetail,
                modifier = Modifier.padding(
                    bottom = when {
                        isLandscape && HubColors.isPrimefly -> 4.dp
                        isLandscape -> 8.dp
                        HubColors.isPrimefly -> 6.dp
                        else -> 12.dp
                    },
                ),
            )

            // In landscape both themes expose exactly three 22sp lines at a
            // time. Portrait keeps the roomier, theme-specific treatment.
            val overview = itemDetail?.overview
            if (overview != null) {
                if (isLandscape) {
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = HubColors.Text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.55f),
                    )
                } else {
                    AutoScrollText(
                        text = overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = HubColors.Text,
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(if (HubColors.isPrimefly) 96.dp else 110.dp),
                    )
                }
            }
        }
    } else {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(
                    start = HubDimens.ScreenPaddingHorizontal,
                    end = HubDimens.ScreenPaddingHorizontal,
                    bottom = if (isLandscape) 10.dp else 18.dp,
                ),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (item == null) {
                AnimatedBrandTitle(
                    style = MaterialTheme.typography.headlineLarge,
                )
                return@Column
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineLarge,
                color = HubColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            HeroMetadataRow(item = item, detail = itemDetail)
            itemDetail?.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HubColors.TextDim,
                    maxLines = if (isLandscape) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(if (isLandscape) 0.62f else 0.88f),
                )
            }
        }
    }
}

@Composable
private fun HomeLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = HubDimens.ScreenPaddingHorizontal, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        HubSkeletonBlock(Modifier.fillMaxWidth(0.42f).height(34.dp))
        repeat(3) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HubSkeletonBlock(Modifier.width(150.dp).height(18.dp), cornerRadius = 6.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(HubDimens.CardSpacing)) {
                    repeat(4) {
                        HubSkeletonBlock(
                            Modifier
                                .width(HubDimens.PosterWidth)
                                .height(HubDimens.PosterHeight),
                        )
                    }
                }
            }
        }
    }
}

/** Exact themed order: year • genre • duration, with theme-coloured separators. */
@Composable
private fun HeroMetadataRow(
    item: MediaItem,
    detail: MediaDetail?,
    modifier: Modifier = Modifier,
) {
    val values = listOfNotNull(
        (item.year ?: detail?.year)?.toString(),
        item.genres.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: detail?.genres?.firstOrNull()?.takeIf { it.isNotBlank() },
        (item.runtimeMinutes ?: detail?.runtimeMinutes)?.let {
            stringResource(R.string.home_minutes, it)
        },
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        values.forEachIndexed { index, value ->
            if (index > 0) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (HubColors.isPrimefly) HubColors.Accent else HubColors.NetflixRed,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = HubColors.TextDim,
            )
        }
    }
}

private fun ResumePoint.toCardItem() = MediaItem(
    tmdbId = tmdbId ?: 0,
    type = type,
    title = title,
    imdbId = imdbId,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    score = score,
)
