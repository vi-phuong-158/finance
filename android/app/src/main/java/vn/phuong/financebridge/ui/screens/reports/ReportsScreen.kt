package vn.phuong.financebridge.ui.screens.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.phuong.financebridge.ui.components.*
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.theme.FinanceTheme

@Composable
fun ReportsScreen(
    dashboardData: UiDashboard = UiDashboard.preview(),
    modifier: Modifier = Modifier,
) {
    var selectedReportPeriod by remember { mutableStateOf("Tháng này") }
    val reportPeriods = listOf("Tháng này", "Quý 4/2026", "Năm 2026")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FinanceTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Báo cáo tài chính",
            style = FinanceTheme.typography.headlineLarge,
            color = FinanceTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 8.dp),
        )

        // Period filter chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            reportPeriods.forEach { period ->
                FinanceChip(
                    text = period,
                    isSelected = selectedReportPeriod == period,
                    onClick = { selectedReportPeriod = period },
                )
            }
        }

        // Summary Metric Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                title = "Tổng thu",
                valueText = dashboardData.formattedIncome,
                type = TransactionType.THU,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Tổng chi",
                valueText = dashboardData.formattedExpense,
                type = TransactionType.CHI,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Tích lũy ròng",
                valueText = dashboardData.formattedNetDiff,
                type = if (dashboardData.netDiff >= 0) TransactionType.THU else TransactionType.CHI,
                modifier = Modifier.weight(1f),
            )
        }

        // Spending Trend
        SpendingChart()

        // Category Breakdown
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 20.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionHeader(title = "Cơ cấu chi tiêu")
                dashboardData.categories.forEach { cat ->
                    CategoryProgress(category = cat)
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}
