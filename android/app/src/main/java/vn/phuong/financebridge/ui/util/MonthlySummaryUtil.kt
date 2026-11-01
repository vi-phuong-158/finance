package vn.phuong.financebridge.ui.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.round
import vn.phuong.financebridge.ui.model.UiPeriodMetric

data class MonthlyBarData(
    val yearMonth: YearMonth,
    val monthLabel: String,
    val expense: Long,
    val income: Long = 0L,
    val isCurrentMonth: Boolean = false,
    val transactionCount: Int = 0,
)

object MonthlySummaryUtil {
    val VIETNAM_ZONE_ID: ZoneId = vn.phuong.financebridge.ui.util.VIETNAM_ZONE_ID

    /**
     * Computes the 5-month query date range (from 4 months ago to end of current month)
     * using the Asia/Ho_Chi_Minh timezone.
     */
    fun compute5MonthQueryRange(
        today: LocalDate = LocalDate.now(VIETNAM_ZONE_ID),
    ): Pair<LocalDate, LocalDate> {
        val currentYm = YearMonth.from(today)
        val startYm = currentYm.minusMonths(4)
        val fromDate = startYm.atDay(1)
        val toDate = currentYm.atEndOfMonth()
        return fromDate to toDate
    }

    /**
     * Converts the 5-month date range to query parameters for /api/dashboard.
     */
    fun compute5MonthQueryParams(
        today: LocalDate = LocalDate.now(VIETNAM_ZONE_ID),
    ): Map<String, String> {
        val (from, to) = compute5MonthQueryRange(today)
        return mapOf("from" to from.toString(), "to" to to.toString())
    }

    /**
     * Aggregates daily metrics from dailySummary into 5 consecutive monthly bars
     * by delegating to the shared ReportPeriodHelper.aggregateMonthlyMetrics engine.
     * Empty months are filled with 0. Seamlessly spans across year boundaries.
     */
    fun aggregate5Months(
        daily: List<UiPeriodMetric>,
        today: LocalDate = LocalDate.now(VIETNAM_ZONE_ID),
    ): List<MonthlyBarData> {
        val currentYm = YearMonth.from(today)
        val startYm = currentYm.minusMonths(4)
        return ReportPeriodHelper.aggregateMonthlyMetrics(
            daily = daily,
            fromMonth = startYm,
            toMonth = currentYm,
            currentMonthRef = currentYm,
        )
    }

    /**
     * Pure analytical comment comparing this month's expense against the average of the 4 prior months.
     * Edge cases:
     * - fewer than 5 months provided -> "Chưa đủ dữ liệu so sánh"
     * - average expense of 4 prior months == 0 -> "Chưa đủ dữ liệu so sánh"
     * - current month higher -> "Chi tháng này cao hơn X% so với trung bình 4 tháng trước"
     * - current month lower -> "Chi tháng này thấp hơn X% so với trung bình 4 tháng trước"
     * - current month equal -> "Chi tháng này tương đương trung bình 4 tháng trước"
     */
    fun computeExpenseComparisonComment(bars: List<MonthlyBarData>): String {
        if (bars.size < 5) return "Chưa đủ dữ liệu so sánh"

        val priorBars = bars.take(4)
        val currentBar = bars.last()

        val avgPrior = priorBars.map { it.expense }.average()
        if (avgPrior <= 0.0) return "Chưa đủ dữ liệu so sánh"

        val currentExpense = currentBar.expense.toDouble()
        val diff = currentExpense - avgPrior

        return when {
            diff > 0.0 -> {
                val pct = round((diff / avgPrior) * 100.0).toInt()
                if (pct > 0) "Chi tháng này cao hơn $pct% so với trung bình 4 tháng trước"
                else "Chi tháng này tương đương trung bình 4 tháng trước"
            }
            diff < 0.0 -> {
                val pct = round((-diff / avgPrior) * 100.0).toInt()
                if (pct > 0) "Chi tháng này thấp hơn $pct% so với trung bình 4 tháng trước"
                else "Chi tháng này tương đương trung bình 4 tháng trước"
            }
            else -> "Chi tháng này tương đương trung bình 4 tháng trước"
        }
    }

    /**
     * Formats outstanding loan debt.
     * Null -> "—"
     * Number -> formatted VND with non-breaking currency.
     */
    fun formatOutstandingDebt(principal: Long?): String {
        if (principal == null) return "—"
        return FinanceFormatters.formatVnd(principal)
    }
}
