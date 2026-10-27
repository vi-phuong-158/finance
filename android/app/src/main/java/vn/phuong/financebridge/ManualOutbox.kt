package vn.phuong.financebridge

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

data class QueuedMutation(val id: String, val payload: String, val endpoint: String)
data class BlockedMutation(val id: String, val endpoint: String, val payload: String?, val targetId: String?, val lastError: String?, val createdAt: Long)

class ManualOutbox(context: Context) : SQLiteOpenHelper(context.applicationContext, "finance_manual_outbox.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE manual_outbox (request_id TEXT PRIMARY KEY NOT NULL, payload TEXT, state TEXT NOT NULL, created_at INTEGER NOT NULL, endpoint TEXT NOT NULL DEFAULT 'manual-transaction', last_error TEXT, target_id TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE manual_outbox ADD COLUMN endpoint TEXT NOT NULL DEFAULT 'manual-transaction'")
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE manual_outbox ADD COLUMN last_error TEXT")
            db.execSQL("ALTER TABLE manual_outbox ADD COLUMN target_id TEXT")
            // Edits still holding a payload get their target so a second edit of the same transaction can be refused.
            db.rawQuery("SELECT request_id,payload FROM manual_outbox WHERE endpoint='transaction-edit' AND payload IS NOT NULL", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val target = runCatching { JSONObject(cursor.getString(1)).getString("transactionId") }.getOrNull() ?: continue
                    db.execSQL("UPDATE manual_outbox SET target_id=? WHERE request_id=?", arrayOf(target, cursor.getString(0)))
                }
            }
        }
    }

    fun enqueue(draft: ManualDraft) {
        draft.validate()
        val payload = JSONObject().put("requestId", draft.requestId).put("dateTime", draft.dateTime)
            .put("type", draft.type).put("amount", draft.amount).put("category", draft.category)
            .put("account", draft.account).put("note", draft.note).toString()
        enqueueRequest(draft.requestId, payload, "manual-transaction")
    }

    fun enqueueRequest(requestId: String, payload: String, endpoint: String) = inTransaction { db -> insert(db, requestId, payload, endpoint) }

    // Replaces a conflicting edit with one based on the latest revision, atomically, so the transaction never has two live edits.
    fun supersede(oldId: String, requestId: String, payload: String) = inTransaction { db ->
        val values = ContentValues().apply { put("state", "SUPERSEDED"); putNull("payload") }
        check(db.update("manual_outbox", values, "request_id=? AND state='BLOCKED' AND endpoint='transaction-edit'", arrayOf(oldId)) == 1) { "REQUEST_NOT_BLOCKED" }
        insert(db, requestId, payload, "transaction-edit")
    }

    // Only BLOCKED rows: the worker never touches them, so the UI cannot race an in-flight send.
    fun cancel(id: String) {
        val values = ContentValues().apply { put("state", "CANCELLED"); putNull("payload") }
        check(writableDatabase.update("manual_outbox", values, "request_id=? AND state='BLOCKED'", arrayOf(id)) == 1) { "REQUEST_NOT_BLOCKED" }
    }

    private fun insert(db: SQLiteDatabase, requestId: String, payload: String, endpoint: String) {
        require(endpoint in setOf("manual-transaction", "loan-event", "transaction-edit", "category-memory"))
        require(java.util.UUID.fromString(requestId).version() == 4)
        require(JSONObject(payload).getString("requestId") == requestId && payload.toByteArray(Charsets.UTF_8).size <= 8192)
        val target = if (endpoint == "transaction-edit") JSONObject(payload).getString("transactionId") else null
        if (target != null) {
            val other = db.rawQuery("SELECT 1 FROM manual_outbox WHERE target_id=? AND request_id<>? AND state IN ('PENDING','SYNCING','BLOCKED') LIMIT 1",
                arrayOf(target, requestId)).use { it.moveToFirst() }
            check(!other) { "EDIT_ALREADY_QUEUED" }
        }
        val values = ContentValues().apply {
            put("request_id", requestId); put("payload", payload); put("state", "PENDING"); put("created_at", System.currentTimeMillis()); put("endpoint", endpoint)
            put("target_id", target)
        }
        // A saved request is immutable across retries, restart and repeated taps.
        db.insertWithOnConflict("manual_outbox", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        val persisted = db.rawQuery("SELECT payload,state,endpoint FROM manual_outbox WHERE request_id=?", arrayOf(requestId)).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(2) == endpoint && (cursor.getString(0) == payload || cursor.getString(1) == "SYNCED")
        }
        check(persisted) { "SAVED_REQUEST_CHANGED" }
    }

    private fun inTransaction(block: (SQLiteDatabase) -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try { block(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }

    fun pending(): List<QueuedMutation> = readableDatabase.rawQuery(
        "SELECT request_id,payload,endpoint FROM manual_outbox WHERE state IN ('PENDING','SYNCING') ORDER BY created_at LIMIT 50", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(QueuedMutation(cursor.getString(0), cursor.getString(1), cursor.getString(2))) }
    }

    fun blocked(): List<BlockedMutation> = readableDatabase.rawQuery(
        "SELECT request_id,endpoint,payload,target_id,last_error,created_at FROM manual_outbox WHERE state='BLOCKED' ORDER BY created_at", null).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(BlockedMutation(cursor.getString(0), cursor.getString(1), cursor.getString(2),
                cursor.getString(3), cursor.getString(4), cursor.getLong(5)))
        }
    }

    fun activeEdit(transactionId: String): QueuedEdit? = readableDatabase.rawQuery(
        "SELECT request_id,state,last_error FROM manual_outbox WHERE target_id=? AND state IN ('PENDING','SYNCING','BLOCKED') ORDER BY created_at DESC LIMIT 1",
        arrayOf(transactionId)).use { cursor -> if (cursor.moveToFirst()) QueuedEdit(cursor.getString(0), cursor.getString(1), cursor.getString(2)) else null }

    fun latestSyncedEdit(transactionId: String): String? = readableDatabase.rawQuery(
        "SELECT request_id FROM manual_outbox WHERE target_id=? AND state='SYNCED' ORDER BY created_at DESC LIMIT 1",
        arrayOf(transactionId)).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    fun update(id: String, state: String, lastError: String? = null) {
        require(state in setOf("PENDING", "SYNCING", "SYNCED", "BLOCKED"))
        val values = ContentValues().apply { put("state", state); put("last_error", lastError); if (state == "SYNCED") putNull("payload") }
        writableDatabase.update("manual_outbox", values, "request_id=?", arrayOf(id))
    }

    // Conflicts and permanent rejections would fail again unchanged; they wait for the user in "Thay đổi cần xử lý".
    fun retryBlocked(): Int = blocked().filter { blockKind(it.lastError) == BlockKind.RETRYABLE }.count { resend(it.id) }

    fun retry(id: String): Boolean = blocked().any { it.id == id && blockKind(it.lastError) == BlockKind.RETRYABLE } && resend(id)

    private fun resend(id: String) = writableDatabase.update("manual_outbox", ContentValues().apply { put("state", "PENDING") },
        "request_id=? AND state='BLOCKED'", arrayOf(id)) == 1

    fun counts(): Pair<Int, Int> = readableDatabase.rawQuery(
        "SELECT state,COUNT(*) FROM manual_outbox GROUP BY state", null).use { cursor ->
        var pending = 0; var blocked = 0
        while (cursor.moveToNext()) {
            when (cursor.getString(0)) {
                "PENDING", "SYNCING" -> pending += cursor.getInt(1)
                "BLOCKED" -> blocked += cursor.getInt(1)
            }
        }
        pending to blocked
    }
}
