package vn.phuong.financebridge.ui.screens.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import vn.phuong.financebridge.ui.components.*
import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.theme.FinanceTheme

data class ReportGridItem(val title: String, val subtitle: String, val icon: ImageVector, val tint: Color)

@Composable
fun ReportsScreen(
    dashboardData: UiDashboard = UiDashboard.preview(),
    modifier: Modifier = Modifier,
) {
    var selectedReportPeriod by remember { mutableStateOf("Tháng này") }
    val reportPeriods = listOf("Tháng này", "Quý 4/2026", "Năm 2026")
    var selectedReportFeature by remember { mutableStateOf<String?>(null) }

    val reportGridItems = listOf(
        ReportGridItem("Phân tích chi tiêu", "Chi tiết theo danh mục", Icons.Rounded.PieChart, Color(0xFFEF5350)),
        ReportGridItem("Phân tích thu", "Nguồn thu nhập", Icons.AutoMirrored.Rounded.TrendingUp, Color(0xFF28A765)),
        ReportGridItem("Theo dõi vay nợ", "Khoản vay và cho vay", Icons.Rounded.AccountBalance, Color(0xFFFFA726)),
        ReportGridItem("Đối tượng thu/chi", "Người nhận và người trả", Icons.Rounded.People, Color(0xFF42A5F5)),
        ReportGridItem("Chuyến đi / Sự kiện", "Thu chi theo sự kiện", Icons.Rounded.Flight, Color(0xFFAB47BC)),
        ReportGridItem("Phân tích tài chính", "Xu hướng dài hạn", Icons.Rounded.Insights, Color(0xFF08A9D6)),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FinanceTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
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

        // Summary Card: Thu, Chi, Chênh lệch
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
                    ReportStatItem(
                        title = "Tổng thu",
                        value = dashboardData.formattedIncome,
                        color = FinanceTheme.colors.income,
                        modifier = Modifier.weight(1f),
                    )
                    ReportStatItem(
                        title = "Tổng chi",
                        value = dashboardData.formattedExpense,
                        color = FinanceTheme.colors.expense,
                        modifier = Modifier.weight(1f),
                    )
                    ReportStatItem(
                        title = "Chênh lệch",
                        value = dashboardData.formattedNetDiff,
                        color = FinanceTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Spending Trend Chart
        SpendingChart()

        // Expense Category Breakdown
        if (dashboardData.categories.isNotEmpty()) {
            FinanceCard(
                modifier = Modifier.fillMaxWidth(),
                shape = FinanceTheme.shapes.xLarge,
                contentPadding = 18.dp,
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
        }

        // 2-column Reports Grid (DESIGN.md)
        Text(
            text = "Báo cáo chuyên sâu",
            style = FinanceTheme.typography.titleMedium,
            color = FinanceTheme.colors.textPrimary,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            reportGridItems.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair.forEach { item ->
                        FinanceCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedReportFeature = item.title },
                            shape = FinanceTheme.shapes.large,
                            contentPadding = 14.dp,
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(item.tint.copy(alpha = 0.12f), FinanceTheme.shapes.medium),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = item.tint,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Text(
                                    text = item.title,
                                    style = FinanceTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                    ),
                                    color = FinanceTheme.colors.textPrimary,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.subtitle,
                                    style = FinanceTheme.typography.caption.copy(fontSize = 11.sp),
                                    color = FinanceTheme.colors.textMuted,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(56.dp))
    }

    if (selectedReportFeature != null) {
        Dialog(onDismissRequest = { selectedReportFeature = null }) {
            Surface(
                shape = FinanceTheme.shapes.xLarge,
                color = FinanceTheme.colors.surfaceCard,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = selectedReportFeature ?: "Báo cáo",
                        style = FinanceTheme.typography.titleLarge,
                        color = FinanceTheme.colors.textPrimary,
                    )
                    Text(
                        text = "Tính năng đang được phát triển",
                        style = FinanceTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = FinanceTheme.colors.primary,
                    )
                    Text(
                        text = "Báo cáo phân tích chuyên sâu sẽ được mở rộng dựa trên dữ liệu giao dịch thực tế của bạn trong các phiên bản cập nhật tới.",
                        style = FinanceTheme.typography.bodyMedium,
                        color = FinanceTheme.colors.textSecondary,
                    )
                    SecondaryButton(
                        text = "Đóng",
                        onClick = { selectedReportFeature = null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportStatItem(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
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
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
