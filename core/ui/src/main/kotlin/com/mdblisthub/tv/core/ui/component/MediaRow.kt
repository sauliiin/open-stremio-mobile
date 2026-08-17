package com.mdblisthub.tv.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mdblisthub.tv.core.ui.R
import androidx.tv.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.CircleShape
import com.mdblisthub.tv.core.model.MediaItem
import com.mdblisthub.tv.core.ui.theme.HubColors
import com.mdblisthub.tv.core.ui.theme.HubDimens
import kotlin.math.abs

/**
 * Keeps the focused card inside the same horizontal safe area as the row
 * title.
 *
 * LazyRow's default bring-into-view calculation considers the physical edge
 * of the viewport visible. After browsing right and returning, it therefore
 * stops as soon as the first card reaches x=0, consuming the content padding
 * and leaving that card flush against the bezel. This is the default minimal
 * scroll calculation with both viewport edges inset, so focus restores to the
 * visual margin instead. It also overrides the home screen's vertical pivot
 * spec, which must never be applied horizontally to individual cards.
 */
@OptIn(ExperimentalFoundationApi::class)
private class SafeHorizontalScroll(private val insetPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leading = offset
        val trailing = offset + size
        val safeLeading = insetPx
        val safeTrailing = containerSize - insetPx

        if (leading >= safeLeading && trailing <= safeTrailing) return 0f
        if (leading < safeLeading && trailing > safeTrailing) return 0f

        val leadingDelta = leading - safeLeading
        val trailingDelta = trailing - safeTrailing
        return if (abs(leadingDelta) < abs(trailingDelta)) leadingDelta else trailingDelta
    }
}

