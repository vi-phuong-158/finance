package vn.phuong.financebridge

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import vn.phuong.financebridge.ui.model.UiPeriodMetric
import vn.phuong.financebridge.ui.util.HistoryPeriodPreset
import vn.phuong.financebridge.ui.util.ReportPeriodHelper

class ReportPeriodHelperTest {

    @Test
    fun testIsValidCategoryForHistory() {
        assertFalse(ReportPeriodHelper.isValidCategoryForHistory(null))
        assertFalse(ReportPeriodHelper.isValidCategoryForHistory(""))
        assertFalse(ReportPeriodHelper.isValidCategoryForHistory("   "))
        assertFalse(ReportPeriodHelper.isValidCategoryForHistory("Chưa phân loại"))
        assertFalse(ReportPeriodHelper.isValidCategoryForHistory("chưa phân loại"))
        assertFalse(ReportPeriodHelper.isValidCategoryForHistory("  Chưa phân loại  "))

        assertTrue(ReportPeriodHelper.isValidCategoryForHistory("Ăn uống"))
        assertTrue(ReportPeriodHelper.isValidCategoryForHistory("Di chuyển"))
        assertTrue(ReportPeriodHelper.isValidCategoryForHistory("Lương"))
    }

    @Test
    fun testCalculatePresetDates() {
        val anchor = LocalDate.of(2026, 10, 15)

        // 3 months: 2026-08-01 to 2026-10-31
        val (p3From, p3To) = ReportPeriodHelper.calculatePresetDates(HistoryPeriodPreset.THREE_MONTHS, anchor)
        assertEquals(LocalDate.of(2026, 8, 1), p3From)
        assertEquals(LocalDate.of(2026, 10, 31), p3To)

        // 6 months: 2026-05-01 to 2026-10-31
        val (p6From, p6To) = ReportPeriodHelper.calculatePresetDates(HistoryPeriodPreset.SIX_MONTHS, anchor)
        assertEquals(LocalDate.of(2026, 5, 1), p6From)
        assertEquals(LocalDate.of(2026, 10, 31), p6To)

        // 12 months: 2025-11-01 to 2026-10-31 (crossing year boundary)
        val (p12From, p12To) = ReportPeriodHelper.calculatePresetDates(HistoryPeriodPreset.TWELVE_MONTHS, anchor)
        assertEquals(LocalDate.of(2025, 11, 1), p12From)
        assertEquals(LocalDate.of(2026, 10, 31), p12To)

        // Across year: anchor in Jan 2026
        val anchorJan = LocalDate.of(2026, 1, 10)
        val (p3JanFrom, p3JanTo) = ReportPeriodHelper.calculatePresetDates(HistoryPeriodPreset.THREE_MONTHS, anchorJan)
        assertEquals(LocalDate.of(2025, 11, 1), p3JanFrom)
        assertEquals(LocalDate.of(2026, 1, 31), p3JanTo)
    }

    @Test
    fun testCalculatePreviousPeriodFullCalendarMonths() {
        // March 2024 -> previous is February 2024 (Leap year: 29 days)
        val fromMar24 = LocalDate.of(2024, 3, 1)
        val toMar24 = LocalDate.of(2024, 3, 31)
        val (prevFrom24, prevTo24) = ReportPeriodHelper.calculatePreviousPeriod(fromMar24, toMar24)
        assertEquals(LocalDate.of(2024, 2, 1), prevFrom24)
        assertEquals(LocalDate.of(2024, 2, 29), prevTo24)

        // March 2025 -> previous is February 2025 (Non-leap year: 28 days)
        val fromMar25 = LocalDate.of(2025, 3, 1)
        val toMar25 = LocalDate.of(2025, 3, 31)
        val (prevFrom25, prevTo25) = ReportPeriodHelper.calculatePreviousPeriod(fromMar25, toMar25)
        assertEquals(LocalDate.of(2025, 2, 1), prevFrom25)
        assertEquals(LocalDate.of(2025, 2, 28), prevTo25)

        // 3 calendar months: Aug 2026 - Oct 2026 -> previous is May 2026 - Jul 2026
        val from3m = LocalDate.of(2026, 8, 1)
        val to3m = LocalDate.of(2026, 10, 31)
        val (prevFrom3m, prevTo3m) = ReportPeriodHelper.calculatePreviousPeriod(from3m, to3m)
        assertEquals(LocalDate.of(2026, 5, 1), prevFrom3m)
        assertEquals(LocalDate.of(2026, 7, 31), prevTo3m)

        // Year wrap: Jan 2026 to Mar 2026 -> previous is Oct 2025 to Dec 2025
        val fromWrap = LocalDate.of(2026, 1, 1)
        val toWrap = LocalDate.of(2026, 3, 31)
        val (prevFromWrap, prevToWrap) = ReportPeriodHelper.calculatePreviousPeriod(fromWrap, toWrap)
        assertEquals(LocalDate.of(2025, 10, 1), prevFromWrap)
        assertEquals(LocalDate.of(2025, 12, 31), prevToWrap)
    }

