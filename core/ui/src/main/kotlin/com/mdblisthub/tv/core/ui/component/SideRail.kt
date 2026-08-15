package com.mdblisthub.tv.core.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mdblisthub.tv.core.ui.theme.HubColors

data class RailItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Mobile's main navigation — a bottom bar, not TV's side rail.
 *
 * The TV build's rail is fully hidden until D-pad focus lands on it, which
 * is what makes it free of screen cost while browsing. That trick doesn't
 * exist on a touchscreen: nothing tabs "into" focus on a tap, so a rail
 * built the same way would be permanently width-zero and permanently
 * unreachable. A bottom bar trades the TV rail's zero screen cost for being
 * always visible and always within thumb reach instead — the standard
 * mobile answer to the same "where does primary nav live" question.
 */
/** However many items the list holds, only this many get a slot on screen at once. */
private const val VisibleItemCount = 5

@Composable
fun BottomNavBar(
    items: List<RailItem>,
    selectedKey: String,
    onSelect: (RailItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val compact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Fixed per-item width, not SpaceEvenly: SpaceEvenly divides up
        // whatever width the row ends up with, which inside a horizontal
        // scroller is undefined — it let every icon cram together instead
        // of the intended five filling the screen edge to edge. Sizing each
        // item as 1/5 of the bar's own width is what makes "five visible,
        // the rest a swipe away" an actual guarantee instead of a hope.
        val itemWidth = maxWidth / VisibleItemCount

        Row(
            modifier = Modifier
                .background(HubColors.Surface.copy(alpha = 0.97f))
                .horizontalScroll(rememberScrollState())
                .padding(vertical = if (compact) 2.dp else 8.dp),
        ) {
            items.forEach { item ->
                BottomNavButton(
                    item = item,
                    selected = item.key == selectedKey,
                    onClick = { onSelect(item) },
                    compact = compact,
                    modifier = Modifier.width(itemWidth),
                )
            }
        }
    }
}

@Composable
private fun BottomNavButton(
    item: RailItem,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val tint = if (selected) HubColors.Accent else HubColors.TextFaint

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 2.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = if (compact) 2.dp else 6.dp),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(if (compact) 20.dp else 22.dp),
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
