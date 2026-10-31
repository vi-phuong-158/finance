package vn.phuong.financebridge

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import vn.phuong.financebridge.ui.model.UiPeriodMetric
import vn.phuong.financebridge.ui.util.MonthlyBarData
import vn.phuong.financebridge.ui.util.MonthlySummaryUtil

class MonthlySummaryUtilTest {

    @Test
    fun compute5MonthQueryRange_normalMonth() {
        val today = LocalDate.of(2026, 10, 24)
        val (from, to) = MonthlySummaryUtil.compute5MonthQueryRange(today)
        assertEquals(LocalDate.of(2026, 6, 1), from)
        assertEquals(LocalDate.of(2026, 10, 31), to)

        val params = MonthlySummaryUtil.compute5MonthQueryParams(today)
        assertEquals("2026-06-01", params["from"])
        assertEquals("2026-10-31", params["to"])
    }

    @Test
    fun compute5MonthQueryRange_spanningAcrossNewYearAndLeapYear() {
        // February 2024 is a leap year (29 days). 4 months prior is October 2023.
        val today = LocalDate.of(2024, 2, 15)
        val (from, to) = MonthlySummaryUtil.compute5MonthQueryRange(today)
        assertEquals(LocalDate.of(2023, 10, 1), from)
        assertEquals(LocalDate.of(2024, 2, 29), to)

        val params = MonthlySummaryUtil.compute5MonthQueryParams(today)
        assertEquals("2023-10-01", params["from"])
        assertEquals("2024-02-29", params["to"])
    }

    @Test
    fun aggregate5Months_includesEmptyMonthsAndSpansAcrossNewYear() {
        val today = LocalDate.of(2026, 2, 10)
        // 5 months expected: 2025-10, 2025-11, 2025-12, 2026-01, 2026-02
        val daily = listOf(
            UiPeriodMetric(name = "2025-10-05", income = 10_000_000L, expense = 5_000_000L, net = 5_000_000L, count = 2),
            UiPeriodMetric(name = "2025-10-20", income = 0L, expense = 3_000_000L, net = -3_000_000L, count = 1),
            // 2025-11 is empty -> should have expense = 0, income = 0
            UiPeriodMetric(name = "2025-12-15", income = 2_000_000L, expense = 4_000_000L, net = -2_000_000L, count = 1),
            // 2026-01 is empty -> should have expense = 0, income = 0
            UiPeriodMetric(name = "2026-02-01", income = 0L, expense = 6_000_000L, net = -6_000_000L, count = 1),
            // metric outside the 5 months should be ignored
            UiPeriodMetric(name = "2025-09-30", income = 0L, expense = 9_000_000L, net = -9_000_000L, count = 1),
        )

        val bars = MonthlySummaryUtil.aggregate5Months(daily, today)
        assertEquals(5, bars.size)

        // Month 1: 2025-10
        assertEquals(YearMonth.of(2025, 10), bars[0].yearMonth)
        assertEquals("T10", bars[0].monthLabel)
        assertEquals(8_000_000L, bars[0].expense)
        assertEquals(10_000_000L, bars[0].income)
        assertFalse(bars[0].isCurrentMonth)

        // Month 2: 2025-11 (empty month)
        assertEquals(YearMonth.of(2025, 11), bars[1].yearMonth)
        assertEquals("T11", bars[1].monthLabel)
        assertEquals(0L, bars[1].expense)
        assertEquals(0L, bars[1].income)
        assertFalse(bars[1].isCurrentMonth)

        // Month 3: 2025-12
        assertEquals(YearMonth.of(2025, 12), bars[2].yearMonth)
        assertEquals("T12", bars[2].monthLabel)
        assertEquals(4_000_000L, bars[2].expense)
        assertEquals(2_000_000L, bars[2].income)
        assertFalse(bars[2].isCurrentMonth)

        // Month 4: 2026-01 (empty month)
        assertEquals(YearMonth.of(2026, 1), bars[3].yearMonth)
        assertEquals("T1", bars[3].monthLabel)
        assertEquals(0L, bars[3].expense)
        assertEquals(0L, bars[3].income)
        assertFalse(bars[3].isCurrentMonth)

        // Month 5: 2026-02 (current month)
        assertEquals(YearMonth.of(2026, 2), bars[4].yearMonth)
        assertEquals("T2", bars[4].monthLabel)
        assertEquals(6_000_000L, bars[4].expense)
        assertEquals(0L, bars[4].income)
        assertTrue(bars[4].isCurrentMonth)
    }

