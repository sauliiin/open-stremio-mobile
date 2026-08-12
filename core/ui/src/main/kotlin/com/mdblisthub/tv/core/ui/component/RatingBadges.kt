package com.mdblisthub.tv.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mdblisthub.tv.core.model.RatingBadge
import com.mdblisthub.tv.core.model.RatingTone
import com.mdblisthub.tv.core.ui.theme.HubColors

/** Aggregator scores, in the source's own colour so they read at a glance. */
@Composable
fun RatingBadges(
    ratings: List<RatingBadge>,
    modifier: Modifier = Modifier,
    max: Int = 5,
) {
    if (ratings.isEmpty()) return

    // Unconstrained on a TV screen, five badges at natural width always fit.
    // A phone's width is narrower than that sum, and a plain `Row` doesn't
    // shrink to cope — it squeezes whichever badge runs out of room, which
    // is what turned "Letterboxd" into a single letter per line. Scrolling
    // keeps every badge at its natural size instead of deforming one.
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ratings.take(max).forEach { badge ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(HubColors.Surface.copy(alpha = 0.75f))
                    .border(1.dp, HubColors.Border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    text = badge.display,
                    style = MaterialTheme.typography.titleMedium,
                    color = badge.tone.color(),
                )
                Text(
                    text = badge.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = HubColors.TextFaint,
                )
            }
        }
    }
}

private fun RatingTone.color(): Color = when (this) {
    RatingTone.IMDB -> HubColors.Imdb
    RatingTone.RT_FRESH -> HubColors.Fresh
    RatingTone.RT_ROTTEN -> HubColors.Rotten
    RatingTone.METACRITIC -> HubColors.Metacritic
    RatingTone.TRAKT -> HubColors.Trakt
    RatingTone.TMDB -> HubColors.Tmdb
    RatingTone.LETTERBOXD -> HubColors.Letterboxd
    RatingTone.NEUTRAL -> HubColors.TextDim
}
