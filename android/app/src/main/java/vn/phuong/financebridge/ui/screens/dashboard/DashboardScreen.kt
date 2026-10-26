package vn.phuong.financebridge.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.phuong.financebridge.ui.components.*
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Header: Đơn giản, tiêu đề lớn + thời gian + icon đồng bộ
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Tổng quan",
                    style = FinanceTheme.typography.headlineLarge,
                    color = FinanceTheme.colors.textPrimary,
                )
                Text(
                    text = data.periodLabel,
                    style = FinanceTheme.typography.bodyMedium,
                    color = FinanceTheme.colors.textMuted,
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(FinanceTheme.colors.primaryContainer, FinanceTheme.shapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = "Đồng bộ",
                    tint = FinanceTheme.colors.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // 2. Hero Card: Tài chính hiện tại (Gradient HeroStart -> HeroEnd, 34sp Bold)
        BalanceCard(
            balanceText = data.formattedBalance,
            incomeText = data.formattedIncome,
            expenseText = data.formattedExpense,
            periodLabel = data.periodLabel,
        )

        // 3. Tình hình thu chi tháng này: Card trắng chứa 3 metric (Thu, Chi, Chênh lệch)
        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 12.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(title = "Tình hình thu chi")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SummaryStat(
                        label = "Tổng thu",
                        value = data.formattedIncome,
                        valueColor = FinanceTheme.colors.income,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryStat(
                        label = "Tổng chi",
                        value = data.formattedExpense,
                        valueColor = FinanceTheme.colors.expense,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryStat(
                        label = "Chênh lệch",
                        value = data.formattedNetDiff,
                        valueColor = FinanceTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 4. Biểu đồ xu hướng chi tiêu
        SpendingChart()

        // 5. Danh mục chi tiêu
        if (data.categories.isNotEmpty()) {
            FinanceCard(
                modifier = Modifier.fillMaxWidth(),
                shape = FinanceTheme.shapes.xLarge,
                contentPadding = 18.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SectionHeader(title = "Danh mục chi tiêu")
                    data.categories.take(5).forEach { category ->
                        CategoryProgress(category = category)
                    }
                }
            }
        }

        // 6. Giao dịch gần đây (tối đa 5 dòng theo DESIGN.md)
        FinanceCard(
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
                    data.recentTransactions.take(5).forEach { tx ->
                        TransactionRow(
                            transaction = tx,
                            onClick = { onTransactionClick(tx.id) },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(56.dp))
    }
}

@Composable
private fun SummaryStat(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = FinanceTheme.typography.caption.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = FinanceTheme.colors.textMuted,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = FinanceTheme.typography.caption.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            ),
            color = valueColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
