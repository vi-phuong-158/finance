package vn.phuong.financebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.util.FinanceFormatters

class FinanceFormattersTest {

    @Test
    fun formatsVndAmountsWithVietnameseDotGrouping() {
        assertEquals("104.051\u00A0đ", FinanceFormatters.formatVnd(104051L))
        assertEquals("1.250.000\u00A0đ", FinanceFormatters.formatVnd(1250000L))
        assertEquals("25.600.000\u00A0đ", FinanceFormatters.formatVnd(25600000L))
        assertEquals("0\u00A0đ", FinanceFormatters.formatVnd(0L))
    }

    @Test
    fun formatsSignedAmountsCorrectly() {
        assertEquals("+1.250.000\u00A0đ", FinanceFormatters.formatVnd(1250000L, includeSign = true, isIncome = true))
        assertEquals("-500.000\u00A0đ", FinanceFormatters.formatVnd(500000L, includeSign = true, isIncome = false))
    }

    @Test
    fun formatsFriendlyDates() {
        val now = System.currentTimeMillis()
        val todayFormatted = FinanceFormatters.formatFriendlyDate(now)
        assertTrue(todayFormatted.startsWith("Hôm nay,"))

        val yesterday = now - 24 * 60 * 60 * 1000L
        val yesterdayFormatted = FinanceFormatters.formatFriendlyDate(yesterday)
        assertTrue(yesterdayFormatted.startsWith("Hôm qua,"))
    }

    @Test
    fun uiTransactionCreationPreservesDataAndFormats() {
        val tx = UiTransaction.create(
            id = "test:1",
            type = TransactionType.CHI,
            amount = 45000L,
            title = "Cà phê",
            subtitle = "VCB",
            category = "Ăn uống",
            accountOrBank = "VCB",
        )
        assertEquals("test:1", tx.id)
        assertEquals(TransactionType.CHI, tx.type)
        assertEquals(45000L, tx.amount)
        assertEquals("-45.000\u00A0đ", tx.formattedAmount)
        assertEquals("Ăn uống", tx.category)
    }

    @Test
    fun parsesDateOnlyAtVietnamMidnightAndRejectsCorruptDates() {
        assertEquals(1709139600000L, FinanceFormatters.parseIsoDateToMillis("2024-02-29"))
        assertEquals("29/02/2024", FinanceFormatters.formatIsoDate("2024-02-29"))
        listOf(null, "", "2026-02-30", "2026-01-01garbage").forEach { date ->
            assertTrue(runCatching { FinanceFormatters.parseIsoDateToMillis(date) }.isFailure)
        }
    }

    @Test
    fun negativeNetFlowNeverLosesItsSign() {
        assertEquals("-12.500\u00A0đ", FinanceFormatters.formatVnd(-12500L))
        assertEquals("-12.500\u00A0đ", FinanceFormatters.formatVnd(-12500L, includeSign = true))
    }

    @Test
    fun annualRateUsesVietnameseDecimalAndRejectsInvalidValues() {
        assertEquals("5,9%/năm", FinanceFormatters.formatAnnualRate(0.059))
        assertEquals("12%/năm", FinanceFormatters.formatAnnualRate(0.12))
        assertEquals("0%/năm", FinanceFormatters.formatAnnualRate(0.0))
        assertTrue(runCatching { FinanceFormatters.formatAnnualRate(Double.NaN) }.isFailure)
    }
}
