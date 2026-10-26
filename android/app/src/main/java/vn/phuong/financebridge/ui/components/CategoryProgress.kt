package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.phuong.financebridge.ui.model.UiCategoryMetric
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.theme.Primary400
import vn.phuong.financebridge.ui.theme.Primary500

@Composable
fun CategoryProgress(
    category: UiCategoryMetric,
    modifier: Modifier = Modifier,
    barColor: Color = Primary500,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Label & Amount
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = category.name,
                    style = FinanceTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = FinanceTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "(${category.transactionCount})",
                    style = FinanceTheme.typography.caption,
                    color = FinanceTheme.colors.textMuted,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = category.formattedAmount,
                style = FinanceTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = FinanceTheme.colors.textPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Progress Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(FinanceTheme.shapes.pill)
                .background(FinanceTheme.colors.borderGlass.copy(alpha = 0.2f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = category.percentage.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(FinanceTheme.shapes.pill)
                    .background(barColor),
            )
        }
    }
}
