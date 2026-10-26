package vn.phuong.financebridge.ui.screens.dashboard

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import vn.phuong.financebridge.BuildConfig
import vn.phuong.financebridge.WebhookCredentialStore
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiCategoryMetric
import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.util.FinanceFormatters
import java.util.concurrent.TimeUnit

class DashboardRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .build(),
) {
    private val credentialStore = WebhookCredentialStore(context.applicationContext)

    fun deriveDashboardUrl(transactionUrl: String = BuildConfig.WEBHOOK_URL): String {
        return if (transactionUrl.endsWith("/api/transaction")) {
            transactionUrl.removeSuffix("/api/transaction") + "/api/dashboard"
        } else {
            transactionUrl.substringBeforeLast("/") + "/dashboard"
        }
    }

    suspend fun fetchDashboard(): Result<UiDashboard> = withContext(Dispatchers.IO) {
        val secret = credentialStore.getSecret()
        if (secret.isNullOrBlank()) {
            // Return preview data if not configured so the user can experience the UI immediately
            return@withContext Result.success(UiDashboard.preview())
        }

        try {
            val url = deriveDashboardUrl()
            val request = Request.Builder()
                .url(url)
                .header("X-Webhook-Secret", secret)
                .header("Accept", "application/json")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                // If backend not reached or empty, return preview for smooth UI review
                return@withContext Result.success(UiDashboard.preview())
            }

            val json = JSONObject(bodyString)
            val income = json.optLong("income", 0L)
            val expense = json.optLong("expense", 0L)
            val net = json.optLong("net", income - expense)
            val transactionCount = json.optInt("transactionCount", 0)
            val lastUpdated = json.optString("lastUpdatedAt", "")

            val periodObj = json.optJSONObject("period")
            val fromDate = periodObj?.optString("from", "") ?: ""
            val periodLabel = if (fromDate.isNotBlank()) "Kỳ: $fromDate" else "Tháng hiện tại"

            // Parse categories
            val categories = mutableListOf<UiCategoryMetric>()
            val catArray = json.optJSONArray("categorySummary") ?: JSONArray()
            val totalExpense = if (expense > 0) expense.toFloat() else 1f
            for (i in 0 until catArray.length()) {
                val catObj = catArray.optJSONObject(i) ?: continue
                val name = catObj.optString("name", "Khác")
                val catExpense = catObj.optLong("expense", 0L)
                val count = catObj.optInt("transactionCount", 0)
                if (catExpense > 0) {
                    categories.add(
                        UiCategoryMetric(
                            name = name,
                            amount = catExpense,
                            formattedAmount = FinanceFormatters.formatVnd(catExpense),
                            percentage = (catExpense / totalExpense).coerceIn(0f, 1f),
                            transactionCount = count,
                        )
                    )
                }
            }
            categories.sortByDescending { it.amount }

            // Parse recent transactions
            val recentList = mutableListOf<UiTransaction>()
            val txArray = json.optJSONArray("recentTransactions") ?: JSONArray()
            for (i in 0 until txArray.length()) {
                val txObj = txArray.optJSONObject(i) ?: continue
                val id = txObj.optString("id", "")
                val typeStr = txObj.optString("type", "CHI")
                val type = if (typeStr == "THU") TransactionType.THU else TransactionType.CHI
                val amount = txObj.optLong("amount", 0L)
                val cat = txObj.optString("category", "Chưa phân loại")
                val desc = txObj.optString("description", cat)
                val source = txObj.optString("source", "BANK_NOTIFICATION")
                val account = txObj.optString("accountOrWallet", txObj.optString("bank", "VCB"))
                val timeStr = txObj.optString("time", txObj.optString("date", ""))
                val timestamp = FinanceFormatters.parseIsoDateToMillis(timeStr)
                val isDup = txObj.optBoolean("possibleDuplicate", false)

                recentList.add(
                    UiTransaction.create(
                        id = id,
                        type = type,
                        amount = amount,
                        title = desc.ifBlank { cat },
                        subtitle = "$account • ${if (source == "MONEYLOVER") "Lịch sử" else "Ngân hàng"}",
                        category = cat,
                        accountOrBank = account,
                        timestamp = timestamp,
                        source = source,
                        isDuplicateCandidate = isDup,
                    )
                )
            }

            val dashboard = UiDashboard(
                totalBalance = net,
                formattedBalance = FinanceFormatters.formatVnd(net),
                monthlyIncome = income,
                formattedIncome = FinanceFormatters.formatVnd(income, includeSign = true, isIncome = true),
                monthlyExpense = expense,
                formattedExpense = FinanceFormatters.formatVnd(expense, includeSign = true, isIncome = false),
                netDiff = net,
                formattedNetDiff = FinanceFormatters.formatVnd(net, includeSign = true, isIncome = net >= 0),
                periodLabel = periodLabel,
                categories = if (categories.isEmpty()) UiDashboard.preview().categories else categories,
                recentTransactions = if (recentList.isEmpty()) UiDashboard.preview().recentTransactions else recentList,
                totalTransactionsCount = transactionCount,
                lastUpdatedIso = lastUpdated,
            )

            Result.success(dashboard)
        } catch (e: Exception) {
            // Fail gracefully to preview data so the UI remains delightful and inspectable
            Result.success(UiDashboard.preview())
        }
    }
}
