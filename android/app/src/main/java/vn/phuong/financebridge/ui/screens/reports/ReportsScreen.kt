package vn.phuong.financebridge.ui.screens.reports

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.phuong.financebridge.ui.components.*
import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.model.UiPeriodMetric
import vn.phuong.financebridge.ui.screens.dashboard.DashboardRepository
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.FinanceFormatters
import vn.phuong.financebridge.ui.util.FinancePeriod

@Composable
fun ReportsScreen(context: Context, modifier: Modifier = Modifier) {
    val repository = remember(context) { DashboardRepository(context) }
    var period by remember { mutableStateOf(FinancePeriod.MONTH) }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var query by remember { mutableStateOf(FinancePeriod.MONTH.query()) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<UiDashboard?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var revision by remember { mutableStateOf(0) }
    var customApplied by remember { mutableStateOf(false) }

    LaunchedEffect(query, revision, period, customApplied) {
        loading = true
        data = null
        error = null
        if (period == FinancePeriod.CUSTOM && !customApplied) { loading = false; return@LaunchedEffect }
        repository.fetchDashboard(query).onSuccess { data = it }.onFailure { error = it.message }
        loading = false
    }

    Column(modifier.fillMaxSize().background(FinanceTheme.colors.background)
        .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Báo cáo tài chính", style = FinanceTheme.typography.headlineLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FinancePeriod.values().forEach { value ->
                FinanceChip(text = value.label, isSelected = period == value, onClick = {
                    period = value
                    inputError = null
                    customApplied = false
                    if (value != FinancePeriod.CUSTOM) { query = value.query(); revision++ }
                })
            }
        }
        if (period == FinancePeriod.CUSTOM) {
            OutlinedTextField(from, { from = it; customApplied = false }, label = { Text("Từ ngày (dd/MM/yyyy)") }, singleLine = true)
            OutlinedTextField(to, { to = it; customApplied = false }, label = { Text("Đến ngày (dd/MM/yyyy)") }, singleLine = true)
            SecondaryButton(text = "Áp dụng", onClick = {
                runCatching { period.query(from, to) }.onSuccess { query = it; inputError = null; customApplied = true; revision++ }
                    .onFailure { inputError = "Ngày chưa hợp lệ hoặc ngày kết thúc trước ngày bắt đầu." }
            })
        }
        inputError?.let { Text(it, color = FinanceTheme.colors.expense) }
        when {
            period == FinancePeriod.CUSTOM && data == null && !loading && error == null -> Text("Chọn khoảng ngày và bấm Áp dụng.")
            loading -> LoadingState()
            error != null -> ErrorState(message = error!!, onRetry = { revision++ })
            else -> data?.let { dashboard ->
                Text(dashboard.periodLabel, style = FinanceTheme.typography.bodyMedium)
                if (dashboard.possibleDuplicateCount > 0) Text("Có giao dịch có thể trùng giữa các nguồn; tổng số cần đối soát.", color = FinanceTheme.colors.expense)
                FinanceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tổng thu: ${dashboard.formattedIncome}", color = FinanceTheme.colors.income)
                        Text("Tổng chi: ${dashboard.formattedExpense}", color = FinanceTheme.colors.expense)
                        Text("Dòng tiền ròng: ${dashboard.formattedNetDiff}")
                    }
                }
                if (dashboard.totalTransactionsCount == 0) EmptyState(title = "Chưa có giao dịch trong kỳ này.")
                if (dashboard.hasReportBreakdowns) SpendingChart(daily = dashboard.daily)
                else Text("Máy chủ chưa cung cấp báo cáo theo ngày, ngân hàng và nguồn. Cần cập nhật kết nối.", color = FinanceTheme.colors.textMuted)
                FinanceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(title = "Chi theo danh mục")
                        if (dashboard.categories.isEmpty()) Text("Chưa có chi tiêu.")
                        dashboard.categories.forEach { CategoryProgress(category = it) }
                    }
                }
                MetricList("Thu theo danh mục", dashboard.incomeCategories)
                if (dashboard.hasReportBreakdowns) {
                    MetricList("Theo ngân hàng", dashboard.banks)
                    MetricList("Thu theo nguồn", dashboard.sources)
                }
            }
        }
        Spacer(Modifier.height(56.dp))
    }
}

@Composable
private fun MetricList(title: String, rows: List<UiPeriodMetric>) {
    FinanceCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(title = title)
            if (rows.isEmpty()) Text("Chưa có dữ liệu trong kỳ này.")
            rows.forEach { row ->
                val label = when (row.name) {
                    "BANK_NOTIFICATION" -> "Thông báo ngân hàng"
                    "MONEYLOVER" -> "Nhập lịch sử"
                    "MANUAL" -> "Ghi chép thủ công"
                    else -> row.name
                }
                Text(label, style = FinanceTheme.typography.titleMedium)
                Text("Thu ${FinanceFormatters.formatVnd(row.income)} · Chi ${FinanceFormatters.formatVnd(row.expense)}",
                    style = FinanceTheme.typography.bodyMedium)
            }
        }
    }
}
