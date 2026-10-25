package vn.phuong.financebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.util.FinanceFormatters

class FinanceFormattersTest {

    @Test
    fun formatsVndAmountsWithVietnameseDotGrouping() {
        assertEquals("104.051 ₫", FinanceFormatters.formatVnd(104051L))
        assertEquals("1.250.000 ₫", FinanceFormatters.formatVnd(1250000L))
        assertEquals("25.600.000 ₫", FinanceFormatters.formatVnd(25600000L))
        assertEquals("0 ₫", FinanceFormatters.formatVnd(0L))
    }

    @Test
    fun formatsSignedAmountsCorrectly() {
        assertEquals("+ 1.250.000 ₫", FinanceFormatters.formatVnd(1250000L, includeSign = true, isIncome = true))
        assertEquals("- 500.000 ₫", FinanceFormatters.formatVnd(500000L, includeSign = true, isIncome = false))
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
        assertEquals("- 45.000 ₫", tx.formattedAmount)
        assertEquals("Ăn uống", tx.category)
    }

    @Test
    fun dashboardPreviewContainsExpectedStructure() {
        val dashboard = UiDashboard.preview()
        assertTrue(dashboard.categories.isNotEmpty())
        assertTrue(dashboard.recentTransactions.isNotEmpty())
        assertEquals(1581, dashboard.totalTransactionsCount)
        assertEquals(779604930L, dashboard.monthlyIncome)
        assertEquals(569649820L, dashboard.monthlyExpense)
        assertEquals(209955110L, dashboard.totalBalance)
    }
}
