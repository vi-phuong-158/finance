package vn.phuong.financebridge.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class FinanceShapes(
    val radiusSmall: Dp = 12.dp,
    val radiusMedium: Dp = 16.dp,
    val radiusLarge: Dp = 20.dp,
    val radiusXLarge: Dp = 24.dp,
    val radiusXXL: Dp = 28.dp,
    val radiusPill: Dp = 999.dp,

    val small: RoundedCornerShape = RoundedCornerShape(12.dp),
    val medium: RoundedCornerShape = RoundedCornerShape(16.dp),
    val large: RoundedCornerShape = RoundedCornerShape(20.dp),
    val xLarge: RoundedCornerShape = RoundedCornerShape(24.dp),
    val xxl: RoundedCornerShape = RoundedCornerShape(28.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(999.dp),
)

val LocalFinanceShapes = staticCompositionLocalOf { FinanceShapes() }
