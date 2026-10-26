package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import vn.phuong.financebridge.ui.model.UiPeriodMetric
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.FinanceFormatters

@Composable
fun SpendingChart(daily: List<UiPeriodMetric>, modifier: Modifier = Modifier) {
    val rows = daily.filter { it.expense > 0 }
    FinanceCard(modifier = modifier.fillMaxWidth(), shape = FinanceTheme.shapes.xLarge, contentPadding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(title = "Chi tiêu theo ngày")
            if (rows.isEmpty()) {
                Text("Chưa có chi tiêu trong kỳ này.", color = FinanceTheme.colors.textMuted)
            } else {
                val maximum = rows.maxOf { it.expense }.toDouble()
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    rows.forEach { row ->
                        Column(modifier = Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(FinanceFormatters.formatVnd(row.expense), style = FinanceTheme.typography.caption,
                                color = FinanceTheme.colors.textPrimary, maxLines = 1, softWrap = false)
                            Box(Modifier.height(110.dp), contentAlignment = Alignment.BottomCenter) {
                                Box(Modifier.width(22.dp).height((100 * row.expense / maximum).toFloat().coerceAtLeast(1f).dp)
                                    .clip(FinanceTheme.shapes.pill).background(FinanceTheme.colors.primary))
                            }
                            Text(FinanceFormatters.formatIsoDate(row.name), style = FinanceTheme.typography.caption,
                                color = FinanceTheme.colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
