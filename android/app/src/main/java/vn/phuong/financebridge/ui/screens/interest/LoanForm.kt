package vn.phuong.financebridge.ui.screens.interest

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import vn.phuong.financebridge.LoanFormInput
import vn.phuong.financebridge.LoanOperation
import vn.phuong.financebridge.ui.data.queueMutation
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.FinanceFormatters
import java.util.UUID

@Composable
fun LoanForm(context: Context, operation: LoanOperation, loan: InterestLoan?, asOf: String, onDismiss: () -> Unit, onQueued: () -> Unit) {
    val scope = rememberCoroutineScope()
    val id = rememberSaveable { UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(loan?.name.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(FinanceFormatters.formatIsoDate(asOf)) }
    var maturity by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf(if (operation == LoanOperation.UPDATE_INFO) loan?.note.orEmpty() else "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val creating = operation == LoanOperation.CREATE_LOAN
    val disbursing = creating || operation == LoanOperation.DISBURSEMENT
    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(shape = FinanceTheme.shapes.xLarge) {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(operation.label, style = FinanceTheme.typography.titleLarge)
                loan?.let { Text(it.name, style = FinanceTheme.typography.bodyMedium) }
                if (creating || operation == LoanOperation.UPDATE_INFO) OutlinedTextField(name, { name = it }, label = { Text("Người vay") }, singleLine = true)
                if (operation != LoanOperation.UPDATE_INFO) {
                    OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Số tiền (đ)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(date, { date = it }, label = { Text("Ngày (dd/MM/yyyy)") }, singleLine = true)
                }
                if (disbursing) {
                    OutlinedTextField(rate, { rate = it }, label = { Text("Lãi suất năm (%)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(maturity, { maturity = it }, label = { Text("Đáo hạn dd/MM/yyyy (có thể trống)") }, singleLine = true)
                    Text("Giữ kỳ tháng 25–25. Giải ngân giữa kỳ bắt đầu tính từ ngày 25 kế tiếp.", style = FinanceTheme.typography.caption)
                }
                OutlinedTextField(note, { note = it }, label = { Text("Ghi chú") })
                error?.let { Text(it, color = FinanceTheme.colors.expense) }
                Button(enabled = !saving, modifier = Modifier.fillMaxWidth(), onClick = {
                    val parsed = runCatching { LoanFormInput(id, operation, loan?.id, name, amount, rate, date, maturity, note).parse() }
                    parsed.onFailure { error = it.message ?: "Kiểm tra thông tin vừa nhập." }.onSuccess { mutation ->
                        val payload = JSONObject().put("requestId", id).put("operation", operation.name).put("note", mutation.note)
                        mutation.loanId?.let { payload.put("loanId", it) }
                        mutation.borrowerName?.let { payload.put("borrowerName", it) }
                        mutation.amount?.let { payload.put("amount", it) }
                        mutation.date?.let { payload.put("date", it) }
                        mutation.annualRate?.let { payload.put("annualRate", it) }
                        mutation.maturity?.let { payload.put("maturity", it) }
                        saving = true; error = null
                        scope.launch {
                            try { queueMutation(context, id, payload.toString(), "loan-event"); onQueued() }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = "Chưa lưu được yêu cầu trên máy. Vui lòng thử lại." }
                            finally { saving = false }
                        }
                    }
                }) { Text(if (saving) "Đang lưu…" else "Lưu yêu cầu") }
                Text("Dữ liệu hiển thị thay đổi sau khi đồng bộ. Lịch sử gốc và audit được giữ lại.", style = FinanceTheme.typography.caption)
                TextButton(enabled = !saving, onClick = onDismiss) { Text("Hủy") }
            }
        }
    }
}
