package vn.phuong.financebridge

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class WebhookResponse(
    val httpCode: Int,
    val status: String?,
    val errorCode: String?,
)

object WebhookClient {
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun post(
        payload: JSONObject,
        endpoint: String = BuildConfig.WEBHOOK_URL,
        secret: String? = null,
    ): WebhookResponse = postJson(payload.toString(), endpoint, secret)

    fun postJson(
        payloadJson: String,
        endpoint: String = BuildConfig.WEBHOOK_URL,
        secret: String? = null,
    ): WebhookResponse {
        if (secret.isNullOrBlank() || !WebhookConfiguration.isValid(endpoint, secret)) {
            return WebhookResponse(0, null, "CLIENT_CONFIGURATION_ERROR")
        }

        val request = Request.Builder()
            .url(endpoint)
            .header("X-Webhook-Secret", secret)
            .post(payloadJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(responseBody) }.getOrNull()
            val status = json?.optString("status")?.takeIf { it.isNotBlank() && it != "null" }
            val responseError = json?.optString("error")?.takeIf { it.matches(ERROR_CODE) }
            return WebhookResponse(response.code, status, responseError)
        }
    }

    fun isRetryable(response: WebhookResponse): Boolean =
        response.httpCode == 408
            || response.httpCode == 425
            || response.httpCode == 429
            || (response.httpCode in 500..599 && response.errorCode !in CONFIGURATION_ERRORS)

    fun message(response: WebhookResponse): String = when (response.errorCode ?: response.status) {
        "CLIENT_CONFIGURATION_ERROR", "SERVER_CONFIGURATION_ERROR" -> "Kiểm tra cấu hình webhook và secret"
        "UNAUTHORIZED" -> "Secret chưa khớp; sửa cấu hình rồi gửi lại"
        "SHEET_SCHEMA_MISMATCH" -> "Cấu trúc hai tab Google Sheets chưa khớp"
        "SHEETS_PERMISSION_DENIED", "SHEET_NOT_FOUND" -> "Kiểm tra ID bảng tính và quyền Editor của service account"
        "UPSTREAM_UNAVAILABLE" -> "Máy chủ hoặc Google Sheets đang tạm thời lỗi"
        "SAVED_TRANSACTION" -> "Đã lưu giao dịch"
        "SAVED_RAW_CAPTURE" -> "Đã lưu mẫu chờ xử lý"
        "DUPLICATE_IGNORED" -> "Giao dịch đã có, không ghi thêm"
        "DROPPED_SENSITIVE", "DROPPED_NON_TRANSACTION" -> "Máy chủ đã loại thông báo này"
        "VALIDATED_TRANSACTION" -> "Kết nối và kiểm tra giao dịch thành công; chưa ghi dữ liệu"
        "VALIDATED_RAW_CAPTURE" -> "Kết nối thành công; mẫu chưa được lưu"
        else -> "Lỗi HTTP ${response.httpCode}: ${response.errorCode ?: "phản hồi không hợp lệ"}"
    }

    fun isTerminalSuccess(response: WebhookResponse): Boolean =
        response.httpCode in 200..299 && response.status in TERMINAL_STATUSES

    fun dryRunSucceeded(response: WebhookResponse): Boolean =
        response.httpCode in 200..299 && response.status in DRY_RUN_STATUSES

    fun code(response: WebhookResponse): String = when {
        response.errorCode != null -> response.errorCode
        response.httpCode > 0 -> "HTTP_${response.httpCode}"
        else -> "NETWORK_UNAVAILABLE"
    }

    private val ERROR_CODE = Regex("[A-Z0-9_]{1,48}")
    private val CONFIGURATION_ERRORS = setOf("SERVER_CONFIGURATION_ERROR", "SHEET_SCHEMA_MISMATCH", "SHEETS_PERMISSION_DENIED", "SHEET_NOT_FOUND")
    private val TERMINAL_STATUSES = setOf(
        "SAVED_TRANSACTION",
        "SAVED_RAW_CAPTURE",
        "DUPLICATE_IGNORED",
        "DROPPED_SENSITIVE",
        "DROPPED_NON_TRANSACTION",
    )
    private val DRY_RUN_STATUSES = setOf("VALIDATED_TRANSACTION", "VALIDATED_RAW_CAPTURE")
}

fun buildTransactionPayload(event: PendingTransaction, dryRun: Boolean = false): JSONObject = JSONObject().apply {
    put("eventId", event.eventId)
    put("bank", event.bank)
    put("packageName", event.packageName)
    put("title", event.title)
    put("text", event.text)
    put("postedAt", event.postedAt)
    put("dryRun", dryRun)
}

