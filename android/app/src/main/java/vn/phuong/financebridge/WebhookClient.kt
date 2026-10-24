package vn.phuong.financebridge

import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class WebhookResponse(
    val httpCode: Int,
    val status: String?,
    val errorCode: String?,
)

object WebhookClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun post(payload: JSONObject): WebhookResponse {
        val endpoint = BuildConfig.WEBHOOK_URL
        val secret = BuildConfig.WEBHOOK_SECRET
        val uri = Uri.parse(endpoint)

        if (uri.scheme != "https" || uri.host.isNullOrBlank() || secret.isBlank()) {
            return WebhookResponse(0, null, "CLIENT_CONFIGURATION_ERROR")
        }

        val request = Request.Builder()
            .url(endpoint)
            .header("X-Webhook-Secret", secret)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
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
            || (response.httpCode in 500..599 && response.errorCode != "SERVER_CONFIGURATION_ERROR" && response.errorCode != "SHEET_SCHEMA_MISMATCH")

    fun message(response: WebhookResponse): String = when (response.errorCode ?: response.status) {
        "CLIENT_CONFIGURATION_ERROR", "SERVER_CONFIGURATION_ERROR" -> "Kiểm tra cấu hình webhook và secret"
        "UNAUTHORIZED" -> "Secret chưa khớp; sửa cấu hình rồi gửi lại"
        "SHEET_SCHEMA_MISMATCH" -> "Cấu trúc hai tab Google Sheets chưa khớp"
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

    companion object {
        private val ERROR_CODE = Regex("[A-Z0-9_]{1,48}")
        private val TERMINAL_STATUSES = setOf(
            "SAVED_TRANSACTION",
            "SAVED_RAW_CAPTURE",
            "DUPLICATE_IGNORED",
            "DROPPED_SENSITIVE",
            "DROPPED_NON_TRANSACTION",
        )
        private val DRY_RUN_STATUSES = setOf("VALIDATED_TRANSACTION", "VALIDATED_RAW_CAPTURE")
    }
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

