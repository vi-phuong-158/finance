package vn.phuong.financebridge.ui.util

import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.model.UiTransaction

/**
 * In-memory cache for Report screens (UI V2 Phase D / P6):
 * - Scoped to process lifetime.
 * - Stores dashboard summaries and category transaction lists by query key.
 * - Thread-safe.
 * - Does NOT persist financial data to disk.
 */
object ReportCache {
    private val dashboardCache = mutableMapOf<String, UiDashboard>()
    private val transactionsCache = mutableMapOf<String, List<UiTransaction>>()
    private val nextCursorCache = mutableMapOf<String, String?>()

    fun normalizeQueryKey(endpoint: String, query: Map<String, String>): String {
        val sortedQuery = query.entries.sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }
        return if (sortedQuery.isEmpty()) endpoint else "$endpoint?$sortedQuery"
    }

    fun getDashboard(key: String): UiDashboard? = synchronized(dashboardCache) {
        dashboardCache[key]
    }

    fun putDashboard(key: String, data: UiDashboard) = synchronized(dashboardCache) {
        dashboardCache[key] = data
    }

    fun getTransactions(key: String): List<UiTransaction>? = synchronized(dashboardCache) {
        transactionsCache[key]
    }

    fun getNextCursor(key: String): String? = synchronized(dashboardCache) {
        nextCursorCache[key]
    }

    fun putTransactions(key: String, data: List<UiTransaction>, nextCursor: String? = null) = synchronized(dashboardCache) {
        transactionsCache[key] = data
        nextCursorCache[key] = nextCursor
    }

    fun clear() = synchronized(dashboardCache) {
        dashboardCache.clear()
        transactionsCache.clear()
        nextCursorCache.clear()
    }

    fun size(): Int = synchronized(dashboardCache) {
        dashboardCache.size + transactionsCache.size
    }
}
