package vn.phuong.financebridge.ui.model

data class UiCategoryMetric(val name: String, val amount: Long, val formattedAmount: String,
    val percentage: Float, val transactionCount: Int)

data class UiPeriodMetric(val name: String, val income: Long, val expense: Long, val net: Long, val count: Int)

data class UiDashboard(
    val monthlyIncome: Long,
    val formattedIncome: String,
    val monthlyExpense: Long,
    val formattedExpense: String,
    val netDiff: Long,
    val formattedNetDiff: String,
    val periodLabel: String,
    val categories: List<UiCategoryMetric>,
    val recentTransactions: List<UiTransaction>,
    val totalTransactionsCount: Int,
    val lastUpdatedIso: String,
    val daily: List<UiPeriodMetric>,
    val banks: List<UiPeriodMetric>,
    val sources: List<UiPeriodMetric>,
    val incomeCategories: List<UiPeriodMetric>,
    val possibleDuplicateCount: Int,
    val hasReportBreakdowns: Boolean,
)
