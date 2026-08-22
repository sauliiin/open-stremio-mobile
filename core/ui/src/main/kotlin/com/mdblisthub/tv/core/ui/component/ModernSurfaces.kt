package com.mdblisthub.tv.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mdblisthub.tv.core.ui.theme.HubColors
import com.mdblisthub.tv.core.ui.theme.HubTokens

/** Translucent OmniStream panel used for forms, settings groups and dialogs. */
@Composable
fun HubGlassCard(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(HubTokens.Radius.xl)
    val border by animateColorAsState(
        targetValue = if (focused) HubColors.Accent.copy(alpha = 0.82f) else HubColors.Border.copy(alpha = 0.82f),
        animationSpec = tween(HubTokens.Motion.fastMillis, easing = HubTokens.Motion.standard),
        label = "glass-card-border",
    )
    val base = Modifier
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    HubColors.SurfaceStrong.copy(alpha = if (strong) 0.90f else 0.74f),
                    HubColors.Surface.copy(alpha = if (strong) 0.86f else 0.58f),
                ),
            ),
        )
        .border(1.dp, border, shape)
        .drawWithCache {
            val shine = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.08f),
                0.36f to Color.Transparent,
                1f to HubColors.Accent.copy(alpha = 0.035f),
            )
            onDrawWithContent {
                drawContent()
                drawRoundRect(
                    brush = shine,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        HubTokens.Radius.xl.toPx(),
                        HubTokens.Radius.xl.toPx(),
                    ),
                    style = Stroke(width = HubTokens.Space.hairline.toPx()),
                )
            }
        }
        .let { baseModifier ->
            if (onClick == null) baseModifier else baseModifier.clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
        }

    Column(
        modifier = modifier.then(base),
        content = content,
    )
}

@Composable
fun HubSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = HubColors.AccentSoft,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun HubScreenHeading(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HubTokens.Space.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                color = HubColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HubColors.TextDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(HubTokens.Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** A Material-independent switch that keeps touch and D-pad behavior aligned. */
@Composable
fun HubToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val track by animateColorAsState(
        targetValue = when {
            !enabled -> HubColors.Border.copy(alpha = HubTokens.Opacity.disabled)
            checked -> HubColors.Accent
            else -> HubColors.Border
        },
        animationSpec = tween(HubTokens.Motion.fastMillis, easing = HubTokens.Motion.standard),
        label = "toggle-track",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(HubTokens.Motion.normalMillis, easing = HubTokens.Motion.standard),
        label = "toggle-thumb",
    )
    Box(
        modifier = modifier
            .width(48.dp)
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(track)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .background(if (checked) HubColors.Text else HubColors.TextDim, CircleShape),
        )
    }
}

@Composable
fun HubSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    leading: (@Composable BoxScope.() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val background by animateColorAsState(
        targetValue = if (focused) HubColors.Accent.copy(alpha = HubTokens.Opacity.selected) else Color.Transparent,
        animationSpec = tween(HubTokens.Motion.fastMillis, easing = HubTokens.Motion.standard),
        label = "setting-row-background",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(HubTokens.Radius.md))
            .let { base ->
                if (onClick == null) base else base.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                )
            }
            .padding(horizontal = HubTokens.Space.lg, vertical = HubTokens.Space.md),
        horizontalArrangement = Arrangement.spacedBy(HubTokens.Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(HubColors.Accent.copy(alpha = HubTokens.Opacity.selected), RoundedCornerShape(HubTokens.Radius.md)),
                contentAlignment = Alignment.Center,
                content = leading,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HubTokens.Space.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = HubColors.Text,
                fontWeight = FontWeight.Medium,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HubColors.TextDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = trailing,
        )
    }
}
