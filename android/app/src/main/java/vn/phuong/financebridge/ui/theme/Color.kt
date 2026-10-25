package vn.phuong.financebridge.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Base 500k VND banknote green inspired palette
val Primary50 = Color(0xFFF3FBF6)
val Primary100 = Color(0xFFDFF3E8)
val Primary200 = Color(0xFFBDE6CF)
val Primary300 = Color(0xFF8FD2AC)
val Primary400 = Color(0xFF63BA88)
val Primary500 = Color(0xFF2F8F63)
val Primary600 = Color(0xFF257A53)
val Primary700 = Color(0xFF1D6243)
val Primary800 = Color(0xFF174E36)
val Primary900 = Color(0xFF113B29)

val AccentMint = Color(0xFF7ED6A7)

// Neutral & surfaces (Light)
val BackgroundLight = Color(0xFFF4F8F5)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceCardLight = Color(0xFFFFFFFF)
val SurfaceGlassLight = Color(0xE6FFFFFF) // 90% opacity for soft glass
val BorderGlassLight = Color(0x332F8F63) // Subtle green-tinted border

val TextPrimaryLight = Color(0xFF123126)
val TextSecondaryLight = Color(0xFF355347)
val TextMutedLight = Color(0xFF6E8B7E)

// Semantic colors
val IncomeGreen = Color(0xFF1F9D61)
val ExpenseRed = Color(0xFFE85D5D)
val WarningGold = Color(0xFFF4B740)
val InfoBlue = Color(0xFF4F8FD7)

// Dark Theme tokens
val BackgroundDark = Color(0xFF0F1713)
val SurfaceDark = Color(0xFF16231C)
val SurfaceElevatedDark = Color(0xFF1C2D24)
val SurfaceGlassDark = Color(0xD916231C) // 85% opacity
val BorderGlassDark = Color(0x3358B980)

val PrimaryDark = Color(0xFF58B980)
val TextPrimaryDark = Color(0xFFF2FBF5)
val TextSecondaryDark = Color(0xFFB5CABE)
val TextMutedDark = Color(0xFF7F978A)

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
