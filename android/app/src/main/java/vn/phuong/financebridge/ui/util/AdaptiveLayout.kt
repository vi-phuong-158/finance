package vn.phuong.financebridge.ui.util

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Screen width tiers for responsive layout, specifically designed for Samsung Galaxy Z Fold 4.
 * - NARROW_FOLD: < 360dp (Z Fold 4 cover screen is ~344dp width).
 * - STANDARD_PHONE: 360dp..599dp (Typical modern Android phone).
 * - EXPANDED_FOLD: >= 600dp (Z Fold 4 unfolded inner screen is ~670-700dp).
 */
enum class WindowWidthTier {
    NARROW_FOLD,
    STANDARD_PHONE,
    EXPANDED_FOLD,
}

object AdaptiveLayoutDefaults {
    val MaxContentWidthDp: Dp = 600.dp
    val MaxNavWidthDp: Dp = 480.dp
    val NarrowGutterDp: Dp = 12.dp
    val StandardGutterDp: Dp = 16.dp
    val ExpandedGutterDp: Dp = 20.dp
}

/**
 * Pure JVM function mapping screen width in dp to WindowWidthTier.
 * Testable in pure JVM unit tests without Android runtime.
 */
fun computeWindowWidthTier(widthDp: Int): WindowWidthTier {
    return when {
        widthDp < 360 -> WindowWidthTier.NARROW_FOLD
        widthDp < 600 -> WindowWidthTier.STANDARD_PHONE
        else -> WindowWidthTier.EXPANDED_FOLD
    }
}

/**
 * Computes horizontal gutter padding for a given screen width.
 */
fun computeHorizontalGutterDp(widthDp: Int): Int {
    return when (computeWindowWidthTier(widthDp)) {
        WindowWidthTier.NARROW_FOLD -> 12
        WindowWidthTier.STANDARD_PHONE -> 16
        WindowWidthTier.EXPANDED_FOLD -> 20
    }
}

@Composable
fun rememberWindowWidthTier(): WindowWidthTier {
    val configuration = LocalConfiguration.current
    return computeWindowWidthTier(configuration.screenWidthDp)
}

/**
 * Constrains content container width to maximum ~600dp centered on wide screens (Fold inner display).
 */
fun Modifier.adaptiveContentWidth(): Modifier {
    return this.widthIn(max = AdaptiveLayoutDefaults.MaxContentWidthDp)
}

/**
 * Constrains floating bottom navigation width to maximum ~480dp centered on wide screens.
 */
fun Modifier.adaptiveNavWidth(): Modifier {
    return this.widthIn(max = AdaptiveLayoutDefaults.MaxNavWidthDp)
}
