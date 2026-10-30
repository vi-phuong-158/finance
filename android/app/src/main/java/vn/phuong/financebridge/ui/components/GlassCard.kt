package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vn.phuong.financebridge.ui.theme.FinanceTheme

/**
 * Legacy name retained for source compatibility. The default treatment is an opaque
 * surface card with a soft border, matching the modern fintech design language.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = FinanceTheme.shapes.xLarge,
    borderWidth: Dp = 1.dp,
    elevation: Dp = FinanceTheme.spacing.elevationLow,
    containerColor: Color = FinanceTheme.colors.surfaceCard,
    borderColor: Color = FinanceTheme.colors.borderGlass,
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = FinanceTheme.colors.shadowAmbient,
                spotColor = FinanceTheme.colors.shadowSpot,
            )
            .clip(shape)
            .background(containerColor)
            .border(
                border = BorderStroke(borderWidth, borderColor),
                shape = shape,
            )
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun FinanceCard(
    modifier: Modifier = Modifier,
    shape: Shape = FinanceTheme.shapes.large,
    elevation: Dp = FinanceTheme.spacing.elevationLow,
    containerColor: Color = FinanceTheme.colors.surfaceCard,
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = FinanceTheme.colors.shadowAmbient,
                spotColor = FinanceTheme.colors.shadowSpot,
            ),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, FinanceTheme.colors.borderGlass),
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}
