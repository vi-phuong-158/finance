package vn.phuong.financebridge.ui.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import vn.phuong.financebridge.ui.model.UiPeriodMetric

val VIETNAM_ZONE_ID: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

enum class HistoryPeriodPreset(val label: String) {
    CURRENT("Kỳ đang xem"),
    THREE_MONTHS("3 tháng"),
    SIX_MONTHS("6 tháng"),
    TWELVE_MONTHS("12 tháng"),
    CUSTOM("Tùy chọn");
}

data class ReportChartBar(
    val key: String, // "YYYY-MM-DD" for daily, "YYYY-MM" for monthly
    val label: String, // e.g. "12" or "12/10" for daily; "T5" or "T5/25" for monthly
    val amount: Long, // expense or income depending on view mode
    val count: Int,
)

data class PeriodComparison(
    val diffAmount: Long,
    val percentChange: Int?, // rounded to nearest int, null if previous == 0
    val isHigher: Boolean,
    val formattedSummary: String, // e.g. "▲ 15% so với giai đoạn trước" or "Chưa có dữ liệu so sánh"
)

object ReportPeriodHelper {

    /**
     * Category history query validation:
     * Blank/null category or "Chưa phân loại" cannot be queried for category history.
     */
    fun isValidCategoryForHistory(category: String?): Boolean {
        if (category.isNullOrBlank()) return false
        val trimmed = category.trim()
        if (trimmed.equals("Chưa phân loại", ignoreCase = true)) return false
        return true
    }

    /**
     * Calculates the [from, to] date range for a given period preset,
     * anchored at [anchorDate] (defaults to current date in Asia/Ho_Chi_Minh).
     */
    fun calculatePresetDates(
        preset: HistoryPeriodPreset,
        anchorDate: LocalDate = LocalDate.now(VIETNAM_ZONE_ID),
        currentPeriodFrom: LocalDate? = null,
        currentPeriodTo: LocalDate? = null,
    ): Pair<LocalDate, LocalDate> {
        val anchorMonth = YearMonth.from(anchorDate)
        return when (preset) {
            HistoryPeriodPreset.CURRENT -> {
                val from = currentPeriodFrom ?: anchorMonth.atDay(1)
                val to = currentPeriodTo ?: anchorMonth.atEndOfMonth()
                from to to
            }
            HistoryPeriodPreset.THREE_MONTHS -> {
                val from = anchorMonth.minusMonths(2).atDay(1)
                val to = anchorMonth.atEndOfMonth()
                from to to
            }
            HistoryPeriodPreset.SIX_MONTHS -> {
                val from = anchorMonth.minusMonths(5).atDay(1)
                val to = anchorMonth.atEndOfMonth()
                from to to
            }
            HistoryPeriodPreset.TWELVE_MONTHS -> {
                val from = anchorMonth.minusMonths(11).atDay(1)
                val to = anchorMonth.atEndOfMonth()
                from to to
            }
            HistoryPeriodPreset.CUSTOM -> {
                val from = currentPeriodFrom ?: anchorMonth.atDay(1)
                val to = currentPeriodTo ?: anchorMonth.atEndOfMonth()
                from to to
            }
        }
    }

    /**
     * Calculates the preceding period of the exact same length:
     * - If from to to spans full calendar months, returns the preceding full calendar months.
     * - Otherwise returns the preceding range of the exact same number of days.
     * Accurately handles leap years (e.g. Feb 29) and year boundaries.
     */
    fun calculatePreviousPeriod(from: LocalDate, to: LocalDate): Pair<LocalDate, LocalDate> {
        require(!to.isBefore(from)) { "to date cannot be before from date" }

        val isFullCalendarMonths = from.dayOfMonth == 1 && to.dayOfMonth == to.lengthOfMonth()
        if (isFullCalendarMonths) {
            val fromMonth = YearMonth.from(from)
            val toMonth = YearMonth.from(to)
            val monthsCount = ChronoUnit.MONTHS.between(fromMonth, toMonth) + 1
            val prevToMonth = fromMonth.minusMonths(1)
            val prevFromMonth = prevToMonth.minusMonths(monthsCount - 1)
            return prevFromMonth.atDay(1) to prevToMonth.atEndOfMonth()
        } else {
            val daysCount = ChronoUnit.DAYS.between(from, to) + 1
            val prevTo = from.minusDays(1)
            val prevFrom = prevTo.minusDays(daysCount - 1)
            return prevFrom to prevTo
        }
    }

