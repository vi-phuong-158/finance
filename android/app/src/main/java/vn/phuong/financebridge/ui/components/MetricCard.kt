package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.theme.FinanceTheme

@Composable
fun MetricCard(
    title: String,
    valueText: String,
    modifier: Modifier = Modifier,
    type: TransactionType? = null,
    badgeColor: Color = when (type) {
        TransactionType.THU -> FinanceTheme.colors.income
        TransactionType.CHI -> FinanceTheme.colors.expense
        null -> FinanceTheme.colors.primary
    },
) {
    GlassCard(
        modifier = modifier,
        shape = FinanceTheme.shapes.large,
        contentPadding = 14.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(FinanceTheme.shapes.pill)
                        .background(badgeColor),
                )
                Text(
                    text = title,
                    style = FinanceTheme.typography.caption,
                    color = FinanceTheme.colors.textMuted,
                )
            }
            AmountText(
                amountText = valueText,
                type = type,
                style = FinanceTheme.typography.titleMedium,
            )
        }
    }
}
