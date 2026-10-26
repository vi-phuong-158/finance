package vn.phuong.financebridge

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

class ManualOutbox(context: Context) : SQLiteOpenHelper(context.applicationContext, "finance_manual_outbox.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE manual_outbox (request_id TEXT PRIMARY KEY NOT NULL, payload TEXT, state TEXT NOT NULL, created_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun enqueue(draft: ManualDraft) {
        draft.validate()
        val payload = JSONObject().put("requestId", draft.requestId).put("dateTime", draft.dateTime)
            .put("type", draft.type).put("amount", draft.amount).put("category", draft.category)
            .put("account", draft.account).put("note", draft.note).toString()
        val values = ContentValues().apply {
            put("request_id", draft.requestId); put("payload", payload); put("state", "PENDING"); put("created_at", System.currentTimeMillis())
        }
        // A saved request is immutable across retries, restart and repeated taps.
        writableDatabase.insertWithOnConflict("manual_outbox", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        val persisted = readableDatabase.rawQuery("SELECT payload,state FROM manual_outbox WHERE request_id=?", arrayOf(draft.requestId)).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0) == payload || cursor.getString(1) == "SYNCED"
        }
        check(persisted) { "SAVED_REQUEST_CHANGED" }
    }

    fun pending(): List<Pair<String, String>> = readableDatabase.rawQuery(
        "SELECT request_id,payload FROM manual_outbox WHERE state IN ('PENDING','SYNCING') ORDER BY created_at LIMIT 50", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
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
