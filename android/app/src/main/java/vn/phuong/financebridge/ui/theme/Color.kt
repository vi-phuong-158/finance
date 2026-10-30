package vn.phuong.financebridge.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Vigil Green fintech palette (UI V2 Phase F).
// Primary #078553 (Primary Strong) ensures WCAG AA contrast ratio (>4.5:1) for buttons, tabs, text on green.
// Accent #00A86B is reserved for decorations, gradients and bold headline text.
val Primary50 = Color(0xFFEFFAF5)   // Mint Light
val Primary100 = Color(0xFFDDF7EC)  // Mint Container
val Primary200 = Color(0xFFBCEFD9)
val Primary300 = Color(0xFF8CE1BE)
val Primary400 = Color(0xFF3DC48D)
val Primary500 = Color(0xFF078553)  // Primary Strong (WCAG AA compliant)
val Primary600 = Color(0xFF056E43)
val Primary700 = Color(0xFF055737)
val Primary800 = Color(0xFF06452D)
val Primary900 = Color(0xFF063A26)

val BrandAccent = Color(0xFF00A86B) // Decorative / large display
val HeroStart = Color(0xFF078553)
val HeroEnd = Color(0xFF00A86B)
val AccentMint = Color(0xFFDDF7EC)

// Neutral & surfaces (Light)
val BackgroundLight = Color(0xFFF5F7F6)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceCardLight = Color(0xFFFFFFFF)
// Floating navigation background (white alpha ~0.94, no blur required)
val SurfaceGlassLight = Color(0xF0FFFFFF)
val BorderGlassLight = Color(0xFFE6EBE8)

val TextPrimaryLight = Color(0xFF161B18)
val TextSecondaryLight = Color(0xFF66716B)
val TextMutedLight = Color(0xFF909A95)

// Semantic colors
val IncomeGreen = Color(0xFF078553)
val ExpenseRed = Color(0xFFE04848)
val WarningGold = Color(0xFFF59E0B)
val InfoBlue = Color(0xFF159BD7)

// Tinted shadow colors
val ShadowAmbientLight = Color(0x0C000000)
val ShadowSpotLight = Color(0x1A078553)

// Dark Theme tokens
val BackgroundDark = Color(0xFF0F1512)
val SurfaceDark = Color(0xFF161F1A)
val SurfaceElevatedDark = Color(0xFF1E2B24)
val SurfaceGlassDark = Color(0xF01E2B24)
val BorderGlassDark = Color(0xFF2C3E34)

val PrimaryDark = Color(0xFF4DD096)
val TextPrimaryDark = Color(0xFFF4F7F5)
val TextSecondaryDark = Color(0xFFC0CBC4)
val TextMutedDark = Color(0xFF86958C)

val ShadowAmbientDark = Color(0x1F000000)
val ShadowSpotDark = Color(0x2E000000)

@Immutable
data class FinanceColors(
    val primary: Color,
    val primaryContainer: Color,
    val onPrimary: Color,
    val brandAccent: Color,
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
    val heroStart: Color,
    val heroEnd: Color,
    val shadowAmbient: Color,
    val shadowSpot: Color,
    val isDark: Boolean,
)

val LocalFinanceColors = staticCompositionLocalOf {
    FinanceColors(
        primary = Primary500,
        primaryContainer = Primary100,
        onPrimary = Color.White,
        brandAccent = BrandAccent,
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
        heroStart = HeroStart,
        heroEnd = HeroEnd,
        shadowAmbient = ShadowAmbientLight,
        shadowSpot = ShadowSpotLight,
        isDark = false,
    )
}
