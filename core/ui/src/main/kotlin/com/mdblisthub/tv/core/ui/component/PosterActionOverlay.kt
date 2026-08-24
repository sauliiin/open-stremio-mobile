package com.mdblisthub.tv.core.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.mdblisthub.tv.core.ui.theme.HubColors
import com.mdblisthub.tv.core.ui.theme.HubTokens
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The pressed card's on-screen geometry at the moment it was held, captured by [PosterCard]. */
data class PosterCardAnchor(
    val boundsInRoot: Rect,
    val cornerRadius: Dp,
    val imageUrl: String?,
)

class PosterOverlayAction(
    val label: String,
    val icon: ImageVector,
    val isDestructive: Boolean = false,
    val onSelected: () -> Unit,
)

class PosterOverlayRequest(
    val anchor: PosterCardAnchor,
    val title: String,
    val subtitle: String? = null,
    val actions: List<PosterOverlayAction>,
)

/**
 * Hand-off point between a long-pressed [PosterCard] anywhere in the app and
 * the single [PosterActionOverlay] mounted once near the app root. A screen
 * builds the actions available for the item it long-pressed and calls
 * [show]; the overlay itself owns when and how that gets presented.
 */
@Stable
object PosterActionOverlayHost {
    var request by mutableStateOf<PosterOverlayRequest?>(null)
        private set

    fun show(request: PosterOverlayRequest) {
        this.request = request
    }

    fun dismiss() {
        request = null
    }
}

private val PosterOverlayExpandSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 340f,
)
private val PosterOverlayCollapseSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 460f,
)
private val PosterOverlayMenuSpring = spring<Float>(
    dampingRatio = 0.8f,
    stiffness = 420f,
)
private val PosterOverlayCornerRadius = HubTokens.Radius.lg

/**
 * Apple-tvOS-style long-press preview. The held poster lifts off the shelf
 * and springs to the centre of the screen while an action list cascades in
 * beneath it; releasing an action (or backing out) sends it home again.
 *
 * A real [Dialog] rather than a plain full-screen composable: its window
 * owns key/focus dispatch for as long as it is shown, so the shelf behind it
 * cannot steal D-pad focus back — the same reason the app's other
 * confirmation dialogs use one. The trade-off is that the shelf behind can
 * only be dimmed, not blurred — Haze blurs within one window's draw tree,
 * and a dialog is a second window.
 */
@Composable
fun PosterActionOverlay() {
    val request = PosterActionOverlayHost.request ?: return
    Dialog(
        onDismissRequest = { PosterActionOverlayHost.dismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val view = LocalView.current
        LaunchedEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        }
        PosterActionOverlayBody(request = request, onDismissed = PosterActionOverlayHost::dismiss)
    }
}

