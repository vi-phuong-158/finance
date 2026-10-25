package vn.phuong.financebridge.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Cyan/blue finance palette derived from DESIGN.md and the MISA layout reference.
// Keep token names stable so existing UI components remain source-compatible.
val Primary50 = Color(0xFFF1FBFE)
val Primary100 = Color(0xFFDDF6FD)
val Primary200 = Color(0xFFB8ECF8)
val Primary300 = Color(0xFF83DDF1)
val Primary400 = Color(0xFF43C8E8)
val Primary500 = Color(0xFF08A9D6)
val Primary600 = Color(0xFF078CC5)
val Primary700 = Color(0xFF0875A7)
val Primary800 = Color(0xFF0B6088)
val Primary900 = Color(0xFF104F70)

val HeroStart = Color(0xFF177CF2)
val HeroEnd = Color(0xFF18C8D9)
val AccentMint = Color(0xFF9AE8F4)

// Neutral & surfaces (Light)
val BackgroundLight = Color(0xFFF4F6F8)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceCardLight = Color(0xFFFFFFFF)
// Glass remains available as a secondary effect, but cards should read as solid white first.
val SurfaceGlassLight = Color(0xFAFFFFFF)
val BorderGlassLight = Color(0xFFE7EBEF)

val TextPrimaryLight = Color(0xFF151A23)
val TextSecondaryLight = Color(0xFF687386)
val TextMutedLight = Color(0xFF98A1AE)

// Semantic colors
val IncomeGreen = Color(0xFF28A765)
val ExpenseRed = Color(0xFFEF5B63)
val WarningGold = Color(0xFFF59E0B)
val InfoBlue = Color(0xFF159BD7)

// Dark Theme tokens
val BackgroundDark = Color(0xFF0E151B)
val SurfaceDark = Color(0xFF151E26)
val SurfaceElevatedDark = Color(0xFF1B2731)
val SurfaceGlassDark = Color(0xF21B2731)
val BorderGlassDark = Color(0xFF2B3945)

val PrimaryDark = Color(0xFF4EC9E7)
val TextPrimaryDark = Color(0xFFF4F7FA)
val TextSecondaryDark = Color(0xFFC2CBD3)
val TextMutedDark = Color(0xFF8895A0)

@Immutable
data class FinanceColors(
    val primary: Color,
    val primaryContainer: Color,
    val onPrimary: Color,
    val background: Color,
    val surface: Color,
    val surfaceCard: Color,
    val surfaceGlass: Color,
    val borderGlass: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val income: Color,
    val expense: Color,
    val warning: Color,
    val info: Color,
    val isDark: Boolean,
)

val LocalFinanceColors = staticCompositionLocalOf {
    FinanceColors(
        primary = Primary500,
        primaryContainer = Primary100,
        onPrimary = Color.White,
        background = BackgroundLight,
        surface = SurfaceLight,
        surfaceCard = SurfaceCardLight,
        surfaceGlass = SurfaceGlassLight,
        borderGlass = BorderGlassLight,
        textPrimary = TextPrimaryLight,
        textSecondary = TextSecondaryLight,
        textMuted = TextMutedLight,
        income = IncomeGreen,
        expense = ExpenseRed,
        warning = WarningGold,
        info = InfoBlue,
        isDark = false,
    )
}
