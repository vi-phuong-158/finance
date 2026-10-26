package vn.phuong.financebridge

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID

data class ManualDraft(val requestId: String, val dateTime: String, val type: String, val amount: Long,
    val category: String, val account: String, val note: String) {
    fun validate() {
        require(UUID.fromString(requestId).version() == 4)
        require(type in setOf("THU", "CHI") && amount in 1..1_000_000_000_000L)
        require(category.isNotBlank() && account in setOf("VCB", "VIETINBANK", "BIDV", "CASH"))
        require(note.toByteArray(Charsets.UTF_8).size <= 1024)
        LocalDateTime.parse(dateTime, DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm").withResolverStyle(ResolverStyle.STRICT))
    }
}

fun manualSyncState(code: Int, status: String?): String = when {
    code in 200..299 && status in setOf("SAVED_TRANSACTION", "DUPLICATE_IGNORED", "SAVED_LOAN_EVENT", "SAVED_TRANSACTION_EDIT", "SAVED_CATEGORY_MEMORY") -> "SYNCED"
    code in setOf(400, 401, 403, 404, 405, 409, 413, 415) || status in setOf("MANUAL_SCHEMA_MIGRATION_REQUIRED", "LOAN_AUDIT_MIGRATION_REQUIRED", "EDIT_SCHEMA_MIGRATION_REQUIRED",
        "LOAN_WRITES_DISABLED", "TRANSACTION_EDITS_DISABLED", "LOAN_SIMULATION_ACTIVE", "LOAN_SOURCE_FULL", "EDIT_SOURCE_FULL", "LEDGER_SOURCE_FULL", "LOAN_LOCALE_UNSUPPORTED",
        "CATEGORY_MEMORY_WRITES_DISABLED", "CATEGORY_MEMORY_MIGRATION_REQUIRED", "CATEGORY_MEMORY_SOURCE_FULL") -> "BLOCKED"
    else -> "PENDING"
}
