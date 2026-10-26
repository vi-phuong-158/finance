package vn.phuong.financebridge

import org.junit.Assert.*
import org.junit.Test

class ManualRecordTest {
    private fun draft() = ManualDraft("11111111-1111-4111-8111-111111111111", "2024-02-29T10:22", "CHI", 12500, "Ăn uống", "CASH", "Synthetic")

    @Test fun validatesMoneyCalendarAndAccountBeforeEnqueue() {
        draft().validate()
        listOf(draft().copy(amount = 0), draft().copy(amount = 1_000_000_000_001L),
            draft().copy(dateTime = "2026-02-30T10:22"), draft().copy(account = "UNKNOWN"),
            draft().copy(note = "á".repeat(513))).forEach { assertTrue(runCatching { it.validate() }.isFailure) }
    }

    @Test fun onlyConfirmedServerStatusesAllowPayloadRemoval() {
        assertEquals("SYNCED", manualSyncState(200, "SAVED_TRANSACTION"))
        assertEquals("SYNCED", manualSyncState(200, "DUPLICATE_IGNORED"))
        assertEquals("PENDING", manualSyncState(200, null))
        assertEquals("PENDING", manualSyncState(503, "UPSTREAM_UNAVAILABLE"))
        assertEquals("PENDING", manualSyncState(429, null))
        assertEquals("BLOCKED", manualSyncState(401, null))
        assertEquals("BLOCKED", manualSyncState(409, "IDEMPOTENCY_CONFLICT"))
        assertEquals("BLOCKED", manualSyncState(503, "MANUAL_SCHEMA_MIGRATION_REQUIRED"))
    }
}
