package com.mdblisthub.tv.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mdblisthub.tv.core.ui.theme.HubColors
import com.mdblisthub.tv.core.ui.theme.HubTokens

/**
 * A focusable button.
 *
 * Written by hand rather than taken from tv-material so that focus reads the
 * same as a poster card does — filled accent, not an outline. Consistency of
 * the focus cue matters more on a remote than component provenance.
 */
@Composable
fun HubButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()

    val background by animateColorAsState(
        targetValue = when {
            !enabled -> HubColors.Surface.copy(alpha = 0.4f)
            focused || primary -> HubColors.Accent
            else -> HubColors.Surface.copy(alpha = HubTokens.Opacity.glass)
        },
        animationSpec = tween(HubTokens.Motion.fastMillis, easing = HubTokens.Motion.standard),
        label = "button-background",
    )
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.98f
            focused -> 1.025f
            else -> 1f
        },
        animationSpec = tween(HubTokens.Motion.fastMillis, easing = HubTokens.Motion.standard),
        label = "button-scale",
    )
    val highlighted = enabled && (focused || primary)
    val shape = RoundedCornerShape(HubTokens.Radius.lg)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = HubTokens.Size.touchTarget)
            .clip(shape)
            .then(
                if (highlighted) {
                    Modifier.background(
                        Brush.horizontalGradient(
                            listOf(HubColors.Accent, HubColors.Accent2),
                        ),
                    )
                } else {
                    Modifier.background(background)
                },
            )
            .border(
                width = 1.dp,
                color = when {
                    !enabled -> HubColors.Border.copy(alpha = 0.45f)
                    highlighted -> HubColors.AccentSoft.copy(alpha = 0.72f)
                    else -> HubColors.Border
                },
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                !enabled -> HubColors.TextFaint
                highlighted -> HubColors.Text
                else -> HubColors.TextDim
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
