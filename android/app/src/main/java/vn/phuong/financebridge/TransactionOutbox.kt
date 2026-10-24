package vn.phuong.financebridge

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PendingTransaction(
    val eventId: String,
    val bank: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val state: String,
)

data class OutboxEntry(
    val eventId: String,
    val bank: String,
    val state: String,
    val errorCode: String?,
    val updatedAt: Long,
)

data class OutboxSummary(
    val pendingCount: Int,
    val blockedCount: Int,
    val recentEntries: List<OutboxEntry>,
)

class TransactionOutbox(context: Context) {
    private val helper = OutboxDatabase(context.applicationContext)

    /** Inserts once. An event already marked terminal or blocked is never silently re-queued. */
    fun enqueueIfNewOrPending(item: PendingTransaction): Boolean {
        val database = helper.writableDatabase
        database.beginTransaction()
        try {
            val retentionCutoff = System.currentTimeMillis() - TERMINAL_RETENTION_MS
            database.delete(
                TABLE,
                "$COL_STATE NOT IN (?, ?) AND $COL_UPDATED_AT < ?",
                arrayOf(STATE_PENDING, STATE_BLOCKED, retentionCutoff.toString()),
            )
            database.insertWithOnConflict(
                TABLE,
                null,
                item.toValues(),
                SQLiteDatabase.CONFLICT_IGNORE,
            )
            val state = database.query(
                TABLE,
                arrayOf(COL_STATE),
                "$COL_EVENT_ID = ?",
                arrayOf(item.eventId),
                null,
                null,
                null,
                "1",
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            database.setTransactionSuccessful()
            return state == STATE_PENDING
        } finally {
            database.endTransaction()
        }
    }

    fun get(eventId: String): PendingTransaction? = helper.readableDatabase.query(
        TABLE,
        arrayOf(COL_EVENT_ID, COL_BANK, COL_PACKAGE, COL_TITLE, COL_TEXT, COL_POSTED_AT, COL_STATE),
        "$COL_EVENT_ID = ?",
        arrayOf(eventId),
        null,
        null,
        null,
        "1",
    ).use { cursor ->
        if (!cursor.moveToFirst()) return null
        val titleIndex = cursor.getColumnIndexOrThrow(COL_TITLE)
        val textIndex = cursor.getColumnIndexOrThrow(COL_TEXT)
        PendingTransaction(
            eventId = cursor.getString(0),
            bank = cursor.getString(1),
            packageName = cursor.getString(2),
            title = if (cursor.isNull(titleIndex)) "" else cursor.getString(titleIndex),
            text = if (cursor.isNull(textIndex)) "" else cursor.getString(textIndex),
            postedAt = cursor.getLong(5),
            state = cursor.getString(6),
        )
    }

    fun markAttempt(eventId: String) {
        helper.writableDatabase.execSQL(
            "UPDATE $TABLE SET $COL_ATTEMPTS = $COL_ATTEMPTS + 1, $COL_ERROR = NULL, $COL_UPDATED_AT = ? WHERE $COL_EVENT_ID = ?",
            arrayOf(System.currentTimeMillis(), eventId),
        )
    }

    fun markRetryable(eventId: String, errorCode: String) = update(eventId) {
        put(COL_STATE, STATE_PENDING)
        put(COL_ERROR, safeErrorCode(errorCode))
        put(COL_UPDATED_AT, System.currentTimeMillis())
    }

    fun markBlocked(eventId: String, errorCode: String) = update(eventId) {
        put(COL_STATE, STATE_BLOCKED)
        put(COL_ERROR, safeErrorCode(errorCode))
        put(COL_UPDATED_AT, System.currentTimeMillis())
    }

    fun markTerminal(eventId: String, resultStatus: String) = update(eventId) {
        put(COL_STATE, safeTerminalState(resultStatus))
        put(COL_TITLE, null as String?)
        put(COL_TEXT, null as String?)
        put(COL_ERROR, null as String?)
        put(COL_UPDATED_AT, System.currentTimeMillis())
    }

    fun retryBlocked(eventId: String): Boolean {
        val values = ContentValues().apply {
            put(COL_STATE, STATE_PENDING)
            put(COL_ERROR, null as String?)
            put(COL_UPDATED_AT, System.currentTimeMillis())
        }
        return helper.writableDatabase.update(
            TABLE,
            values,
            "$COL_EVENT_ID = ? AND $COL_STATE = ? AND $COL_TEXT IS NOT NULL",
            arrayOf(eventId, STATE_BLOCKED),
        ) == 1
    }

    fun blockedEventIds(): List<String> = helper.readableDatabase.query(
        TABLE,
        arrayOf(COL_EVENT_ID),
        "$COL_STATE = ? AND $COL_TEXT IS NOT NULL",
        arrayOf(STATE_BLOCKED),
        null,
        null,
        "$COL_CREATED_AT ASC",
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun pendingEventIds(): List<String> = helper.readableDatabase.query(
        TABLE,
        arrayOf(COL_EVENT_ID),
        "$COL_STATE = ? AND $COL_TEXT IS NOT NULL",
        arrayOf(STATE_PENDING),
        null,
        null,
        "$COL_CREATED_AT ASC",
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun summary(limit: Int = 20): OutboxSummary {
        val counts = helper.readableDatabase.rawQuery(
            "SELECT $COL_STATE, COUNT(*) FROM $TABLE GROUP BY $COL_STATE",
            null,
        ).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getInt(1)) }
        }

        val entries = helper.readableDatabase.query(
            TABLE,
            arrayOf(COL_EVENT_ID, COL_BANK, COL_STATE, COL_ERROR, COL_UPDATED_AT),
            null,
            null,
            null,
            null,
            "$COL_UPDATED_AT DESC",
            limit.coerceIn(1, MAX_RECENT_ENTRIES).toString(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val errorIndex = cursor.getColumnIndexOrThrow(COL_ERROR)
                    add(
                        OutboxEntry(
                            eventId = cursor.getString(0),
                            bank = cursor.getString(1),
                            state = cursor.getString(2),
                            errorCode = if (cursor.isNull(errorIndex)) null else cursor.getString(errorIndex),
                            updatedAt = cursor.getLong(4),
                        ),
                    )
                }
            }
        }

