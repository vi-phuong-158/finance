package vn.phuong.financebridge.ui.model

import vn.phuong.financebridge.ui.util.FinanceFormatters

data class UiCategoryMetric(
    val name: String,
    val amount: Long,
    val formattedAmount: String,
    val percentage: Float, // 0.0f .. 1.0f
    val transactionCount: Int,
)

data class UiDashboard(
    val totalBalance: Long,
    val formattedBalance: String,
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
) {
    companion object {
        fun preview(): UiDashboard {
            val income = 779604930L
            val expense = 569649820L
            val net = income - expense
            val categories = listOf(
                UiCategoryMetric("Con cái", 111332000L, FinanceFormatters.formatVnd(111332000L), 0.20f, 42),
                UiCategoryMetric("Mua sắm", 98535100L, FinanceFormatters.formatVnd(98535100L), 0.17f, 65),
                UiCategoryMetric("Quà tặng & Quyên góp", 60404000L, FinanceFormatters.formatVnd(60404000L), 0.11f, 18),
                UiCategoryMetric("Ăn uống", 46878500L, FinanceFormatters.formatVnd(46878500L), 0.08f, 120),
                UiCategoryMetric("Gia đình", 39000000L, FinanceFormatters.formatVnd(39000000L), 0.07f, 14),
                UiCategoryMetric("Đồ dùng cá nhân", 33558000L, FinanceFormatters.formatVnd(33558000L), 0.06f, 31),
                UiCategoryMetric("Xăng", 29411000L, FinanceFormatters.formatVnd(29411000L), 0.05f, 52),
                UiCategoryMetric("Hoá đơn điện", 24909000L, FinanceFormatters.formatVnd(24909000L), 0.04f, 12),
            )
            val transactions = listOf(
                UiTransaction.create(
                    id = "bank:vcb-001",
                    type = TransactionType.CHI,
                    amount = 45000L,
                    title = "Cà phê sáng Highlands",
                    subtitle = "VCB • Chuyển khoản QR",
                    category = "Ăn uống",
                    accountOrBank = "VCB",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 35, // 35 mins ago
                ),
                UiTransaction.create(
                    id = "bank:vcb-002",
                    type = TransactionType.THU,
                    amount = 25000000L,
                    title = "Nhận lương tháng 10",
                    subtitle = "VCB • Chuyển tiền đến",
                    category = "Lương",
                    accountOrBank = "VCB",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 4, // 4 hours ago
                ),
                UiTransaction.create(
                    id = "bank:vcb-003",
                    type = TransactionType.CHI,
                    amount = 320000L,
                    title = "Đổ xăng Petrolimex",
                    subtitle = "VCB • POS",
                    category = "Xăng",
                    accountOrBank = "VCB",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 26, // Yesterday
                ),
                UiTransaction.create(
                    id = "bank:vcb-004",
                    type = TransactionType.CHI,
                    amount = 1450000L,
                    title = "Thanh toán tiền điện EVN",
                    subtitle = "VCB • Hóa đơn tự động",
                    category = "Hoá đơn điện",
                    accountOrBank = "VCB",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 50, // 2 days ago
                ),
                UiTransaction.create(
                    id = "bank:vcb-005",
                    type = TransactionType.CHI,
                    amount = 890000L,
                    title = "Mua sách & đồ dùng học tập",
                    subtitle = "VCB • Chuyển khoản",
                    category = "Con cái",
                    accountOrBank = "VCB",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 75,
                ),
            )
            return UiDashboard(
                totalBalance = net,
                formattedBalance = FinanceFormatters.formatVnd(net),
                monthlyIncome = income,
                formattedIncome = FinanceFormatters.formatVnd(income, includeSign = true, isIncome = true),
                monthlyExpense = expense,
                formattedExpense = FinanceFormatters.formatVnd(expense, includeSign = true, isIncome = false),
                netDiff = net,
                formattedNetDiff = FinanceFormatters.formatVnd(net, includeSign = true, isIncome = net >= 0),
                periodLabel = "Tháng 10/2026",
                categories = categories,
                recentTransactions = transactions,
                totalTransactionsCount = 1581,
                lastUpdatedIso = "2026-10-25T23:00:00+07:00",
            )
        }
    }
}
