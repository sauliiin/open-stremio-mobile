package com.mdblisthub.tv.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/**
 * Type sized for a hand, not a couch.
 *
 * This is the mobile build's fork of the TV app's typography: the reference
 * here is a phone held at arm's length, not a 1080p panel three metres away,
 * so every size below is scaled down from the TV original rather than reused
 * as-is — text sized for a sofa reads oversized and wastes space on a 6" screen.
 */
private val HubTypography = Typography(
    displayLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

/** Screen edge inset. No overscan to budget for on a phone, just thumb reach. */
object HubDimens {
    val ScreenPaddingHorizontal = 16.dp
    val ScreenPaddingVertical = 14.dp
    val RowSpacing = 18.dp
    val CardSpacing = 10.dp
    /** Primefly follows a streaming-service shelf: wide 16:9 art instead of posters. */
    // Sized so three shelves (row title + poster + caption) land inside one
    // phone screen at a time, the way Netflix/Prime's own mobile home does —
    // tall enough to read comfortably, short enough that a third row is
    // always at least partly visible as the "there's more" cue to scroll.
    //
    // Landscape reads configuration so each theme can keep its native card
    // shape while making use of the height recovered from the compact nav.
    // Both landscape formats are scaled by roughly the same 12%, so neither
    // portrait posters nor Primefly's 16:9 artwork is stretched.
    val PosterWidth: Dp
        @Composable get() {
            val config = LocalConfiguration.current
            val isLandscape = config.screenWidthDp > config.screenHeightDp
            if (HubColors.isPrimefly) return if (isLandscape) 146.dp else 130.dp
            return if (isLandscape) 73.dp else 108.dp
        }
    val PosterHeight: Dp
        @Composable get() {
            val config = LocalConfiguration.current
            val isLandscape = config.screenWidthDp > config.screenHeightDp
            if (HubColors.isPrimefly) return if (isLandscape) 82.dp else 73.dp
            return if (isLandscape) 110.dp else 162.dp
        }
}

@Composable
fun HubTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = HubColors.Accent,
        onPrimary = HubColors.Text,
        secondary = HubColors.Accent2,
        background = HubColors.Background,
        onBackground = HubColors.Text,
        surface = HubColors.Surface,
        onSurface = HubColors.Text,
        surfaceVariant = HubColors.SurfaceStrong,
        border = HubColors.Border,
    )
    MaterialTheme(colorScheme = colorScheme, typography = HubTypography) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            HubColors.Background,
                            HubColors.Surface.copy(alpha = 0.44f),
                            HubColors.Background,
                        ),
                    ),
                ),
        ) { content() }
    }
}
