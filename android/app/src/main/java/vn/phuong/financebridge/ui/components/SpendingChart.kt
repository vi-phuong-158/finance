package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiPeriodMetric
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.FinanceFormatters
import vn.phuong.financebridge.ui.util.ReportChartBar
import vn.phuong.financebridge.ui.util.ReportPeriodHelper

/**
 * Canvas-based SpendingChart (UI V2 Phase D / §9.3):
 * - Fits any screen width (including 344dp Galaxy Z Fold 4) without horizontal scrolling.
 * - Renders up to 31 daily bars cleanly.
 * - Tap a bar to view exact date, amount and transaction count.
 * - Features a subtle average line.
 * - Minimalist axis labels (start, middle, end) to avoid crowding.
 */
@Composable
fun SpendingChart(
    daily: List<UiPeriodMetric>,
    modifier: Modifier = Modifier,
    fromDate: LocalDate? = null,
    toDate: LocalDate? = null,
) {
    val nonZeroExpenses = remember(daily) { daily.filter { it.expense > 0 } }

    FinanceCard(
        modifier = modifier.fillMaxWidth(),
        shape = FinanceTheme.shapes.xLarge,
        contentPadding = 18.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(title = "Chi tiêu theo ngày")

            if (nonZeroExpenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Chưa có chi tiêu trong kỳ này.",
                        color = FinanceTheme.colors.textMuted,
                        style = FinanceTheme.typography.bodyMedium
                    )
                }
            } else {
                // Determine date range for daily bars
                val resolvedFrom = remember(daily, fromDate) {
                    fromDate ?: runCatching { LocalDate.parse(daily.first().name) }.getOrDefault(LocalDate.now())
                }
                val resolvedTo = remember(daily, toDate) {
                    toDate ?: runCatching { LocalDate.parse(daily.last().name) }.getOrDefault(LocalDate.now())
                }

                val bars = remember(daily, resolvedFrom, resolvedTo) {
                    val days = java.time.temporal.ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) + 1
                    if (days in 1..31) {
                        ReportPeriodHelper.aggregateDailyToDaily(daily, resolvedFrom, resolvedTo, isIncome = false)
                    } else {
                        // If more than 31 days, sort and display available non-zero days
                        daily.map {
                            ReportChartBar(
                                key = it.name,
                                label = if (it.name.length >= 10) it.name.substring(8, 10) else it.name,
                                amount = it.expense,
                                count = it.count
                            )
                        }
                    }
                }

                val maxAmount = remember(bars) { (bars.maxOfOrNull { it.amount } ?: 1L).coerceAtLeast(1L) }
                val averageAmount = remember(bars) {
                    if (bars.isNotEmpty()) bars.sumOf { it.amount } / bars.size else 0L
                }

                var selectedBarIndex by remember(bars) { mutableStateOf<Int?>(null) }
                val selectedBar = selectedBarIndex?.let { bars.getOrNull(it) }

                // Info banner when a bar is tapped
                if (selectedBar != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = FinanceTheme.shapes.small,
                        color = FinanceTheme.colors.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ngày ${selectedBar.key}",
                                style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = FinanceTheme.colors.primary
                            )
                            AmountText(
                                amountText = FinanceFormatters.formatVnd(selectedBar.amount, includeSign = false),
                                type = TransactionType.CHI,
                                style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                                autoShrink = true
                            )
                        }
                    }
                } else if (averageAmount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Trung bình: ${FinanceFormatters.formatVnd(averageAmount)}/ngày",
                            style = FinanceTheme.typography.caption,
                            color = FinanceTheme.colors.textMuted
                        )
                    }
                }

                val primaryColor = FinanceTheme.colors.primary
                val zeroBarColor = if (FinanceTheme.colors.isDark) Color(0xFF2C322E) else Color(0xFFE2E8E4)
                val averageLineColor = if (FinanceTheme.colors.isDark) Color(0xFF4A554E) else Color(0xFFB0BEC5)

                // Canvas chart area
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .pointerInput(bars) {
                            detectTapGestures { offset ->
                                if (bars.isNotEmpty()) {
                                    val slotWidth = size.width / bars.size
                                    val tapped = (offset.x / slotWidth).toInt().coerceIn(0, bars.size - 1)
                                    selectedBarIndex = if (selectedBarIndex == tapped) null else tapped
                                }
                            }
                        }
                ) {
                    val availableWidth = size.width
                    val availableHeight = size.height - 18.dp.toPx() // Reserve space for axis labels
                    val slotWidth = availableWidth / bars.size
                    val barWidth = (slotWidth * 0.65f).coerceIn(2.dp.toPx(), 14.dp.toPx())

                    // Draw average dashed line
                    if (averageAmount > 0 && maxAmount > 0) {
                        val avgY = availableHeight - ((averageAmount.toFloat() / maxAmount) * availableHeight).coerceIn(0f, availableHeight)
                        drawLine(
                            color = averageLineColor,
                            start = Offset(0f, avgY),
                            end = Offset(availableWidth, avgY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )
                    }

                    // Draw bars
                    for ((idx, bar) in bars.withIndex()) {
                        val isSelected = idx == selectedBarIndex
                        val barHeight = if (bar.amount > 0) {
                            ((bar.amount.toFloat() / maxAmount) * availableHeight).coerceAtLeast(3.dp.toPx())
                        } else {
                            1.5.dp.toPx()
                        }

                        val x = idx * slotWidth + (slotWidth - barWidth) / 2f
                        val y = availableHeight - barHeight

                        val color = when {
                            isSelected -> primaryColor
                            bar.amount > 0 -> primaryColor.copy(alpha = 0.85f)
                            else -> zeroBarColor
                        }

                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }
                }

                // Minimalist axis labels: start, middle, end
                if (bars.size >= 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = bars.first().label,
                            style = FinanceTheme.typography.caption.copy(fontSize = 10.sp),
                            color = FinanceTheme.colors.textMuted
                        )
                        if (bars.size >= 5) {
                            val midIndex = bars.size / 2
                            Text(
                                text = bars[midIndex].label,
                                style = FinanceTheme.typography.caption.copy(fontSize = 10.sp),
                                color = FinanceTheme.colors.textMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            text = bars.last().label,
                            style = FinanceTheme.typography.caption.copy(fontSize = 10.sp),
                            color = FinanceTheme.colors.textMuted
                        )
                    }
                }
            }
        }
    }
}