    @Test
    fun computeExpenseComparisonComment_higherExpense() {
        // Average of prior 4 months: (10M + 12M + 8M + 10M) / 4 = 10M.
        // Current month: 15M -> 5M higher -> +50%
        val bars = listOf(
            MonthlyBarData(YearMonth.of(2026, 6), "T6", 10_000_000L),
            MonthlyBarData(YearMonth.of(2026, 7), "T7", 12_000_000L),
            MonthlyBarData(YearMonth.of(2026, 8), "T8", 8_000_000L),
            MonthlyBarData(YearMonth.of(2026, 9), "T9", 10_000_000L),
            MonthlyBarData(YearMonth.of(2026, 10), "T10", 15_000_000L, isCurrentMonth = true),
        )
        val comment = MonthlySummaryUtil.computeExpenseComparisonComment(bars)
        assertEquals("Chi tháng này cao hơn 50% so với trung bình 4 tháng trước", comment)
    }

    @Test
    fun computeExpenseComparisonComment_lowerExpense() {
        // Average of prior 4 months: 10M.
        // Current month: 7M -> 3M lower -> -30%
        val bars = listOf(
            MonthlyBarData(YearMonth.of(2026, 6), "T6", 10_000_000L),
            MonthlyBarData(YearMonth.of(2026, 7), "T7", 10_000_000L),
            MonthlyBarData(YearMonth.of(2026, 8), "T8", 10_000_000L),
            MonthlyBarData(YearMonth.of(2026, 9), "T9", 10_000_000L),
            MonthlyBarData(YearMonth.of(2026, 10), "T10", 7_000_000L, isCurrentMonth = true),
        )
        val comment = MonthlySummaryUtil.computeExpenseComparisonComment(bars)
        assertEquals("Chi tháng này thấp hơn 30% so với trung bình 4 tháng trước", comment)
    }

    @Test
    fun computeExpenseComparisonComment_zeroAveragePrior() {
        // All prior months have 0 expense -> average = 0 -> division by zero avoided
        val bars = listOf(
            MonthlyBarData(YearMonth.of(2026, 6), "T6", 0L),
            MonthlyBarData(YearMonth.of(2026, 7), "T7", 0L),
            MonthlyBarData(YearMonth.of(2026, 8), "T8", 0L),
            MonthlyBarData(YearMonth.of(2026, 9), "T9", 0L),
            MonthlyBarData(YearMonth.of(2026, 10), "T10", 5_000_000L, isCurrentMonth = true),
        )
        val comment = MonthlySummaryUtil.computeExpenseComparisonComment(bars)
        assertEquals("Chưa đủ dữ liệu so sánh", comment)
    }

    @Test
    fun computeExpenseComparisonComment_insufficientData() {
        // Only 3 months
        val bars = listOf(
            MonthlyBarData(YearMonth.of(2026, 8), "T8", 5_000_000L),
            MonthlyBarData(YearMonth.of(2026, 9), "T9", 5_000_000L),
            MonthlyBarData(YearMonth.of(2026, 10), "T10", 5_000_000L, isCurrentMonth = true),
        )
        val comment = MonthlySummaryUtil.computeExpenseComparisonComment(bars)
        assertEquals("Chưa đủ dữ liệu so sánh", comment)
    }

    @Test
    fun computeExpenseComparisonComment_equivalentExpense() {
        val bars = listOf(
            MonthlyBarData(YearMonth.of(2026, 6), "T6", 10_000_000L),
            MonthlyBarData(YearMonth.of(2026, 7), "T7", 10_000_000L),
            MonthlyBarData(YearMonth.of(2026, 8), "T8", 10_000_000L),
            MonthlyBarData(YearMonth.of(2026, 9), "T9", 10_000_000L),
            MonthlyBarData(YearMonth.of(2026, 10), "T10", 10_000_000L, isCurrentMonth = true),
        )
        val comment = MonthlySummaryUtil.computeExpenseComparisonComment(bars)
        assertEquals("Chi tháng này tương đương trung bình 4 tháng trước", comment)
    }

    @Test
    fun formatOutstandingDebt_cases() {
        assertEquals("—", MonthlySummaryUtil.formatOutstandingDebt(null))
        assertEquals("0\u00A0đ", MonthlySummaryUtil.formatOutstandingDebt(0L))
        assertEquals("150.000.000\u00A0đ", MonthlySummaryUtil.formatOutstandingDebt(150_000_000L))
    }
}
