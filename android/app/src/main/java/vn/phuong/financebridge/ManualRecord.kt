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

enum class BlockKind { RETRYABLE, EDIT_CONFLICT, PERMANENT }

// Requests are idempotent by requestId, so resending is always safe; only codes that can never succeed
// with the same payload are permanent. Unknown or missing codes (rows blocked before v3) stay retryable.
private val PERMANENT_BLOCKS = setOf("IDEMPOTENCY_CONFLICT", "TRANSACTION_NOT_FOUND", "LOAN_NOT_FOUND", "INTEREST_OVERPAYMENT",
    "METHOD_NOT_ALLOWED", "UNSUPPORTED_MEDIA_TYPE", "PAYLOAD_TOO_LARGE", "ENDPOINT_NOT_ALLOWED",
    "HTTP_400", "HTTP_404", "HTTP_405", "HTTP_413", "HTTP_415")

fun blockKind(code: String?): BlockKind = when {
    code == "EDIT_CONFLICT" -> BlockKind.EDIT_CONFLICT
    code != null && (code in PERMANENT_BLOCKS || code.startsWith("INVALID_")) -> BlockKind.PERMANENT
    else -> BlockKind.RETRYABLE
}

// Only a short server code is ever stored; anything else is replaced by the HTTP status.
fun manualSyncErrorCode(httpCode: Int, error: String?): String = error?.takeIf { Regex("[A-Z_]{1,64}").matches(it) } ?: "HTTP_$httpCode"

fun blockMessage(code: String?): String = when (code) {
    null -> "Bị chặn từ bản cũ, chưa rõ nguyên nhân. Gửi lại để kiểm tra."
    "EDIT_CONFLICT" -> "Giao dịch đã đổi trên máy chủ sau khi bạn sửa. Tải bản mới để đối chiếu."
    "UNAUTHORIZED", "HTTP_401", "HTTP_403", "CONNECTION_NOT_CONFIGURED" -> "Mã kết nối chưa đúng hoặc chưa nhập. Kiểm tra trong Khác → Kết nối & hệ thống rồi gửi lại."
    "TRANSACTION_EDITS_DISABLED", "LOAN_WRITES_DISABLED", "CATEGORY_MEMORY_WRITES_DISABLED" -> "Máy chủ đang tắt tính năng này. Gửi lại sau khi bật."
    "MANUAL_SCHEMA_MIGRATION_REQUIRED", "EDIT_SCHEMA_MIGRATION_REQUIRED", "LOAN_AUDIT_MIGRATION_REQUIRED", "CATEGORY_MEMORY_MIGRATION_REQUIRED" ->
        "Sổ trên máy chủ cần nâng cấp. Gửi lại sau khi nâng cấp xong."
    "LOAN_SOURCE_FULL", "EDIT_SOURCE_FULL", "LEDGER_SOURCE_FULL", "CATEGORY_MEMORY_SOURCE_FULL" -> "Sổ trên máy chủ đã đầy. Gửi lại sau khi xử lý trên máy chủ."
    "TRANSACTION_NOT_FOUND" -> "Không còn tìm thấy giao dịch này trên máy chủ."
    "IDEMPOTENCY_CONFLICT" -> "Máy chủ đã nhận một yêu cầu cùng mã nhưng khác nội dung."
    else -> if (blockKind(code) == BlockKind.PERMANENT) "Máy chủ từ chối yêu cầu ($code)." else "Chưa gửi được ($code). Thử gửi lại."
}
