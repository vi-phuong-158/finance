package vn.phuong.financebridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

private val LightFinanceColors = FinanceColors(
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

private val DarkFinanceColors = FinanceColors(
    primary = PrimaryDark,
    primaryContainer = SurfaceElevatedDark,
    onPrimary = SurfaceDark,
    brandAccent = BrandAccent,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceCard = SurfaceElevatedDark,
    surfaceGlass = SurfaceGlassDark,
    borderGlass = BorderGlassDark,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textMuted = TextMutedDark,
    income = IncomeGreen,
    expense = ExpenseRed,
    warning = WarningGold,
    info = InfoBlue,
    heroStart = PrimaryDark,
    heroEnd = BrandAccent,
    shadowAmbient = ShadowAmbientDark,
    shadowSpot = ShadowSpotDark,
    isDark = true,
)

private val LightMaterialColorScheme = lightColorScheme(
    primary = Primary500,
    onPrimary = Color.White,
    primaryContainer = Primary100,
    onPrimaryContainer = Primary900,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Primary50,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderGlassLight,
)

private val DarkMaterialColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = SurfaceDark,
    primaryContainer = SurfaceElevatedDark,
    onPrimaryContainer = PrimaryDark,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceElevatedDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderGlassDark,
)

object FinanceTheme {
    val colors: FinanceColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFinanceColors.current

    val typography: FinanceTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalFinanceTypography.current

    val shapes: FinanceShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalFinanceShapes.current

    val spacing: FinanceSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalFinanceSpacing.current
}

@Composable
fun FinanceBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val financeColors = if (darkTheme) DarkFinanceColors else LightFinanceColors
    val materialColors = if (darkTheme) DarkMaterialColorScheme else LightMaterialColorScheme
    val typography = FinanceTypography()
    val shapes = FinanceShapes()
    val spacing = FinanceSpacing()

    CompositionLocalProvider(
        LocalFinanceColors provides financeColors,
        LocalFinanceTypography provides typography,
        LocalFinanceShapes provides shapes,
        LocalFinanceSpacing provides spacing,
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content,
        )
    }
}
