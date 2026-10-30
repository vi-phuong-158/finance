package vn.phuong.financebridge

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// Synthetic ids and values only.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManualOutboxTest {
    private lateinit var app: Application
    private lateinit var outbox: ManualOutbox
    private val tx = "synthetic-transaction-1"
    private val a = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val b = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
    private val c = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"

    private fun edit(id: String, base: String = "0", category: String = "Ăn uống", target: String = tx) = JSONObject().put("requestId", id)
        .put("transactionId", target).put("baseRevision", base).put("category", category).put("note", "").put("excluded", false).put("type", "CHI").toString()

    @Before fun open() { app = RuntimeEnvironment.getApplication(); app.deleteDatabase("finance_manual_outbox.db"); outbox = ManualOutbox(app) }
    @After fun close() = outbox.close()

    @Test fun upgradeFromV2KeepsEveryQueuedRowAndBackfillsEditTargets() {
        outbox.close(); app.deleteDatabase("finance_manual_outbox.db")
        SQLiteDatabase.openOrCreateDatabase(app.getDatabasePath("finance_manual_outbox.db").apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL("CREATE TABLE manual_outbox (request_id TEXT PRIMARY KEY NOT NULL, payload TEXT, state TEXT NOT NULL, created_at INTEGER NOT NULL, endpoint TEXT NOT NULL DEFAULT 'manual-transaction')")
            db.execSQL("INSERT INTO manual_outbox VALUES (?,?,?,?,?)", arrayOf(a, edit(a), "BLOCKED", 1, "transaction-edit"))
            db.execSQL("INSERT INTO manual_outbox VALUES (?,?,?,?,?)", arrayOf(b, JSONObject().put("requestId", b).toString(), "PENDING", 2, "manual-transaction"))
            db.execSQL("INSERT INTO manual_outbox VALUES (?,?,?,?,?)", arrayOf(c, null, "SYNCED", 3, "transaction-edit"))
            db.version = 2
        }
        outbox = ManualOutbox(app)
        assertEquals(1 to 1, outbox.counts())
        assertEquals(listOf(b), outbox.pending().map { it.id })
        val blocked = outbox.blocked().single()
        assertEquals(a, blocked.id); assertEquals(tx, blocked.targetId); assertNull(blocked.lastError); assertEquals(edit(a), blocked.payload)
        // Pre-v3 blocks carry no reason, so they stay resendable; the resend records the real reason.
        assertEquals(QueuedEdit(a, "BLOCKED", null), outbox.activeEdit(tx))
        assertTrue(outbox.retry(a))
        assertEquals(2 to 0, outbox.counts())
    }

    @Test fun aSecondEditOfTheSameTransactionIsRefusedWhileTheFirstIsLive() {
        outbox.enqueueRequest(a, edit(a), "transaction-edit")
        outbox.enqueueRequest(a, edit(a), "transaction-edit") // repeated tap of the same save stays idempotent
        assertEquals("EDIT_ALREADY_QUEUED", runCatching { outbox.enqueueRequest(b, edit(b), "transaction-edit") }.exceptionOrNull()?.message)
        outbox.enqueueRequest(c, edit(c, target = "synthetic-transaction-2"), "transaction-edit")
        outbox.update(a, "BLOCKED", "UNAUTHORIZED")
        assertEquals("EDIT_ALREADY_QUEUED", runCatching { outbox.enqueueRequest(b, edit(b), "transaction-edit") }.exceptionOrNull()?.message)
        outbox.update(a, "SYNCED")
        outbox.enqueueRequest(b, edit(b, base = a), "transaction-edit")
        assertEquals(a, outbox.latestSyncedEdit(tx))
        assertEquals(QueuedEdit(b, "PENDING", null), outbox.activeEdit(tx))
    }

    @Test fun resendSkipsConflictsAndPermanentRejections() {
        outbox.enqueueRequest(a, edit(a), "transaction-edit")
        outbox.enqueueRequest(b, JSONObject().put("requestId", b).toString(), "manual-transaction")
        outbox.enqueueRequest(c, JSONObject().put("requestId", c).toString(), "loan-event")
        outbox.update(a, "BLOCKED", "EDIT_CONFLICT"); outbox.update(b, "BLOCKED", "INVALID_PAYLOAD"); outbox.update(c, "BLOCKED", "UNAUTHORIZED")
        assertEquals(1, outbox.retryBlocked())
        assertEquals(listOf(c), outbox.pending().map { it.id })
        assertFalse(outbox.retry(a)); assertFalse(outbox.retry(b))
        assertEquals(listOf("EDIT_CONFLICT", "INVALID_PAYLOAD"), outbox.blocked().map { it.lastError })
    }

    @Test fun conflictIsReplacedAtomicallyByAnEditOnTheLatestRevision() {
        outbox.enqueueRequest(a, edit(a), "transaction-edit")
        // Not blocked yet: the worker may be sending it, so it cannot be replaced or cancelled.
        assertEquals("REQUEST_NOT_BLOCKED", runCatching { outbox.supersede(a, b, edit(b, base = c)) }.exceptionOrNull()?.message)
        assertEquals("REQUEST_NOT_BLOCKED", runCatching { outbox.cancel(a) }.exceptionOrNull()?.message)
        outbox.update(a, "BLOCKED", "EDIT_CONFLICT")
        // A failing replacement leaves the conflict untouched.
        assertTrue(runCatching { outbox.supersede(a, "not-a-uuid", edit(b, base = c)) }.isFailure)
        assertEquals(QueuedEdit(a, "BLOCKED", "EDIT_CONFLICT"), outbox.activeEdit(tx))
        outbox.supersede(a, b, edit(b, base = c, category = "Mua sắm"))
        assertEquals(QueuedEdit(b, "PENDING", null), outbox.activeEdit(tx))
        assertEquals(listOf(QueuedMutation(b, edit(b, base = c, category = "Mua sắm"), "transaction-edit")), outbox.pending())
        assertTrue(outbox.blocked().isEmpty())
    }

    @Test fun cancelDropsTheBlockedPayloadAndFreesTheTransaction() {
        outbox.enqueueRequest(a, edit(a), "transaction-edit")
        outbox.update(a, "BLOCKED", "TRANSACTION_NOT_FOUND")
        outbox.cancel(a)
        assertEquals(0 to 0, outbox.counts())
        assertNull(outbox.activeEdit(tx)); assertNull(outbox.latestSyncedEdit(tx))
        // A cancelled id can never be re-queued with its old payload.
        assertEquals("SAVED_REQUEST_CHANGED", runCatching { outbox.enqueueRequest(a, edit(a), "transaction-edit") }.exceptionOrNull()?.message)
        outbox.enqueueRequest(b, edit(b), "transaction-edit")
        assertEquals(QueuedEdit(b, "PENDING", null), outbox.activeEdit(tx))
    }

    @Test fun activeEditsReturnsPendingSyncingAndBlockedEdits() {
        outbox.enqueueRequest(a, edit(a, target = "tx-1"), "transaction-edit")
        outbox.enqueueRequest(b, edit(b, target = "tx-2"), "transaction-edit")
        outbox.update(b, "BLOCKED", "EDIT_CONFLICT")
        outbox.enqueueRequest(c, JSONObject().put("requestId", c).toString(), "manual-transaction")

        val edits = outbox.activeEdits()
        assertEquals(2, edits.size)
        assertEquals("tx-1", edits[0].targetId)
        assertEquals("PENDING", edits[0].state)
        assertEquals("tx-2", edits[1].targetId)
        assertEquals("BLOCKED", edits[1].state)
    }
}
