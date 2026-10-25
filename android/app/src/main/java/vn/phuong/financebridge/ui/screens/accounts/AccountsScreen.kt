package vn.phuong.financebridge.ui.screens.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.phuong.financebridge.ui.components.FinanceCard
import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.theme.FinanceTheme

@Composable
fun AccountsScreen(
    dashboardData: UiDashboard,
    onViewTransactions: () -> Unit,
    onLoanTrackingClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FinanceTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Tài khoản",
            style = FinanceTheme.typography.headlineLarge,
            color = FinanceTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 8.dp),
        )

        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 20.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Tài chính hiện tại",
                    style = FinanceTheme.typography.bodyMedium,
                    color = FinanceTheme.colors.textSecondary,
                )
                Text(
                    text = dashboardData.formattedBalance,
                    style = FinanceTheme.typography.balance,
                    color = FinanceTheme.colors.textPrimary,
                )
                Text(
                    text = "Tổng hợp từ dữ liệu giao dịch đang có",
                    style = FinanceTheme.typography.caption,
                    color = FinanceTheme.colors.textMuted,
                )
            }
        }

        Text(
            text = "Nguồn ngân hàng",
            style = FinanceTheme.typography.titleMedium,
            color = FinanceTheme.colors.textPrimary,
        )

        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BankSourceRow("Vietcombank", "VCB")
                BankSourceRow("VietinBank", "VietinBank")
                BankSourceRow("BIDV", "BIDV")
            }
        }

        Text(
            text = "Quản lý",
            style = FinanceTheme.typography.titleMedium,
            color = FinanceTheme.colors.textPrimary,
        )

        FinanceCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onLoanTrackingClick),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 18.dp,
        ) {
            ActionRow(
                icon = Icons.Rounded.Payments,
                title = "Theo dõi tiền lãi",
                subtitle = "Khoản cho vay, lãi đã thu và lãi còn thu",
            )
        }

        FinanceCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onViewTransactions),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 18.dp,
        ) {
            ActionRow(
                icon = Icons.Rounded.ReceiptLong,
                title = "Lịch sử giao dịch",
                subtitle = "Xem toàn bộ khoản thu và chi đã ghi nhận",
            )
        }

        Spacer(modifier = Modifier.height(56.dp))
    }
}

@Composable
private fun BankSourceRow(name: String, code: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(FinanceTheme.colors.primaryContainer, FinanceTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountBalance,
                contentDescription = null,
                tint = FinanceTheme.colors.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = FinanceTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = FinanceTheme.colors.textPrimary,
            )
            Text(
                text = "$code · nguồn giao dịch hỗ trợ",
                style = FinanceTheme.typography.caption,
                color = FinanceTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(FinanceTheme.colors.primaryContainer, FinanceTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = FinanceTheme.colors.primary)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = FinanceTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = FinanceTheme.colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = FinanceTheme.typography.caption,
                color = FinanceTheme.colors.textMuted,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = FinanceTheme.colors.textMuted,
        )
    }
}
