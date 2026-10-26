package vn.phuong.financebridge.ui.screens.transactions

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import vn.phuong.financebridge.CategoryChoice
import vn.phuong.financebridge.TransactionEditInput
import vn.phuong.financebridge.ui.data.queueMutation
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.theme.FinanceTheme
import java.util.UUID

@Composable
fun TransactionEditForm(context: Context, transaction: UiTransaction, categories: List<CategoryChoice>, onDismiss: () -> Unit, onQueued: () -> Unit) {
    val scope = rememberCoroutineScope()
    val id = rememberSaveable { UUID.randomUUID().toString() }
    var type by rememberSaveable { mutableStateOf(transaction.type.name) }
    var category by rememberSaveable { mutableStateOf(transaction.category) }
    var note by rememberSaveable { mutableStateOf(transaction.note) }
    var excluded by rememberSaveable { mutableStateOf(transaction.excluded) }
    var menu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(shape = FinanceTheme.shapes.xLarge) {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sửa giao dịch", style = FinanceTheme.typography.titleLarge)
                Text(transaction.formattedAmount, style = FinanceTheme.typography.titleMedium)
                Row {
                    listOf("CHI" to "Chi", "THU" to "Thu").forEach { (value, label) ->
                        TextButton(onClick = {
                            type = value
                            if (categories.none { it.name == category && (it.type == type || it.type == "BOTH") }) category = "Chưa phân loại"
                        }) { Text(if (type == value) "✓ $label" else label) }
                    }
                }
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text("Danh mục: $category") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        categories.filter { it.type == type || it.type == "BOTH" }.forEach { choice ->
                            DropdownMenuItem(text = { Text(choice.name) }, onClick = { category = choice.name; menu = false })
                        }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Ghi chú") })
                Row { Checkbox(checked = excluded, onCheckedChange = { excluded = it }); Text("Không tính vào báo cáo", modifier = Modifier.padding(top = 12.dp)) }
                if (category == "Chuyển nội bộ") Text("Chuyển nội bộ luôn được loại khỏi tổng thu chi.", style = FinanceTheme.typography.caption)
                Text("Giữ nguyên giao dịch nguồn và lưu lịch sử sửa đổi.", style = FinanceTheme.typography.caption)
                error?.let { Text(it, color = FinanceTheme.colors.expense) }
                Button(enabled = !saving, modifier = Modifier.fillMaxWidth(), onClick = {
                    val input = TransactionEditInput(id, transaction.id, transaction.revision, category, note, excluded, type)
                    runCatching { input.validate(categories) }.onFailure { error = it.message ?: "Kiểm tra thông tin vừa nhập." }.onSuccess {
                        val payload = JSONObject().put("requestId", id).put("transactionId", transaction.id).put("baseRevision", transaction.revision)
                            .put("category", category).put("note", note).put("excluded", excluded).put("type", type)
                        saving = true; error = null
                        scope.launch {
                            try { queueMutation(context, id, payload.toString(), "transaction-edit"); onQueued() }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = "Chưa lưu được yêu cầu trên máy. Vui lòng thử lại." }
                            finally { saving = false }
                        }
                    }
                }) { Text(if (saving) "Đang lưu…" else "Lưu thay đổi") }
                TextButton(enabled = !saving, onClick = onDismiss) { Text("Hủy") }
            }
        }
    }
}
