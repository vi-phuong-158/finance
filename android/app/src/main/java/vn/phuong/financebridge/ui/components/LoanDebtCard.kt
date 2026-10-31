package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.MonthlySummaryUtil

@Composable
fun LoanDebtCard(
    outstandingPrincipal: Long?,
    isError: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FinanceCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = FinanceTheme.shapes.large,
        contentPadding = 14.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(FinanceTheme.shapes.medium)
                    .background(FinanceTheme.colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountBalance,
                    contentDescription = "Dư nợ vay",
                    tint = FinanceTheme.colors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Dư nợ vay",
                    style = FinanceTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = FinanceTheme.colors.textSecondary,
                )
                if (isError) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "—",
                            style = FinanceTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = FinanceTheme.colors.textPrimary,
                        )
                        Text(
                            text = "Chưa tải được",
                            style = FinanceTheme.typography.caption.copy(fontSize = 11.sp),
                            color = FinanceTheme.colors.textMuted,
                        )
                    }
                } else {
                    AmountText(
                        amountText = MonthlySummaryUtil.formatOutstandingDebt(outstandingPrincipal),
                        style = FinanceTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = FinanceTheme.colors.textPrimary,
                        autoShrink = true,
                        minScaleRatio = 0.75f,
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = "Mở Vay & lãi",
                tint = FinanceTheme.colors.textMuted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
