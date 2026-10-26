package vn.phuong.financebridge

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

data class QueuedMutation(val id: String, val payload: String, val endpoint: String)

class ManualOutbox(context: Context) : SQLiteOpenHelper(context.applicationContext, "finance_manual_outbox.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE manual_outbox (request_id TEXT PRIMARY KEY NOT NULL, payload TEXT, state TEXT NOT NULL, created_at INTEGER NOT NULL, endpoint TEXT NOT NULL DEFAULT 'manual-transaction')")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE manual_outbox ADD COLUMN endpoint TEXT NOT NULL DEFAULT 'manual-transaction'")
    }

    fun enqueue(draft: ManualDraft) {
        draft.validate()
        val payload = JSONObject().put("requestId", draft.requestId).put("dateTime", draft.dateTime)
            .put("type", draft.type).put("amount", draft.amount).put("category", draft.category)
            .put("account", draft.account).put("note", draft.note).toString()
        enqueueRequest(draft.requestId, payload, "manual-transaction")
    }

    fun enqueueRequest(requestId: String, payload: String, endpoint: String) {
        require(endpoint in setOf("manual-transaction", "loan-event", "transaction-edit"))
        require(java.util.UUID.fromString(requestId).version() == 4)
        require(JSONObject(payload).getString("requestId") == requestId && payload.toByteArray(Charsets.UTF_8).size <= 8192)
        val values = ContentValues().apply {
            put("request_id", requestId); put("payload", payload); put("state", "PENDING"); put("created_at", System.currentTimeMillis()); put("endpoint", endpoint)
        }
        // A saved request is immutable across retries, restart and repeated taps.
        writableDatabase.insertWithOnConflict("manual_outbox", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        val persisted = readableDatabase.rawQuery("SELECT payload,state,endpoint FROM manual_outbox WHERE request_id=?", arrayOf(requestId)).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(2) == endpoint && (cursor.getString(0) == payload || cursor.getString(1) == "SYNCED")
        }
        check(persisted) { "SAVED_REQUEST_CHANGED" }
    }

    fun pending(): List<QueuedMutation> = readableDatabase.rawQuery(
        "SELECT request_id,payload,endpoint FROM manual_outbox WHERE state IN ('PENDING','SYNCING') ORDER BY created_at LIMIT 50", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(QueuedMutation(cursor.getString(0), cursor.getString(1), cursor.getString(2))) }
    }

    fun update(id: String, state: String) {
        require(state in setOf("PENDING", "SYNCING", "SYNCED", "BLOCKED"))
        val values = ContentValues().apply { put("state", state); if (state == "SYNCED") putNull("payload") }
        writableDatabase.update("manual_outbox", values, "request_id=?", arrayOf(id))
    }

    fun retryBlocked() { writableDatabase.execSQL("UPDATE manual_outbox SET state='PENDING' WHERE state='BLOCKED'") }

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
