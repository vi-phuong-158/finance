package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.phuong.financebridge.ui.model.UiPeriodMetric
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.FinanceFormatters
import vn.phuong.financebridge.ui.util.MonthlySummaryUtil

@Composable
fun FiveMonthSpendingChart(
    daily: List<UiPeriodMetric>,
    onOpenReports: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val monthlyBars = remember(daily) {
        MonthlySummaryUtil.aggregate5Months(daily)
    }
    val comparisonComment = remember(monthlyBars) {
        MonthlySummaryUtil.computeExpenseComparisonComment(monthlyBars)
    }

    val maxExpense = remember(monthlyBars) {
        monthlyBars.maxOfOrNull { it.expense }?.coerceAtLeast(1L) ?: 1L
    }

    FinanceCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenReports),
        shape = FinanceTheme.shapes.xLarge,
        contentPadding = 16.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header: Tiêu đề + Liên kết mở Báo cáo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tình hình chi tiêu 5 tháng",
                    style = FinanceTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = FinanceTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Báo cáo",
                        style = FinanceTheme.typography.caption.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = FinanceTheme.colors.primary,
                        ),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = "Xem báo cáo",
                        tint = FinanceTheme.colors.primary,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }

            // Câu nhận xét tính thuần (so sánh tháng này với trung bình 4 tháng trước)
            Text(
                text = comparisonComment,
                style = FinanceTheme.typography.caption.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = FinanceTheme.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 5 cột chi theo tháng (vừa khít 344dp, nhãn không rớt dòng)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                monthlyBars.forEach { bar ->
                    val isCurrent = bar.isCurrentMonth
                    val barColor = if (isCurrent) {
                        FinanceTheme.colors.primary
                    } else {
                        FinanceTheme.colors.primary.copy(alpha = 0.35f)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        // Số tiền trên đầu cột
                        Text(
                            text = if (bar.expense > 0) FinanceFormatters.formatVnd(bar.expense) else "0\u00A0đ",
                            style = FinanceTheme.typography.caption.copy(
                                fontSize = 9.sp,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = if (isCurrent) FinanceTheme.colors.textPrimary else FinanceTheme.colors.textMuted,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Cột Canvas có bo góc 6dp
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .width(22.dp)
                                    .fillMaxHeight(),
                            ) {
                                val canvasHeight = size.height
                                val canvasWidth = size.width
                                val ratio = (bar.expense.toDouble() / maxExpense.toDouble()).toFloat().coerceIn(0f, 1f)
                                // Height at least 4dp baseline so 0 months still have a clean base
                                val barHeight = (canvasHeight * ratio).coerceAtLeast(4.dp.toPx())
                                val top = canvasHeight - barHeight

                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(0f, top),
                                    size = Size(canvasWidth, barHeight),
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Nhãn tháng (T6, T7, T8, T9, T10 - ngắn gọn, không bao giờ xuống dòng)
                        Text(
                            text = bar.monthLabel,
                            style = FinanceTheme.typography.caption.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (isCurrent) FinanceTheme.colors.primary else FinanceTheme.colors.textSecondary,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}
