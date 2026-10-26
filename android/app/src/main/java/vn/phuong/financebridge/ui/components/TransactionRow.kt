package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.theme.FinanceTheme

@Composable
fun TransactionRow(
    transaction: UiTransaction,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val isIncome = transaction.type == TransactionType.THU
    val iconBgColor = if (isIncome) {
        FinanceTheme.colors.income.copy(alpha = 0.12f)
    } else {
        FinanceTheme.colors.expense.copy(alpha = 0.10f)
    }
    val iconTintColor = if (isIncome) {
        FinanceTheme.colors.income
    } else {
        FinanceTheme.colors.expense
    }

    val icon = getCategoryIcon(transaction.category)

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

private fun getCategoryIcon(category: String): ImageVector {
    val lower = category.lowercase()
    return when {
        lower.contains("ăn") || lower.contains("uống") || lower.contains("cà phê") -> Icons.Rounded.Restaurant
        lower.contains("xăng") || lower.contains("xe") || lower.contains("di chuyển") -> Icons.Rounded.LocalGasStation
        lower.contains("mua sắm") || lower.contains("siêu thị") -> Icons.Rounded.ShoppingCart
        lower.contains("lương") || lower.contains("tiền chuyển đến") || lower.contains("thu nhập") -> Icons.Rounded.Payments
        lower.contains("con") || lower.contains("học") -> Icons.Rounded.ChildCare
        lower.contains("điện") || lower.contains("nước") || lower.contains("hóa đơn") -> Icons.Rounded.ElectricBolt
        lower.contains("quà") || lower.contains("quyên góp") -> Icons.Rounded.CardGiftcard
        lower.contains("nhà") || lower.contains("gia đình") -> Icons.Rounded.Home
        lower.contains("sức khỏe") || lower.contains("thuốc") || lower.contains("khám") -> Icons.Rounded.MedicalServices
        lower.contains("giải trí") || lower.contains("phim") -> Icons.Rounded.SportsEsports
        else -> Icons.Rounded.Receipt
    }
}