    @Test
    fun testCalculatePreviousPeriodArbitraryDays() {
        // 10 days: 2026-10-11 to 2026-10-20 -> previous 10 days: 2026-10-01 to 2026-10-10
        val from = LocalDate.of(2026, 10, 11)
        val to = LocalDate.of(2026, 10, 20)
        val (prevFrom, prevTo) = ReportPeriodHelper.calculatePreviousPeriod(from, to)
        assertEquals(LocalDate.of(2026, 10, 1), prevFrom)
        assertEquals(LocalDate.of(2026, 10, 10), prevTo)

        // Across leap year: 2024-03-01 to 2024-03-05 (5 days) -> previous: 2024-02-25 to 2024-02-29 (5 days)
        val fromLeap = LocalDate.of(2024, 3, 1)
        val toLeap = LocalDate.of(2024, 3, 5)
        val (prevFromLeap, prevToLeap) = ReportPeriodHelper.calculatePreviousPeriod(fromLeap, toLeap)
        assertEquals(LocalDate.of(2024, 2, 25), prevFromLeap)
        assertEquals(LocalDate.of(2024, 2, 29), prevToLeap)
    }

    @Test
    fun testAggregateDailyToMonthlyProducesExact12BarsWithMissingFilled() {
        val from = LocalDate.of(2025, 11, 1)
        val to = LocalDate.of(2026, 10, 31)

        // Only have data for 2 months out of 12
        val daily = listOf(
            UiPeriodMetric(name = "2025-11-05", income = 0L, expense = 100_000L, net = -100_000L, count = 1),
            UiPeriodMetric(name = "2025-11-20", income = 0L, expense = 200_000L, net = -200_000L, count = 1),
            UiPeriodMetric(name = "2026-10-01", income = 0L, expense = 500_000L, net = -500_000L, count = 2)
        )

        val bars = ReportPeriodHelper.aggregateDailyToMonthly(daily, from, to, isIncome = false)

        assertEquals("Must produce exactly 12 bars for 12 months", 12, bars.size)
        // First month: 2025-11
        assertEquals("2025-11", bars[0].key)
        assertEquals(300_000L, bars[0].amount)
        assertEquals(2, bars[0].count)

        // Middle missing month: 2025-12 should be 0
        assertEquals("2025-12", bars[1].key)
        assertEquals(0L, bars[1].amount)
        assertEquals(0, bars[1].count)

        // Last month: 2026-10
        assertEquals("2026-10", bars[11].key)
        assertEquals(500_000L, bars[11].amount)
        assertEquals(2, bars[11].count)
    }

    @Test
    fun testAggregateDailyToDailyFillsMissingDays() {
        val from = LocalDate.of(2026, 10, 1)
        val to = LocalDate.of(2026, 10, 5) // 5 days

        val daily = listOf(
            UiPeriodMetric(name = "2026-10-02", income = 0L, expense = 50_000L, net = -50_000L, count = 1),
            UiPeriodMetric(name = "2026-10-05", income = 0L, expense = 10_000_000L, net = -10_000_000L, count = 1) // 1 day extremely large
        )

        val bars = ReportPeriodHelper.aggregateDailyToDaily(daily, from, to, isIncome = false)

        assertEquals(5, bars.size)
        // Day 1 has 0
        assertEquals("2026-10-01", bars[0].key)
        assertEquals(0L, bars[0].amount)
        // Day 2 has 50k
        assertEquals("2026-10-02", bars[1].key)
        assertEquals(50_000L, bars[1].amount)
        // Day 5 has 10M
        assertEquals("2026-10-05", bars[4].key)
        assertEquals(10_000_000L, bars[4].amount)
    }

    @Test
    fun testCalculateComparison() {
        // Previous == 0
        val compZero = ReportPeriodHelper.calculateComparison(100_000L, 0L)
        assertEquals("Chưa có dữ liệu so sánh", compZero.formattedSummary)
        assertNull(compZero.percentChange)

        // Current > Previous (Higher)
        val compHigher = ReportPeriodHelper.calculateComparison(120_000L, 100_000L)
        assertEquals("▲ 20% so với giai đoạn trước", compHigher.formattedSummary)
        assertEquals(20, compHigher.percentChange)
        assertTrue(compHigher.isHigher)

        // Current < Previous (Lower)
        val compLower = ReportPeriodHelper.calculateComparison(80_000L, 100_000L)
        assertEquals("▼ 20% so với giai đoạn trước", compLower.formattedSummary)
        assertEquals(-20, compLower.percentChange)
        assertFalse(compLower.isHigher)

        // Equal
        val compEqual = ReportPeriodHelper.calculateComparison(100_000L, 100_000L)
        assertEquals("Không đổi so với giai đoạn trước", compEqual.formattedSummary)
        assertEquals(0, compEqual.percentChange)
    }

    @Test
    fun testMatchesBarFilterUsesVietnamTimeZone() {
        // 2026-10-01 00:30:00 in Asia/Ho_Chi_Minh (+07:00) is 2026-09-30 17:30:00 UTC
        val timestampVnEarlyMorning = java.time.ZonedDateTime.of(
            2026, 10, 1, 0, 30, 0, 0,
            java.time.ZoneId.of("Asia/Ho_Chi_Minh")
        ).toInstant().toEpochMilli()

        assertTrue(ReportPeriodHelper.matchesBarFilter(timestampVnEarlyMorning, "2026-10"))
        assertTrue(ReportPeriodHelper.matchesBarFilter(timestampVnEarlyMorning, "2026-10-01"))
        assertFalse(ReportPeriodHelper.matchesBarFilter(timestampVnEarlyMorning, "2026-09"))
        assertFalse(ReportPeriodHelper.matchesBarFilter(timestampVnEarlyMorning, "2026-09-30"))
    }
}
