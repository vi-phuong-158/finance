package vn.phuong.financebridge

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import vn.phuong.financebridge.ui.util.FinancePeriod

class FinancePeriodTest {
    @Test fun periodsIncludeBothBoundariesAcrossLeapYearAndNewYear() {
        val today = LocalDate.of(2024, 3, 1)
        assertEquals(mapOf("from" to "2024-02-01", "to" to "2024-02-29"), FinancePeriod.PREVIOUS_MONTH.query(today = today))
        assertEquals(mapOf("from" to "2023-12-27", "to" to "2024-01-02"), FinancePeriod.WEEK.query(today = LocalDate.of(2024, 1, 2)))
        assertEquals(mapOf("from" to "2024-01-01", "to" to "2024-12-31"), FinancePeriod.YEAR.query(today = today))
        assertEquals(mapOf("from" to "2024-03-01", "to" to "2024-03-01"), FinancePeriod.TODAY.query(today = today))
    }

    @Test fun customRangeRejectsImpossibleAndReversedDates() {
        assertEquals(mapOf("from" to "2024-02-29", "to" to "2024-03-01"), FinancePeriod.CUSTOM.query("29/02/2024", "01/03/2024"))
        listOf("30/02/2024" to "01/03/2024", "01/03/2024" to "29/02/2024", "" to "01/03/2024").forEach { (from, to) ->
            assertTrue(runCatching { FinancePeriod.CUSTOM.query(from, to) }.isFailure)
        }
    }
}