/**
 * A list, rendered as a row of posters.
 *
 * `focusRestorer` is what makes vertical navigation feel right: leaving a row
 * and coming back should land on the card you left, not snap to the first one.
 * Without it, browsing down three rows and back up loses your place every
 * time — the classic tell of a TV app built from phone components.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaRow(
    title: String,
    items: List<MediaItem>,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    hidden: Boolean = false,
    onToggleVisibility: () -> Unit = {},
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
    onItemClick: (MediaItem) -> Unit = {},
    onItemFocused: (MediaItem) -> Unit = {},
    onReachedEnd: () -> Unit = {},
    /**
     * Overridable and index-aware because [MediaItem.key] — `type:tmdbId` —
     * is right for every catalog row, where a title appears at most once,
     * but not for "Continuar assistindo": two different in-progress episodes
     * of the same show carry the same tmdbId, and `toCardItem()` drops
     * season/episode entirely, so the two cards are not just same-keyed but
     * structurally *equal* `MediaItem`s. No function of the item alone can
     * tell them apart — only their position in the list the caller built
     * can, which is why this takes the index too, and why the crash this
     * exists to fix ("Key ... was already used") could not be solved from
     * inside this component.
     */
    key: (Int, MediaItem) -> Any = { _, item -> item.key },
    /**
     * Takes priority over [onItemClick] when set. The same ambiguity the
     * custom [key] exists for — two cards that are equal `MediaItem`s —
     * means `onItemClick(item)` alone cannot tell "Continuar assistindo"
     * which of two identical-looking cards was actually pressed either;
     * only the position can, so that row supplies this instead of widening
     * every other row's simpler, already-correct `(MediaItem) -> Unit`.
     */
    onItemClickIndexed: ((Int, MediaItem) -> Unit)? = null,
    /** Held OK on a card — see [PosterCard]. Indexed for the same reason as above. */
    onItemLongClickIndexed: ((Int, MediaItem) -> Unit)? = null,
    progressPercent: ((Int, MediaItem) -> Float?)? = null,
    isWatched: ((Int, MediaItem) -> Boolean)? = null,
    /** See [PosterCard]'s parameter of the same name. */
    requireDoubleTapToOpen: Boolean = false,
    requestInitialFocus: Boolean = false,
    onInitialFocusHandled: () -> Unit = {},
) {
    if (items.isEmpty() && !isEditMode) return

    val initialFocusRequester = remember { FocusRequester() }
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus && initialFocusRequester.requestFocus()) {
            onInitialFocusHandled()
        }
    }

    val horizontalInsetPx = with(LocalDensity.current) {
        HubDimens.ScreenPaddingHorizontal.toPx()
    }
    val safeHorizontalScroll = remember(horizontalInsetPx) {
        SafeHorizontalScroll(horizontalInsetPx)
    }

    // Hoisted rather than read inline: `stringResource` is only callable from a
    // composable scope, and the `Icon` calls below sit inside `IconButton`
    // content lambdas where reading them per icon would also re-resolve on
    // every recomposition of the row.
    val moveUpLabel = stringResource(R.string.row_move_up)
    val moveDownLabel = stringResource(R.string.row_move_down)
    val renameLabel = stringResource(R.string.row_rename)
    val showLabel = stringResource(R.string.row_show)
    val hideLabel = stringResource(R.string.row_hide)
    val deleteLabel = stringResource(R.string.row_delete)

    Column(
        modifier.fillMaxWidth().alpha(if (isEditMode && hidden) 0.5f else 1f),
        verticalArrangement = Arrangement.spacedBy(if (HubColors.isPrimefly) 6.dp else 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HubDimens.ScreenPaddingHorizontal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = HubColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            
            if (isEditMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    EditIconButton(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = moveUpLabel,
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                    )
                    EditIconButton(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = moveDownLabel,
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                    )
                    EditIconButton(
                        imageVector = Icons.Default.Edit,
                        contentDescription = renameLabel,
                        onClick = onRename,
                    )
                    EditIconButton(
                        imageVector = if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (hidden) showLabel else hideLabel,
                        onClick = onToggleVisibility,
                    )
                    EditIconButton(
                        imageVector = Icons.Default.Delete,
                        contentDescription = deleteLabel,
                        onClick = onDelete,
                        tint = HubColors.Rotten,
                    )
                }
            }
        }

        CompositionLocalProvider(LocalBringIntoViewSpec provides safeHorizontalScroll) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(HubDimens.CardSpacing),
                contentPadding = PaddingValues(horizontal = HubDimens.ScreenPaddingHorizontal),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRestorer(),
            ) {
                itemsIndexed(items, key = key) { index, item ->
                    PosterCard(
                        item = item,
                        initialFocusRequester = initialFocusRequester.takeIf {
                            requestInitialFocus && index == 0
                        },
                        onClick = {
                            onItemClickIndexed?.invoke(index, item) ?: onItemClick(item)
                        },
                        onFocused = { focused ->
                            onItemFocused(focused)
                            // Paging off focus rather than off scroll position:
                            // on a remote the two are the same thing, and focus
                            // is the one that fires exactly once per card.
                            if (focused.key == items.lastOrNull()?.key) onReachedEnd()
                        },
                        progressPercent = progressPercent?.invoke(index, item),
                        isWatched = isWatched?.invoke(index, item) ?: false,
                        onLongClick = onItemLongClickIndexed?.let { handler ->
                            { handler(index, item) }
                        },
                        requireDoubleTapToOpen = requireDoubleTapToOpen,
                        modifier = Modifier.focusProperties { canFocus = !isEditMode },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = HubColors.Text,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            pressed -> HubColors.Accent.copy(alpha = 0.72f)
            focused -> HubColors.Accent
            else -> HubColors.Surface.copy(alpha = 0.62f)
        },
        label = "edit-button-background",
    )

    Box(
        modifier = Modifier
            // Keep a real 48 dp hit target. TV IconButton is focus-first; this
            // explicit clickable surface is also reliable for direct touch.
            .size(48.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> HubColors.TextFaint
                focused -> HubColors.Text
                else -> tint
            },
            modifier = Modifier.size(24.dp),
        )
    }
}
