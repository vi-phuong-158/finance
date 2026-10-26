package vn.phuong.financebridge.ui.screens.interest

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import vn.phuong.financebridge.BuildConfig
import vn.phuong.financebridge.WebhookConfiguration
import vn.phuong.financebridge.WebhookCredentialStore
import java.util.concurrent.TimeUnit

data class InterestEntry(val date: String, val label: String, val amount: Long)
data class InterestLoan(
    val id: String, val name: String, val principal: Long, val due: Long,
    val currentInterest: Long, val paid: Long, val status: String,
    val disbursements: List<InterestEntry>, val payments: List<InterestEntry>,
)
data class InterestDashboard(
    val asOf: String, val from: String, val to: String, val principal: Long,
    val accrued: Long, val paid: Long, val due: Long, val currentInterest: Long,
    val currentRemaining: Long, val collected: Long, val advance: Long, val loans: List<InterestLoan>,
)

class InterestRepository(context: Context) {
    private val credentials = WebhookCredentialStore(context.applicationContext)
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()

    suspend fun fetch(): Result<InterestDashboard> = withContext(Dispatchers.IO) {
        val secret = credentials.getSecret().orEmpty()
        if (!WebhookConfiguration.isValid(BuildConfig.WEBHOOK_URL, secret)) {
            return@withContext Result.failure(IllegalStateException("Hãy cấu hình mã kết nối trong Khác → Kết nối & hệ thống."))
        }
        try {
            val request = Request.Builder()
                .url(BuildConfig.WEBHOOK_URL.removeSuffix("/api/transaction") + "/api/interest")
                .header("X-Webhook-Secret", secret).header("Accept", "application/json").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val message = when (response.code) {
                        401 -> "Mã kết nối chưa đúng. Kiểm tra lại trong Cài đặt."
                        404 -> "Máy chủ chưa có chức năng theo dõi tiền lãi."
                        else -> "Chưa tải được sổ lãi. Kiểm tra kết nối và cấu hình nguồn dữ liệu."
                    }
                    return@withContext Result.failure(IllegalStateException(message))
                }
                Result.success(parse(JSONObject(response.body?.string() ?: error("EMPTY_RESPONSE"))))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Không tải được sổ lãi. Vui lòng thử lại."))
        }
    }

    private fun entries(array: JSONArray, disbursement: Boolean): List<InterestEntry> =
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            InterestEntry(item.getString("date"),
                if (disbursement) "Giải ngân · ${item.getDouble("annualRate") * 100}%/năm" else item.getString("type"),
                item.getLong(if (disbursement) "principal" else "amount"))
        }

    private fun parse(json: JSONObject): InterestDashboard {
        require(json.getString("rule") == "MONTHLY_25_V1")
        val totals = json.getJSONObject("totals")
        val period = json.getJSONObject("period")
        val rows = json.getJSONArray("loans")
        val loans = (0 until rows.length()).map { index ->
            val item = rows.getJSONObject(index)
            InterestLoan(item.getString("id"), item.getString("name"), item.getLong("outstandingPrincipal"),
                item.getLong("interestDue"), item.getLong("currentInterest"), item.getLong("paidInterest"),
                item.getString("status"), entries(item.getJSONArray("disbursements"), true), entries(item.getJSONArray("payments"), false))
        }
        return InterestDashboard(json.getString("asOf"), period.getString("from"), period.getString("to"),
            totals.getLong("outstandingPrincipal"), totals.getLong("accruedInterest"), totals.getLong("paidInterest"),
            totals.getLong("interestDue"), totals.getLong("currentInterest"), totals.getLong("currentRemaining"),
            totals.getLong("collectedThisPeriod"), totals.getLong("advanceInterest"), loans)
    }
}
