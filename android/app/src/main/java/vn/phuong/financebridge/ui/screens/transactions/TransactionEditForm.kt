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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.phuong.financebridge.CategoryChoice
import vn.phuong.financebridge.CategoryMemory
import vn.phuong.financebridge.CategoryMemoryIndex
import vn.phuong.financebridge.EditFields
import vn.phuong.financebridge.EditGate
import vn.phuong.financebridge.EditGuard
import vn.phuong.financebridge.ManualOutbox
import vn.phuong.financebridge.TransactionEditInput
import vn.phuong.financebridge.ui.components.CategorySuggestionCard
import vn.phuong.financebridge.ui.data.FinanceApi
import vn.phuong.financebridge.ui.data.latestTransaction
import vn.phuong.financebridge.ui.data.queueMutation
import vn.phuong.financebridge.ui.data.supersedeEdit
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.theme.FinanceTheme
import java.util.UUID

sealed interface EditStart {
    data class Ready(val transaction: UiTransaction, val categories: List<CategoryChoice>?) : EditStart
    data class Refused(val message: String) : EditStart
}

// Decides which revision an edit may be based on before the form opens (see EditGuard).
suspend fun prepareEdit(context: Context, api: FinanceApi, shown: UiTransaction): EditStart {
    val (active, synced) = withContext(Dispatchers.IO) { ManualOutbox(context).use { it.activeEdit(shown.id) to it.latestSyncedEdit(shown.id) } }
    when (EditGuard.queued(active)) {
        EditGate.WAIT_FOR_SYNC -> return EditStart.Refused("Giao dịch này đang có thay đổi chờ đồng bộ. Đợi đồng bộ xong rồi sửa tiếp.")
        EditGate.RESOLVE_CONFLICT -> return EditStart.Refused("Lần sửa trước của giao dịch này bị xung đột. Mở Khác → Thay đổi cần xử lý để đối chiếu.")
        else -> Unit
    }
    api.latestTransaction(shown.id).getOrNull()?.let { lookup ->
        val latest = lookup.transaction ?: return EditStart.Refused("Không còn tìm thấy giao dịch này trên máy chủ. Hãy tải lại danh sách.")
        if (!lookup.editingEnabled) return EditStart.Refused("Chưa mở chỉnh sửa trên máy chủ.")
        return EditStart.Ready(latest, lookup.categories)
    }
    return if (EditGuard.offline(synced, shown.revision) == EditGate.READY_SHOWN) EditStart.Ready(shown, null)
    else EditStart.Refused("Cần kết nối mạng để tải bản mới nhất trước khi sửa.")
}

@Composable
fun TransactionEditForm(context: Context, transaction: UiTransaction, categories: List<CategoryChoice>, memory: CategoryMemoryIndex?,
    onDismiss: () -> Unit, onQueued: () -> Unit, initial: EditFields? = null, replacing: String? = null) {
    // `transaction` is the latest server state; `initial`/`replacing` re-apply a conflicting edit on top of it.
    val scope = rememberCoroutineScope()
    val id = rememberSaveable { UUID.randomUUID().toString() }
    var type by rememberSaveable { mutableStateOf(initial?.type ?: transaction.type.name) }
    var category by rememberSaveable {
        mutableStateOf(initial?.category ?: CategoryMemory.preselectForEdit(CategoryMemory.suggest(memory, transaction.memoryKey, transaction.type.name),
            transaction.source, transaction.revision, transaction.category))
    }
    var hideSuggestion by rememberSaveable { mutableStateOf(false) }
    // Rules are per direction, so switching Thu/Chi looks up a different rule.
    val suggestion = CategoryMemory.suggest(memory, transaction.memoryKey, type)
        ?.takeIf { categories.any { c -> c.name == it.category } }
        ?.takeIf { !(it.category == transaction.category && (transaction.revision != "0" || transaction.source == "MANUAL")) }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: transaction.note) }
    var excluded by rememberSaveable { mutableStateOf(initial?.excluded ?: transaction.excluded) }
    var menu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(shape = FinanceTheme.shapes.xLarge) {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (replacing != null) "Xác nhận lại thay đổi" else "Sửa giao dịch", style = FinanceTheme.typography.titleLarge)
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
                if (suggestion != null && !hideSuggestion) CategorySuggestionCard(suggestion, applied = category == suggestion.category,
                    onApply = { category = suggestion.category },
                    onDismiss = {
                        if (category == suggestion.category && category != transaction.category) category = transaction.category
                        hideSuggestion = true
                    })
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
                            try {
                                if (replacing != null) supersedeEdit(context, replacing, id, payload.toString())
                                else queueMutation(context, id, payload.toString(), "transaction-edit")
                                onQueued()
                            }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) {
                                error = when (e.message) {
                                    "EDIT_ALREADY_QUEUED" -> "Giao dịch này đã có thay đổi đang chờ đồng bộ. Đợi đồng bộ xong rồi sửa tiếp."
                                    "REQUEST_NOT_BLOCKED" -> "Yêu cầu cũ đã được xử lý. Hãy tải lại danh sách."
                                    else -> "Chưa lưu được yêu cầu trên máy. Vui lòng thử lại."
                                }
                            }
                            finally { saving = false }
                        }
                    }
                }) { Text(if (saving) "Đang lưu…" else "Lưu thay đổi") }
                TextButton(enabled = !saving, onClick = onDismiss) { Text("Hủy") }
            }
        }
    }
}
