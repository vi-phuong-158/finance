package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.theme.Primary300
import vn.phuong.financebridge.ui.theme.Primary500

enum class ChartPeriod(val label: String) {
    DAYS_7("7 ngày"),
    DAYS_30("30 ngày"),
    MONTH("Tháng")
}

data class ChartBarData(
    val label: String,
    val amount: Long,
    val ratio: Float, // 0.0 to 1.0 relative to max
    val isHighlight: Boolean = false,
)

@Composable
fun SpendingChart(
    modifier: Modifier = Modifier,
) {
    var selectedPeriod by remember { mutableStateOf(ChartPeriod.DAYS_7) }

    // Sample spending distribution data based on selected period
    val bars = when (selectedPeriod) {
        ChartPeriod.DAYS_7 -> listOf(
            ChartBarData("T2", 150000L, 0.25f),
            ChartBarData("T3", 420000L, 0.55f),
            ChartBarData("T4", 280000L, 0.40f),
            ChartBarData("T5", 790000L, 0.85f),
            ChartBarData("T6", 950000L, 1.00f, isHighlight = true),
            ChartBarData("T7", 610000L, 0.68f),
            ChartBarData("CN", 340000L, 0.45f),
        )
        ChartPeriod.DAYS_30 -> listOf(
            ChartBarData("T1", 3200000L, 0.60f),
            ChartBarData("T2", 5400000L, 0.90f),
            ChartBarData("T3", 4100000L, 0.75f),
            ChartBarData("T4", 6200000L, 1.00f, isHighlight = true),
        )
        ChartPeriod.MONTH -> listOf(
            ChartBarData("T7", 12500000L, 0.70f),
            ChartBarData("T8", 14200000L, 0.82f),
            ChartBarData("T9", 11800000L, 0.65f),
            ChartBarData("T10", 16500000L, 1.00f, isHighlight = true),
        )
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = FinanceTheme.shapes.xLarge,
        contentPadding = 18.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header with Period Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Xu hướng chi tiêu",
                    style = FinanceTheme.typography.titleMedium,
                    color = FinanceTheme.colors.textPrimary,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChartPeriod.values().forEach { period ->
                        FinanceChip(
                            text = period.label,
                            isSelected = selectedPeriod == period,
                            onClick = { selectedPeriod = period },
                        )
                    }
                }
            }

            // Chart Canvas / Bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEach { bar ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        // Bar height representation
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height((100 * bar.ratio).coerceAtLeast(8f).dp)
                                .clip(FinanceTheme.shapes.pill)
                                .background(if (bar.isHighlight) Primary500 else Primary300.copy(alpha = 0.5f)),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Bar X-axis label
                        Text(
                            text = bar.label,
                            style = FinanceTheme.typography.caption.copy(fontSize = 11.sp),
                            color = if (bar.isHighlight) FinanceTheme.colors.textPrimary else FinanceTheme.colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}