        return OutboxSummary(
            pendingCount = counts[STATE_PENDING] ?: 0,
            blockedCount = counts[STATE_BLOCKED] ?: 0,
            recentEntries = entries,
        )
    }

    fun purgeOldTerminalEntries(now: Long = System.currentTimeMillis()) {
        helper.writableDatabase.delete(
            TABLE,
            "$COL_STATE NOT IN (?, ?) AND $COL_UPDATED_AT < ?",
            arrayOf(STATE_PENDING, STATE_BLOCKED, (now - TERMINAL_RETENTION_MS).toString()),
        )
    }

    private inline fun update(eventId: String, crossinline values: ContentValues.() -> Unit) {
        val contentValues = ContentValues().apply {
            values()
        }
        helper.writableDatabase.update(TABLE, contentValues, "$COL_EVENT_ID = ?", arrayOf(eventId))
    }

    private fun PendingTransaction.toValues(): ContentValues = ContentValues().apply {
        put(COL_EVENT_ID, eventId)
        put(COL_BANK, bank)
        put(COL_PACKAGE, packageName)
        put(COL_TITLE, title)
        put(COL_TEXT, text)
        put(COL_POSTED_AT, postedAt)
        put(COL_STATE, state)
        put(COL_ATTEMPTS, 0)
        put(COL_CREATED_AT, System.currentTimeMillis())
        put(COL_UPDATED_AT, System.currentTimeMillis())
    }

    private fun safeErrorCode(value: String): String = value.takeIf { it.matches(ERROR_CODE) } ?: "REQUEST_FAILED"

    private fun safeTerminalState(value: String): String = value.takeIf { it in TERMINAL_STATES } ?: STATE_DROPPED

    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_BLOCKED = "BLOCKED"
        const val STATE_SAVED_TRANSACTION = "SAVED_TRANSACTION"
        const val STATE_SAVED_RAW_CAPTURE = "SAVED_RAW_CAPTURE"
        const val STATE_DUPLICATE = "DUPLICATE_IGNORED"
        const val STATE_DROPPED = "DROPPED_SENSITIVE"
        const val STATE_DROPPED_NON_TRANSACTION = "DROPPED_NON_TRANSACTION"

        private const val TABLE = "transaction_outbox"
        private const val COL_EVENT_ID = "event_id"
        private const val COL_BANK = "bank"
        private const val COL_PACKAGE = "package_name"
        private const val COL_TITLE = "title"
        private const val COL_TEXT = "text"
        private const val COL_POSTED_AT = "posted_at"
        private const val COL_STATE = "state"
        private const val COL_ATTEMPTS = "attempts"
        private const val COL_ERROR = "last_error_code"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_UPDATED_AT = "updated_at"
        private const val TERMINAL_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        private const val MAX_RECENT_ENTRIES = 100
        private val ERROR_CODE = Regex("[A-Z0-9_]{1,48}")
        private val TERMINAL_STATES = setOf(
            STATE_SAVED_TRANSACTION,
            STATE_SAVED_RAW_CAPTURE,
            STATE_DUPLICATE,
            STATE_DROPPED,
            STATE_DROPPED_NON_TRANSACTION,
        )
    }

    private class OutboxDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE transaction_outbox (
                    event_id TEXT PRIMARY KEY NOT NULL,
                    bank TEXT NOT NULL,
                    package_name TEXT NOT NULL,
                    title TEXT,
                    text TEXT,
                    posted_at INTEGER NOT NULL,
                    state TEXT NOT NULL,
                    attempts INTEGER NOT NULL DEFAULT 0,
                    last_error_code TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )""".trimIndent(),
            )
            db.execSQL("CREATE INDEX transaction_outbox_state_created_idx ON transaction_outbox(state, created_at)")
            db.execSQL("CREATE INDEX transaction_outbox_updated_idx ON transaction_outbox(updated_at)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2 && newVersion >= 2) {
                db.execSQL("CREATE INDEX IF NOT EXISTS transaction_outbox_updated_idx ON transaction_outbox(updated_at)")
            }
        }

        companion object {
            private const val DATABASE_NAME = "finance_bridge_outbox.db"
            private const val DATABASE_VERSION = 2
        }
    }
}

