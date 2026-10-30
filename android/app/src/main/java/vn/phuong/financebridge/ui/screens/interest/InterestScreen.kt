package vn.phuong.financebridge.ui.screens.interest

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.phuong.financebridge.ui.components.AmountText
import vn.phuong.financebridge.ui.components.EmptyState
import vn.phuong.financebridge.ui.components.ErrorState
import vn.phuong.financebridge.ui.components.FinanceCard
import vn.phuong.financebridge.ui.components.LoadingState
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.FinanceFormatters
import vn.phuong.financebridge.LoanOperation

@Composable
fun InterestScreen(context: Context, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val repository = remember(context) { InterestRepository(context) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<InterestDashboard?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var form by remember { mutableStateOf<Pair<LoanOperation, InterestLoan?>?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    fun load() {
        loading = true
        error = null
        data = null
        scope.launch {
            repository.fetch().onSuccess { data = it }.onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("Quay lại") }
            TextButton(onClick = { load() }, enabled = !loading) { Text("Làm mới") }
        }
        savedMessage?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp), style = FinanceTheme.typography.bodyMedium) }
        when {
            loading -> LoadingState()
            error != null -> ErrorState(message = error!!, onRetry = { load() })
            data != null -> {
                val current = data!!
                LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Text("Tiền lãi & Công nợ", style = FinanceTheme.typography.headlineLarge)
                        Text("Ngày xem ${displayDate(current.asOf)}", style = FinanceTheme.typography.caption)
                        if (current.writesEnabled) Button(onClick = { form = LoanOperation.CREATE_LOAN to null }) { Text("+ Khoản vay mới") }
                        else Text("Chưa mở chức năng ghi công nợ trên máy chủ. Dữ liệu hiện ở chế độ chỉ đọc.", style = FinanceTheme.typography.caption)
                    }
                    item {
                        FinanceCard(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Tổng gốc còn lại", style = FinanceTheme.typography.bodyMedium)
                                AmountText(FinanceFormatters.formatVnd(current.principal), style = FinanceTheme.typography.headlineLarge)
                                Text("Kỳ ${displayDate(current.from)} → ${displayDate(current.to)}", style = FinanceTheme.typography.bodyMedium)
                                InterestMetric("Lãi kỳ hiện tại · chưa chốt", current.currentInterest)
                                InterestMetric("Dự kiến còn thu khi chốt kỳ", current.currentRemaining)
                            }
                        }
                    }
                    item {
                        FinanceCard(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                InterestMetric("Lãi đã chốt lũy kế", current.accrued)
                                InterestMetric("Lãi đã thu lũy kế", current.paid)
                                InterestMetric("Lãi đến hạn còn thu", current.due)
                                InterestMetric("Đã nhận trong kỳ này", current.collected)
                                if (current.advance > 0) InterestMetric("Lãi thu trước", current.advance)
                            }
                        }
                    }
                    item {
                        Text("Lãi theo dư nợ cuối ngày × số ngày thực tế × lãi suất năm ÷ 365. Chốt kỳ ngày 25; thu gốc giảm lãi từ ngày nhận. Dự kiến giả định không phát sinh thêm đến cuối kỳ.",
                            style = FinanceTheme.typography.bodyMedium, color = FinanceTheme.colors.textSecondary)
                        Text("Khoản vay · ${current.loans.size}", style = FinanceTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 16.dp))
                    }
                    if (current.loans.isEmpty()) item { EmptyState("Chưa có khoản vay", "Thêm khoản vay và đợt giải ngân trong sổ nguồn.") }
                    items(current.loans, key = { it.id }) { loan ->
                        FinanceCard(Modifier.fillMaxWidth().clickable { expanded = if (expanded == loan.id) null else loan.id }) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(loan.name, style = FinanceTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text(loan.status, style = FinanceTheme.typography.caption)
                                InterestMetric("Gốc còn lại", loan.principal)
                                InterestMetric("Lãi đến hạn còn thu", loan.due)
                                InterestMetric("Lãi kỳ hiện tại", loan.currentInterest)
                                Text(if (expanded == loan.id) "Thu gọn" else "Xem giải ngân và lịch sử thu tiền",
                                    color = FinanceTheme.colors.primary, style = FinanceTheme.typography.bodyMedium)
                                if (expanded == loan.id) {
                                    HorizontalDivider()
                                    InterestMetric("Tổng đã giải ngân", loan.disbursed)
                                    InterestMetric("Lãi đã chốt lũy kế", loan.accrued)
                                    InterestMetric("Lãi đã thu", loan.paid)
                                    loan.maturity?.let { Text("Đáo hạn: ${displayDate(it)}") }
                                    if (loan.note.isNotBlank()) Text(loan.note, style = FinanceTheme.typography.bodyMedium)
                                    if (current.writesEnabled) {
                                        Column {
                                            TextButton(onClick = { form = LoanOperation.DISBURSEMENT to loan }) { Text("+ Giải ngân thêm") }
                                            Row {
                                                TextButton(onClick = { form = LoanOperation.PRINCIPAL_PAYMENT to loan }) { Text("Thu gốc") }
                                                TextButton(onClick = { form = LoanOperation.INTEREST_PAYMENT to loan }) { Text("Thu lãi") }
                                            }
                                            TextButton(onClick = { form = LoanOperation.UPDATE_INFO to loan }) { Text("Sửa thông tin") }
                                        }
                                    }
                                    Text("Dòng thời gian", style = FinanceTheme.typography.titleMedium)
                                    (loan.disbursements + loan.payments).sortedByDescending { it.date }
                                        .forEach { entry -> InterestEntryRow(entry) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    form?.let { (operation, loan) ->
        LoanForm(context, operation, loan, data?.asOf ?: return@let, onDismiss = { form = null }, onQueued = {
            form = null; savedMessage = "Đã lưu yêu cầu trên máy. Bấm Làm mới sau khi đồng bộ để xem kết quả."; load()
        })
    }
}

private fun displayDate(value: String): String = FinanceFormatters.formatIsoDate(value)

@Composable
private fun InterestEntryRow(entry: InterestEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${displayDate(entry.date)} · ${entry.label}", style = FinanceTheme.typography.bodyMedium)
        entry.maturity?.let { Text("Đáo hạn ${displayDate(it)}", style = FinanceTheme.typography.caption) }
        AmountText(FinanceFormatters.formatVnd(entry.amount))
    }
}

@Composable
private fun InterestMetric(label: String, amount: Long) {
    // Vertical metrics keep full amounts readable on narrow phones and larger font scales.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = FinanceTheme.typography.bodyMedium, color = FinanceTheme.colors.textSecondary)
        AmountText(FinanceFormatters.formatVnd(amount), style = FinanceTheme.typography.titleMedium)
    }
}
