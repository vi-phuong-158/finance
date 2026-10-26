package vn.phuong.financebridge.ui.screens.dashboard

import android.content.Context
import org.json.JSONArray
import vn.phuong.financebridge.ui.data.FinanceApi
import vn.phuong.financebridge.ui.data.parseTransaction
import vn.phuong.financebridge.ui.model.UiCategoryMetric
import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.model.UiPeriodMetric
import vn.phuong.financebridge.ui.util.FinanceFormatters

class DashboardRepository(context: Context) {
    private val api = FinanceApi(context)

    suspend fun fetchDashboard(query: Map<String, String> = emptyMap()): Result<UiDashboard> = api.read("dashboard", query) { json ->
        val income = json.getLong("income")
        val expense = json.getLong("expense")
        val net = json.getLong("net")
        require(income >= 0 && expense >= 0 && net == income - expense)
        fun metrics(array: JSONArray): List<UiPeriodMetric> = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            UiPeriodMetric(if (item.isNull("name")) "Ngoài ngân hàng" else item.getString("name"),
                item.getLong("income"), item.getLong("expense"), item.getLong("net"), item.getInt("transactionCount"))
        }
        val categoryMetrics = metrics(json.getJSONArray("categorySummary"))
        val categories = categoryMetrics.filter { it.expense > 0 }.sortedByDescending { it.expense }.map { item ->
            UiCategoryMetric(item.name, item.expense, FinanceFormatters.formatVnd(item.expense),
                if (expense > 0) (item.expense.toDouble() / expense).toFloat().coerceIn(0f, 1f) else 0f, item.count)
        }
        val transactions = json.getJSONArray("recentTransactions")
        val period = json.getJSONObject("period")
        UiDashboard(monthlyIncome = income, formattedIncome = FinanceFormatters.formatVnd(income),
            monthlyExpense = expense, formattedExpense = FinanceFormatters.formatVnd(expense),
            netDiff = net, formattedNetDiff = FinanceFormatters.formatVnd(net, includeSign = true),
            periodLabel = "${FinanceFormatters.formatIsoDate(period.getString("from"))} – ${FinanceFormatters.formatIsoDate(period.getString("to"))}",
            categories = categories, recentTransactions = (0 until transactions.length()).map { parseTransaction(transactions.getJSONObject(it)) },
            totalTransactionsCount = json.getInt("transactionCount"), lastUpdatedIso = json.getString("lastUpdatedAt"),
            daily = if (json.has("dailySummary")) metrics(json.getJSONArray("dailySummary")) else emptyList(),
            banks = if (json.has("bankSummary")) metrics(json.getJSONArray("bankSummary")) else emptyList(),
            sources = if (json.has("sourceSummary")) metrics(json.getJSONArray("sourceSummary")) else emptyList(),
            incomeCategories = categoryMetrics.filter { it.income > 0 }.sortedByDescending { it.income },
            possibleDuplicateCount = json.getInt("possibleDuplicateCount"),
            hasReportBreakdowns = json.has("dailySummary") && json.has("bankSummary") && json.has("sourceSummary"))
    }
}
