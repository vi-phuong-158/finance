package vn.phuong.financebridge.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class FinanceSpacing(
    val s4: Dp = 4.dp,
    val s8: Dp = 8.dp,
    val s12: Dp = 12.dp,
    val s16: Dp = 16.dp,
    val s20: Dp = 20.dp,
    val s24: Dp = 24.dp,
    val s32: Dp = 32.dp,
    val s40: Dp = 40.dp,
    val s48: Dp = 48.dp,

    // Elevation tokens
    val elevationLow: Dp = 2.dp,
    val elevationMedium: Dp = 4.dp,
    val elevationFloating: Dp = 8.dp,
)

val LocalFinanceSpacing = staticCompositionLocalOf { FinanceSpacing() }