@Composable
private fun PosterActionOverlayBody(
    request: PosterOverlayRequest,
    onDismissed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val zoom = remember { Animatable(0f) }
    val scrim = remember { Animatable(0f) }
    val menu = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var slotBounds by remember { mutableStateOf<Rect?>(null) }
    val firstActionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        launch { scrim.animateTo(1f, tween(HubTokens.Motion.normalMillis, easing = HubTokens.Motion.standard)) }
        launch { zoom.animateTo(1f, PosterOverlayExpandSpring) }
        launch {
            delay(70)
            menu.animateTo(1f, PosterOverlayMenuSpring)
        }
        firstActionFocusRequester.requestFocus()
    }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            coroutineScope {
                launch { menu.animateTo(0f, tween(120)) }
                launch {
                    delay(60)
                    scrim.animateTo(0f, tween(HubTokens.Motion.normalMillis, easing = HubTokens.Motion.standard))
                }
                launch { zoom.animateTo(0f, PosterOverlayCollapseSpring) }
            }
            onDismissed()
        }
    }

    fun select(action: PosterOverlayAction) {
        if (closing) return
        action.onSelected()
        close()
    }

    BackHandler(enabled = true) { close() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
            .pointerInput(Unit) { detectTapGestures { close() } },
    ) {
        val anchorBounds = request.anchor.boundsInRoot
        val anchorAspect = anchorBounds
            .takeIf { it.height > 0f }
            ?.let { it.width / it.height }
            ?: 0.675f
        val aspect = anchorAspect.coerceIn(0.35f, 2.4f)
        val maxPosterWidth = maxWidth * 0.62f
        val posterHeight = min(maxPosterWidth / aspect, maxHeight * 0.5f)
        val posterWidth = posterHeight * aspect
        val menuWidth = min(300.dp, maxWidth - HubTokens.Space.giant)
        val columnWidth = max(posterWidth, menuWidth)

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    HubColors.Background.copy(
                        alpha = HubTokens.Opacity.scrim * scrim.value.coerceIn(0f, 1f),
                    ),
                ),
        )

        Column(
            modifier = Modifier.align(Alignment.Center).width(columnWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Invisible slot marking where the zoomed poster comes to rest.
            Box(
                Modifier
                    .size(width = posterWidth, height = posterHeight)
                    .onGloballyPositioned { slotBounds = it.boundsInRoot() },
            )

            Spacer(Modifier.height(HubTokens.Space.lg))

            // The poster already identifies the selection. Keeping the title
            // here duplicated it and pushed the actions below the fold on a
            // phone, so the compact menu goes straight from preview to actions.
            Spacer(Modifier.height(HubTokens.Space.sm))

            Column(
                modifier = Modifier
                    .width(menuWidth)
                    .graphicsLayer {
                        val progress = menu.value
                        val clamped = progress.coerceIn(0f, 1f)
                        alpha = clamped
                        val scale = 0.62f + 0.38f * progress
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                HubColors.SurfaceStrong.copy(alpha = 0.92f),
                                HubColors.Surface.copy(alpha = 0.88f),
                            ),
                        ),
                        RoundedCornerShape(HubTokens.Radius.panel),
                    )
                    .border(
                        HubTokens.Space.hairline,
                        HubColors.Border.copy(alpha = 0.6f),
                        RoundedCornerShape(HubTokens.Radius.panel),
                    ),
            ) {
                request.actions.forEachIndexed { index, action ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(HubTokens.Space.hairline)
                                .background(HubColors.Border.copy(alpha = 0.5f)),
                        )
                    }
                    PosterOverlayActionRow(
                        action = action,
                        focusRequester = if (index == 0) firstActionFocusRequester else null,
                        onSelected = { select(action) },
                        modifier = Modifier.graphicsLayer {
                            val stagger = index * 0.08f
                            val progress = ((menu.value.coerceIn(0f, 1f) - stagger) / (1f - stagger))
                                .coerceIn(0f, 1f)
                            alpha = progress
                            translationY = (1f - progress) * HubTokens.Space.sm.toPx()
                        },
                    )
                }
            }
        }

        // The travelling poster itself, drawn above the slot column.
        Box(
            modifier = Modifier
                .size(width = posterWidth, height = posterHeight)
                .graphicsLayer {
                    val slot = slotBounds
                    if (slot == null || slot.width <= 0f) {
                        alpha = 0f
                        return@graphicsLayer
                    }
                    val progress = zoom.value
                    val clamped = progress.coerceIn(0f, 1f)
                    val scale = lerp(anchorBounds.width / slot.width, 1f, progress)
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = lerp(anchorBounds.left, slot.left, progress) - rootOrigin.x
                    translationY = lerp(anchorBounds.top, slot.top, progress) - rootOrigin.y
                    alpha = clamped
                    val finalRadius = PosterOverlayCornerRadius.toPx()
                    val startRadius = request.anchor.cornerRadius.toPx()
                    val apparentRadius = lerp(startRadius, finalRadius, clamped)
                    shape = RoundedCornerShape(if (scale > 0f) apparentRadius / scale else apparentRadius)
                    clip = true
                    shadowElevation = HubTokens.Space.xl.toPx() * clamped
                },
        ) {
            if (request.anchor.imageUrl != null) {
                AsyncImage(
                    model = request.anchor.imageUrl,
                    contentDescription = request.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(HubColors.Surface),
                )
            } else {
                Box(Modifier.fillMaxSize().background(HubColors.Surface))
            }
        }
    }
}

@Composable
private fun PosterOverlayActionRow(
    action: PosterOverlayAction,
    focusRequester: FocusRequester?,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val contentColor = if (action.isDestructive) HubColors.Rotten else HubColors.Text
    val background by animateColorAsState(
        targetValue = if (focused) HubColors.Accent.copy(alpha = HubTokens.Opacity.selected) else Color.Transparent,
        animationSpec = tween(HubTokens.Motion.fastMillis, easing = HubTokens.Motion.standard),
        label = "poster-overlay-action-background",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onSelected,
            )
            .padding(horizontal = HubTokens.Space.xl, vertical = HubTokens.Space.lg),
        horizontalArrangement = Arrangement.spacedBy(HubTokens.Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
    }
}
