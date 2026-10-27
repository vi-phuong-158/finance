package vn.phuong.financebridge

import org.junit.Assert.*
import org.junit.Test

class EditConflictTest {
    private val rev = "22222222-2222-4222-8222-222222222222"

    @Test fun onlyConflictsAndUnfixableRequestsAreKeptFromBlindResend() {
        assertEquals(BlockKind.EDIT_CONFLICT, blockKind("EDIT_CONFLICT"))
        listOf("IDEMPOTENCY_CONFLICT", "TRANSACTION_NOT_FOUND", "INVALID_EDIT", "INVALID_PAYLOAD", "HTTP_400", "ENDPOINT_NOT_ALLOWED")
            .forEach { assertEquals(it, BlockKind.PERMANENT, blockKind(it)) }
        // Fixable on the server or by re-entering the connection code; a resend reuses the same requestId.
        listOf(null, "UNAUTHORIZED", "HTTP_401", "CONNECTION_NOT_CONFIGURED", "TRANSACTION_EDITS_DISABLED", "EDIT_SCHEMA_MIGRATION_REQUIRED", "SOMETHING_NEW")
            .forEach { assertEquals(it.toString(), BlockKind.RETRYABLE, blockKind(it)) }
        assertTrue(blockMessage("EDIT_CONFLICT").contains("Tải bản mới"))
        assertTrue(blockMessage(null).isNotBlank())
    }

    @Test fun storedErrorIsAShortCodeNeverAServerBody() {
        assertEquals("EDIT_CONFLICT", manualSyncErrorCode(409, "EDIT_CONFLICT"))
        assertEquals("HTTP_401", manualSyncErrorCode(401, null))
        assertEquals("HTTP_400", manualSyncErrorCode(400, "Invalid note: Nguyễn Văn A 0123456789"))
        assertEquals("HTTP_409", manualSyncErrorCode(409, "X".repeat(65)))
    }

    @Test fun aSecondEditWaitsForTheFirstAndAConflictMustBeResolved() {
        assertNull(EditGuard.queued(null))
        assertEquals(EditGate.WAIT_FOR_SYNC, EditGuard.queued(QueuedEdit(rev, "PENDING", null)))
        assertEquals(EditGate.WAIT_FOR_SYNC, EditGuard.queued(QueuedEdit(rev, "SYNCING", null)))
        assertEquals(EditGate.WAIT_FOR_SYNC, EditGuard.queued(QueuedEdit(rev, "BLOCKED", "UNAUTHORIZED")))
        assertEquals(EditGate.RESOLVE_CONFLICT, EditGuard.queued(QueuedEdit(rev, "BLOCKED", "EDIT_CONFLICT")))
    }

    @Test fun offlineEditTrustsTheListOnlyWhenItShowsTheLastAcknowledgedEdit() {
        assertEquals(EditGate.READY_SHOWN, EditGuard.offline(null, "0"))
        assertEquals(EditGate.READY_SHOWN, EditGuard.offline(rev, rev))
        // The list was loaded before the last edit synced: its revision is stale and would conflict.
        assertEquals(EditGate.NEEDS_NETWORK, EditGuard.offline(rev, "0"))
    }

    @Test fun diffShowsEveryFieldAndMarksOnlyChangedOnes() {
        val diff = EditGuard.diff(EditFields("CHI", "Ăn uống", "", false), EditFields("CHI", "Mua sắm", "quà", true))
        assertEquals(listOf("Loại", "Danh mục", "Ghi chú", "Báo cáo"), diff.map { it.label })
        assertEquals(listOf(false, true, true, true), diff.map { it.changed })
        assertEquals("(trống)", diff[2].server)
    }
}
