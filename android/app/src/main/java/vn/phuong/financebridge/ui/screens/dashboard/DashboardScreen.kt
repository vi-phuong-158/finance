package vn.phuong.financebridge.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.phuong.financebridge.ui.components.*
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.theme.FinanceTheme

@Composable
fun DashboardScreen(
    dashboardState: UiDashboard?,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onViewAllTransactions: () -> Unit,
    onTransactionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (isLoading && dashboardState == null) {
        LoadingState(modifier = modifier)
        return
    }

    if (errorMessage != null && dashboardState == null) {
        ErrorState(
            message = errorMessage,
            onRetry = onRefresh,
            modifier = modifier,
        )
        return
    }

    val data = dashboardState ?: UiDashboard.preview()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FinanceTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // 1. Top Header: Title & Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Tổng quan tài chính",
                    style = FinanceTheme.typography.headlineLarge,
                    color = FinanceTheme.colors.textPrimary,
                )
                Text(
                    text = data.periodLabel,
                    style = FinanceTheme.typography.bodyMedium,
                    color = FinanceTheme.colors.textMuted,
                )
            }

            // Sync / status indicator button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(FinanceTheme.colors.surfaceGlass, FinanceTheme.shapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "⚡",
                    style = FinanceTheme.typography.titleMedium,
                )
            }
        }

        // 2. Main Balance Card (Visual Hero)
        BalanceCard(
            balanceText = data.formattedBalance,
            incomeText = data.formattedIncome,
            expenseText = data.formattedExpense,
            periodLabel = data.periodLabel,
        )

        // 3. Mini Metric Summary Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                title = "Thu nhập",
                valueText = data.formattedIncome,
                type = TransactionType.THU,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Chi tiêu",
                valueText = data.formattedExpense,
                type = TransactionType.CHI,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Tích lũy",
                valueText = data.formattedNetDiff,
                type = if (data.netDiff >= 0) TransactionType.THU else TransactionType.CHI,
                modifier = Modifier.weight(1f),
            )
        }

        // 4. Spending Chart
        SpendingChart()

        // 5. Expense Categories Breakdown
        if (data.categories.isNotEmpty()) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = FinanceTheme.shapes.xLarge,
                contentPadding = 18.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SectionHeader(
                        title = "Danh mục chi tiêu",
                    )
                    data.categories.take(5).forEach { category ->
                        CategoryProgress(category = category)
                    }
                }
            }
        }

        // 6. Recent Transactions
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 18.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SectionHeader(
                    title = "Giao dịch gần đây",
                    actionLabel = "Xem tất cả",
                    onActionClick = onViewAllTransactions,
                )

                if (data.recentTransactions.isEmpty()) {
                    EmptyState(
                        title = "Chưa có giao dịch",
                        subtitle = "Các giao dịch từ ngân hàng hoặc lịch sử sẽ hiển thị ở đây.",
                    )
                } else {
                    data.recentTransactions.take(6).forEach { tx ->
                        TransactionRow(
                            transaction = tx,
                            onClick = { onTransactionClick(tx.id) },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp)) // Extra space for floating bottom bar
    }
}
