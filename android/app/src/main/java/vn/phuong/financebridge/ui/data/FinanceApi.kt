package vn.phuong.financebridge.ui.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import vn.phuong.financebridge.BuildConfig
import vn.phuong.financebridge.CategoryChoice
import vn.phuong.financebridge.CategoryMemory
import vn.phuong.financebridge.WebhookConfiguration
import vn.phuong.financebridge.WebhookCredentialStore
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.util.FinanceFormatters
import java.util.concurrent.TimeUnit

class FinanceApi(context: Context) {
    private val credentials = WebhookCredentialStore(context.applicationContext)
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()

    suspend fun <T> read(endpoint: String, query: Map<String, String> = emptyMap(), decode: (JSONObject) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                require(endpoint in setOf("dashboard", "transactions", "category-memory"))
                val secret = credentials.getSecret().orEmpty()
                if (!WebhookConfiguration.isValid(BuildConfig.WEBHOOK_URL, secret)) {
                    error("Hãy nhập mã kết nối trong Khác → Kết nối & hệ thống.")
                }
                val url = (BuildConfig.WEBHOOK_URL.removeSuffix("/api/transaction") + "/api/$endpoint")
                    .toHttpUrl().newBuilder().apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
                val request = Request.Builder().url(url).header("X-Webhook-Secret", secret)
                    .header("Accept", "application/json").get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error(when (response.code) {
                        401 -> "Mã kết nối chưa đúng. Kiểm tra lại trong Khác."
                        400 -> "Bộ lọc chưa hợp lệ. Kiểm tra ngày và thử lại."
                        409 -> "Dữ liệu đã thay đổi. Hãy tải lại danh sách."
                        else -> "Chưa tải được dữ liệu. Vui lòng thử lại."
                    })
                    Result.success(decode(JSONObject(response.body?.string() ?: error("EMPTY_RESPONSE"))))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                Result.failure(IllegalStateException(e.message?.takeIf { it.any { c -> c.code > 127 } }
                    ?: "Dữ liệu chưa sẵn sàng. Vui lòng thử lại."))
            } catch (_: Exception) {
                // Never surface server bodies, credentials, or a financial sample as a successful read.
                Result.failure(IllegalStateException("Không tải được dữ liệu. Kiểm tra kết nối và thử lại."))
            }
        }
}

data class TransactionLookup(val transaction: UiTransaction?, val categories: List<CategoryChoice>, val editingEnabled: Boolean)

// The latest server state of one transaction, so an edit is always based on its current revision.
suspend fun FinanceApi.latestTransaction(id: String): Result<TransactionLookup> = read("transactions", mapOf("id" to id, "limit" to "1")) { json ->
    val values = json.getJSONArray("transactions")
    val choices = json.optJSONArray("categories")
    val found = (0 until values.length()).map { parseTransaction(values.getJSONObject(it)) }.firstOrNull { it.id == id }
    TransactionLookup(found, if (choices == null) emptyList() else (0 until choices.length()).map {
        val choice = choices.getJSONObject(it); CategoryChoice(choice.getString("name"), choice.getString("type"))
    }, json.optBoolean("editingEnabled", false))
}

fun parseTransaction(item: JSONObject): UiTransaction {
    val type = when (item.getString("type")) {
        "THU" -> TransactionType.THU
        "CHI" -> TransactionType.CHI
        else -> error("INVALID_TRANSACTION")
    }
    val amount = item.getLong("amount")
    require(amount > 0)
    val source = item.getString("source")
    require(source in setOf("MONEYLOVER", "BANK_NOTIFICATION", "MANUAL"))
    val category = item.getString("category")
    val account = if (item.isNull("accountOrWallet")) "Chưa rõ tài khoản" else item.getString("accountOrWallet")
    val date = if (item.isNull("time")) item.getString("date") else item.getString("time")
    return UiTransaction.create(id = item.getString("id"), type = type, amount = amount,
        title = if (item.isNull("description")) category else item.getString("description").ifBlank { category },
        subtitle = "$account · ${when (source) { "MONEYLOVER" -> "Nhập lịch sử"; "MANUAL" -> "Ghi chép thủ công"; else -> "Thông báo ngân hàng" }}",
        category = category, accountOrBank = account, timestamp = FinanceFormatters.parseIsoDateToMillis(date),
        source = source, isDuplicateCandidate = item.getBoolean("possibleDuplicate"), revision = item.optString("revision", "0"),
        note = if (item.isNull("description")) "" else item.getString("description"), excluded = item.getBoolean("excludedFromReport"),
        memoryKey = if (item.has("memoryKey") && !item.isNull("memoryKey")) item.getString("memoryKey").takeIf(CategoryMemory::validKey) else null)
}