    /**
     * Shared monthly aggregation engine used by both Home (MonthlySummaryUtil.aggregate5Months)
     * and Reports/CategoryHistory (aggregateDailyToMonthly).
     * Groups dailySummary by "YYYY-MM" and fills missing calendar months in fromMonth..toMonth with 0.
     */
    fun aggregateMonthlyMetrics(
        daily: List<UiPeriodMetric>,
        fromMonth: YearMonth,
        toMonth: YearMonth,
        currentMonthRef: YearMonth = YearMonth.now(VIETNAM_ZONE_ID),
    ): List<MonthlyBarData> {
        if (toMonth.isBefore(fromMonth)) return emptyList()

        val dailyByMonth = daily.groupBy { metric ->
            if (metric.name.length >= 7) metric.name.substring(0, 7) else ""
        }

        val result = mutableListOf<MonthlyBarData>()
        var ym = fromMonth
        while (!ym.isAfter(toMonth)) {
            val key = ym.toString() // "YYYY-MM"
            val matching = dailyByMonth[key] ?: emptyList()
            result.add(
                MonthlyBarData(
                    yearMonth = ym,
                    monthLabel = "T${ym.monthValue}",
                    expense = matching.sumOf { it.expense },
                    income = matching.sumOf { it.income },
                    isCurrentMonth = (ym == currentMonthRef),
                    transactionCount = matching.sumOf { it.count },
                )
            )
            ym = ym.plusMonths(1)
        }
        return result
    }

    /**
     * Aggregates dailySummary into calendar months for a date range:
     * - Fills missing months with 0 amount and 0 count.
     * - Supports crossing year boundaries (e.g. Nov 2025 -> Apr 2026).
     * - A 12-month range will produce exactly 12 bars.
     */
    fun aggregateDailyToMonthly(
        daily: List<UiPeriodMetric>,
        from: LocalDate,
        to: LocalDate,
        isIncome: Boolean = false,
    ): List<ReportChartBar> {
        if (to.isBefore(from)) return emptyList()

        val fromMonth = YearMonth.from(from)
        val toMonth = YearMonth.from(to)
        val spansMultipleYears = fromMonth.year != toMonth.year

        return aggregateMonthlyMetrics(daily, fromMonth, toMonth).map { bar ->
            val ym = bar.yearMonth
            val label = if (spansMultipleYears) {
                "T${ym.monthValue}/${ym.year % 100}"
            } else {
                bar.monthLabel
            }
            ReportChartBar(
                key = ym.toString(),
                label = label,
                amount = if (isIncome) bar.income else bar.expense,
                count = bar.transactionCount,
            )
        }
    }

    /**
     * Aggregates dailySummary into daily bars for ranges <= 31 days:
     * - Fills missing days with 0.
     */
    fun aggregateDailyToDaily(
        daily: List<UiPeriodMetric>,
        from: LocalDate,
        to: LocalDate,
        isIncome: Boolean = false,
    ): List<ReportChartBar> {
        if (to.isBefore(from)) return emptyList()

        val dailyByDate = daily.associateBy { it.name }
        val spansMultipleMonths = from.monthValue != to.monthValue

        val bars = mutableListOf<ReportChartBar>()
        var currentDate = from

        while (!currentDate.isAfter(to)) {
            val key = currentDate.toString() // "YYYY-MM-DD"
            val label = if (spansMultipleMonths) {
                "${currentDate.dayOfMonth}/${currentDate.monthValue}"
            } else {
                "${currentDate.dayOfMonth}"
            }

            val item = dailyByDate[key]
            val amount = if (item != null) (if (isIncome) item.income else item.expense) else 0L
            val count = item?.count ?: 0

            bars.add(
                ReportChartBar(
                    key = key,
                    label = label,
                    amount = amount,
                    count = count,
                )
            )

            currentDate = currentDate.plusDays(1)
        }

        return bars
    }

    /**
     * Calculates the comparison percentage and summary string compared to previous period:
     * - If previous == 0: returns "Chưa có dữ liệu so sánh".
     * - Otherwise returns formatted "▲ X% so với giai đoạn trước" or "▼ X% so với giai đoạn trước".
     */
    fun calculateComparison(currentAmount: Long, previousAmount: Long): PeriodComparison {
        if (previousAmount <= 0L) {
            return PeriodComparison(
                diffAmount = currentAmount,
                percentChange = null,
                isHigher = currentAmount > 0,
                formattedSummary = "Chưa có dữ liệu so sánh",
            )
        }

        val diff = currentAmount - previousAmount
        val pctDouble = (diff.toDouble() / previousAmount) * 100.0
        val pctInt = kotlin.math.round(pctDouble).toInt()
        val absPct = kotlin.math.abs(pctInt)

        val summary = when {
            diff > 0 -> "▲ $absPct% so với giai đoạn trước"
            diff < 0 -> "▼ $absPct% so với giai đoạn trước"
            else -> "Không đổi so với giai đoạn trước"
        }

        return PeriodComparison(
            diffAmount = diff,
            percentChange = pctInt,
            isHigher = diff > 0,
            formattedSummary = summary,
        )
    }

    /**
     * Checks whether a transaction timestamp (epoch millis) falls into [selectedBarKey]
     * ("YYYY-MM" for monthly bars or "YYYY-MM-DD" for daily bars) in the Vietnam timezone.
     */
    fun matchesBarFilter(timestampMillis: Long, selectedBarKey: String): Boolean {
        val date = java.time.Instant.ofEpochMilli(timestampMillis).atZone(VIETNAM_ZONE_ID).toLocalDate()
        return if (selectedBarKey.length == 7) {
            YearMonth.from(date).toString() == selectedBarKey
        } else {
            date.toString() == selectedBarKey
        }
    }
}
