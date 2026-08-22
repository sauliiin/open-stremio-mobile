package com.mdblisthub.tv.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp

/**
 * OmniStream's visual vocabulary.
 *
 * These values deliberately keep the app's denser, artwork-first identity
 * while giving every screen the same rhythm. Components should consume these
 * tokens instead of inventing one-off radii, opacities and animation timings.
 */
object HubTokens {
    object Space {
        val hairline = 0.5.dp
        val xxs = 2.dp
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 20.dp
        val xxl = 24.dp
        val huge = 32.dp
        val giant = 48.dp
    }

    object Radius {
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 22.dp
        val panel = 26.dp
        val full = 999.dp
    }

    object Opacity {
        const val glass = 0.64f
        const val glassStrong = 0.84f
        const val selected = 0.16f
        const val pressed = 0.12f
        const val subtle = 0.07f
        const val disabled = 0.38f
        const val scrim = 0.72f
    }

    object Motion {
        const val fastMillis = 160
        const val normalMillis = 260
        const val navigationMillis = 300
        const val slowMillis = 420
        const val cinematicMillis = 700

        val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val decelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
    }

    object Size {
        val touchTarget = 48.dp
        val navIcon = 26.dp
        val navHeight = 68.dp
        val contentBottomClearance = 104.dp
    }

    object Breakpoint {
        val largePhone = 420.dp
        val tablet = 720.dp
        val wide = 960.dp
    }
}
