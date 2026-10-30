package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.CategoryVisuals

@Composable
fun TransactionRow(
    transaction: UiTransaction,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val icon = CategoryVisuals.icon(transaction.category)
    val iconTintColor = CategoryVisuals.color(transaction.category, FinanceTheme.colors)
    val iconBgColor = iconTintColor.copy(alpha = 0.12f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(FinanceTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Icon / Category Avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(FinanceTheme.shapes.medium)
                .background(iconBgColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = transaction.category,
                tint = iconTintColor,
                modifier = Modifier.size(24.dp),
            )
        }

        // Details: Title & Subtitle
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = transaction.title.ifBlank { transaction.category },
                style = FinanceTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = FinanceTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "${transaction.accountOrBank} • ${transaction.formattedDate}",
                    style = FinanceTheme.typography.caption,
                    color = FinanceTheme.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (transaction.isDuplicateCandidate) {
                    Box(
                        modifier = Modifier
                            .clip(FinanceTheme.shapes.pill)
                            .background(FinanceTheme.colors.warning.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "Nghi trùng",
                            style = FinanceTheme.typography.caption.copy(fontSize = 10.sp),
                            color = FinanceTheme.colors.warning,
                        )
                    }
                }
            }
        }

        // Amount
        AmountText(
            amountText = transaction.formattedAmount,
            type = transaction.type,
            style = FinanceTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
        )
    }
}
