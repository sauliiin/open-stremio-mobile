package com.mdblisthub.tv.core.ui.component

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mdblisthub.tv.core.ui.theme.HubColors
import com.mdblisthub.tv.core.ui.theme.HubTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

data class RailItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

/** Scroll-aware state for OmniStream's floating root navigation. */
@Stable
class HubNavBarScrollState {
    var labelVisibility by mutableFloatStateOf(1f)
        private set

    private var accumulatedDelta = 0f

    fun expand() {
        labelVisibility = 1f
        accumulatedDelta = 0f
    }

    fun collapse() {
        labelVisibility = 0f
        accumulatedDelta = 0f
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val deltaY = available.y
            if (deltaY == 0f) return Offset.Zero

            if ((deltaY > 0f) != (accumulatedDelta > 0f) && accumulatedDelta != 0f) {
                accumulatedDelta = deltaY
            } else {
                accumulatedDelta += deltaY
            }

            when {
                accumulatedDelta < -SCROLL_THRESHOLD && labelVisibility != 0f -> collapse()
                accumulatedDelta > SCROLL_THRESHOLD && labelVisibility != 1f -> expand()
            }
            return Offset.Zero
        }
    }

    private companion object {
        const val SCROLL_THRESHOLD = 56f
    }
}

@Composable
fun rememberHubNavBarScrollState(): HubNavBarScrollState = remember { HubNavBarScrollState() }

/**
 * Floating, translucent root navigation. Haze is optional so low-end devices
 * and previews retain the same contrast with a slightly more opaque fallback.
 */
@Composable
fun BottomNavBar(
    items: List<RailItem>,
    selectedKey: String,
    onSelect: (RailItem) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: HubNavBarScrollState? = null,
    hazeState: HazeState? = null,
) {
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val labelFraction by animateFloatAsState(
        targetValue = if (landscape) 0f else scrollState?.labelVisibility ?: 1f,
        animationSpec = tween(HubTokens.Motion.navigationMillis, easing = HubTokens.Motion.standard),
        label = "bottom-nav-labels",
    )
    val barMaxWidth by animateDpAsState(
        targetValue = if (labelFraction < 0.5f) 390.dp else 560.dp,
        animationSpec = tween(HubTokens.Motion.navigationMillis, easing = HubTokens.Motion.standard),
        label = "bottom-nav-width",
    )
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pillShape = RoundedCornerShape(HubTokens.Radius.full)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (landscape) 64.dp else 18.dp,
                end = if (landscape) 64.dp else 18.dp,
                bottom = bottomInset + if (landscape) 5.dp else 10.dp,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val glassModifier = Modifier
            .widthIn(max = barMaxWidth)
            .fillMaxWidth()
            .clip(pillShape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) { blurRadius = 24.dp }
                } else {
                    Modifier
                },
            )
            .background(
                HubColors.Surface.copy(
                    alpha = if (hazeState == null) HubTokens.Opacity.glassStrong else HubTokens.Opacity.glass,
                ),
            )
            .background(
                Brush.horizontalGradient(
                    listOf(
                        HubColors.Accent.copy(alpha = 0.055f),
                        Color.Transparent,
                        HubColors.Accent2.copy(alpha = 0.045f),
                    ),
                ),
            )

        Row(
            modifier = glassModifier.padding(horizontal = 7.dp, vertical = if (landscape) 4.dp else 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = item.key == selectedKey
                val iconColor by animateColorAsState(
                    targetValue = if (selected) HubColors.AccentSoft else HubColors.TextFaint,
                    animationSpec = tween(HubTokens.Motion.fastMillis, easing = HubTokens.Motion.standard),
                    label = "nav-icon-${item.key}",
                )
                val selectedBackground by animateColorAsState(
                    targetValue = if (selected) {
                        HubColors.Accent.copy(alpha = HubTokens.Opacity.selected)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(HubTokens.Motion.normalMillis, easing = HubTokens.Motion.standard),
                    label = "nav-background-${item.key}",
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(pillShape)
                        .background(selectedBackground)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(item) },
                        )
                        .padding(horizontal = 4.dp, vertical = if (landscape) 7.dp else 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = iconColor,
                        modifier = Modifier.size(HubTokens.Size.navIcon),
                    )
                    if (labelFraction > 0.01f) {
                        Spacer(Modifier.height(3.dp * labelFraction))
                        Box(
                            modifier = Modifier
                                .height(14.dp * labelFraction)
                                .alpha(labelFraction),
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = iconColor,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
