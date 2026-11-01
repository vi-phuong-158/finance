package vn.phuong.financebridge

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.util.ReportCache

class ReportCacheTest {

    @Before
    fun setUp() {
        ReportCache.clear()
    }

    private fun mockDashboard(periodLabel: String): UiDashboard {
        return UiDashboard(
            monthlyIncome = 1_000_000L,
            formattedIncome = "1.000.000 ₫",
            monthlyExpense = 500_000L,
            formattedExpense = "500.000 ₫",
            netDiff = 500_000L,
            formattedNetDiff = "+500.000 ₫",
            periodLabel = periodLabel,
            categories = emptyList(),
            recentTransactions = emptyList(),
            totalTransactionsCount = 0,
            lastUpdatedIso = "2026-10-31T00:00:00Z",
            daily = emptyList(),
            banks = emptyList(),
            sources = emptyList(),
            incomeCategories = emptyList(),
            possibleDuplicateCount = 0,
            hasReportBreakdowns = true,
        )
    }

    @Test
    fun testCacheHitMissAndRefresh() {
        val queryKey = ReportCache.normalizeQueryKey("dashboard", mapOf("from" to "2026-10-01", "to" to "2026-10-31"))

        // Miss
        assertNull(ReportCache.getDashboard(queryKey))

        // Put and Hit
        val initialData = mockDashboard("Kỳ 1")
        ReportCache.putDashboard(queryKey, initialData)

        val cached = ReportCache.getDashboard(queryKey)
        assertNotNull(cached)
        assertEquals("Kỳ 1", cached?.periodLabel)

        // Refresh / Update
        val refreshedData = mockDashboard("Kỳ 1 (mới)")
        ReportCache.putDashboard(queryKey, refreshedData)

        val updated = ReportCache.getDashboard(queryKey)
        assertEquals("Kỳ 1 (mới)", updated?.periodLabel)

        // Clear
        ReportCache.clear()
        assertNull(ReportCache.getDashboard(queryKey))
    }

    @Test
    fun testNormalizeQueryKeySortsParameters() {
        val key1 = ReportCache.normalizeQueryKey("dashboard", mapOf("to" to "2026-10-31", "from" to "2026-10-01"))
        val key2 = ReportCache.normalizeQueryKey("dashboard", mapOf("from" to "2026-10-01", "to" to "2026-10-31"))

        assertEquals("Parameters must be sorted so identical queries hit same cache", key1, key2)
        assertEquals("dashboard?from=2026-10-01&to=2026-10-31", key1)
    }

    @Test
    fun testTransactionsCache() {
        val key = ReportCache.normalizeQueryKey("transactions", mapOf("category" to "Ăn uống"))
        assertNull(ReportCache.getTransactions(key))

        val list = listOf(
            UiTransaction.create(
                id = "tx1",
                amount = 50_000L,
                category = "Ăn uống",
                type = TransactionType.CHI,
                title = "Ăn trưa",
                accountOrBank = "VCB",
                source = "BANK_NOTIFICATION",
            )
        )
        ReportCache.putTransactions(key, list, "cursor_page_2")

        val cached = ReportCache.getTransactions(key)
        assertEquals(1, cached?.size)
        assertEquals("tx1", cached?.first()?.id)
        assertEquals("cursor_page_2", ReportCache.getNextCursor(key))
    }
}
