package vn.phuong.financebridge.ui.screens.transactions

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import vn.phuong.financebridge.ui.data.queueMutation
import vn.phuong.financebridge.ui.data.supersedeEdit
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.CategoryVisuals
import java.util.UUID

sealed interface EditStart {
    data class Ready(val transaction: UiTransaction, val latestSyncedEdit: String?) : EditStart
    data class Refused(val message: String) : EditStart
}

// Only the local queue gates opening the form; latest-revision verification runs afterwards.
suspend fun prepareEdit(context: Context, shown: UiTransaction): EditStart {
    val (active, synced) = withContext(Dispatchers.IO) { ManualOutbox(context).use { it.activeEdit(shown.id) to it.latestSyncedEdit(shown.id) } }
    when (EditGuard.queued(active)) {
        EditGate.WAIT_FOR_SYNC -> return EditStart.Refused("Giao dịch này đang có thay đổi chờ đồng bộ. Đợi đồng bộ xong rồi sửa tiếp.")
        EditGate.RESOLVE_CONFLICT -> return EditStart.Refused("Lần sửa trước của giao dịch này bị xung đột. Mở Khác → Thay đổi cần xử lý để đối chiếu.")
        else -> Unit
    }
    return EditStart.Ready(shown, synced)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditForm(context: Context, transaction: UiTransaction, categories: List<CategoryChoice>, memory: CategoryMemoryIndex?,
    onDismiss: () -> Unit, onQueued: () -> Unit, initial: EditFields? = null, replacing: String? = null, embedded: Boolean = false, onSavingChanged: (Boolean) -> Unit = {},
    draft: EditFields? = null, onDraftChanged: (EditFields) -> Unit = {}, canSave: Boolean = true,
    verifying: Boolean = false, verificationMessage: String? = null) {
    // `transaction` is the latest server state; `initial`/`replacing` re-apply a conflicting edit on top of it.
    val scope = rememberCoroutineScope()
    val id = rememberSaveable { UUID.randomUUID().toString() }
    var localType by rememberSaveable { mutableStateOf(initial?.type ?: transaction.type.name) }
    var localCategory by rememberSaveable {
        mutableStateOf(initial?.category ?: CategoryMemory.preselectForEdit(CategoryMemory.suggest(memory, transaction.memoryKey, transaction.type.name),
            transaction.source, transaction.revision, transaction.category))
    }
    var hideSuggestion by rememberSaveable { mutableStateOf(false) }
    var localNote by rememberSaveable { mutableStateOf(initial?.note ?: transaction.note) }
    var localExcluded by rememberSaveable { mutableStateOf(initial?.excluded ?: transaction.excluded) }
    var saving by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val type = draft?.type ?: localType
    val category = draft?.category ?: localCategory
    val note = draft?.note ?: localNote
    val excluded = draft?.excluded ?: localExcluded
    fun updateFields(fields: EditFields) {
        localType = fields.type; localCategory = fields.category; localNote = fields.note; localExcluded = fields.excluded
        onDraftChanged(fields)
    }
    fun currentFields() = EditFields(type, category, note, excluded)
    // Rules are per direction, so switching Thu/Chi looks up a different rule.
    val suggestion = CategoryMemory.suggest(memory, transaction.memoryKey, type)
        ?.takeIf { categories.any { c -> c.name == it.category } }
        ?.takeIf { !(it.category == transaction.category && (transaction.revision != "0" || transaction.source == "MANUAL")) }
    val form: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (replacing != null) "Xác nhận lại thay đổi" else "Sửa giao dịch", style = FinanceTheme.typography.titleLarge)
                Text(transaction.formattedAmount, style = FinanceTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("CHI" to "Chi", "THU" to "Thu").forEachIndexed { index, (value, label) ->
                        SegmentedButton(selected = type == value, onClick = {
                            updateFields(currentFields().copy(type = value, category = if (categories.any { it.name == category && (it.type == value || it.type == "BOTH") }) category else "Chưa phân loại"))
                        }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) }
                    }
                }
                Text("Danh mục", style = FinanceTheme.typography.titleMedium)
                BoxWithConstraints {
                    val columns = CategoryVisuals.columns(maxWidth.value.toInt())
                    val filtered = categories.filter { it.type == type || it.type == "BOTH" }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (suggestion != null && filtered.any { it.name == suggestion.category }) {
                            Text("Đề xuất", style = FinanceTheme.typography.caption)
                            CategoryTile(suggestion.category, category == suggestion.category, { updateFields(currentFields().copy(category = suggestion.category)) }, Modifier.fillMaxWidth())
                        }
                        filtered.filterNot { it.name == suggestion?.category }.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { choice ->
                                    CategoryTile(choice.name, category == choice.name, { updateFields(currentFields().copy(category = choice.name)) }, Modifier.weight(1f))
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                if (suggestion != null && !hideSuggestion) CategorySuggestionCard(suggestion, applied = category == suggestion.category,
                    onApply = { updateFields(currentFields().copy(category = suggestion.category)) },
                    onDismiss = {
                        if (category == suggestion.category && category != transaction.category) updateFields(currentFields().copy(category = transaction.category))
                        hideSuggestion = true
                    })
                OutlinedTextField(note, { updateFields(currentFields().copy(note = it)) }, label = { Text("Ghi chú") })
                Row { Checkbox(checked = excluded, onCheckedChange = { updateFields(currentFields().copy(excluded = it)) }); Text("Không tính vào báo cáo", modifier = Modifier.padding(top = 12.dp)) }
                if (category == "Chuyển nội bộ") Text("Chuyển nội bộ luôn được loại khỏi tổng thu chi.", style = FinanceTheme.typography.caption)
                Text("Giữ nguyên giao dịch nguồn và lưu lịch sử sửa đổi.", style = FinanceTheme.typography.caption)
                verificationMessage?.let { Text(it, style = FinanceTheme.typography.bodyMedium) }
                error?.let { Text(it, color = FinanceTheme.colors.expense) }
            }
                Button(enabled = !saving && canSave && !verifying, modifier = Modifier.fillMaxWidth(), onClick = {
                    val input = TransactionEditInput(id, transaction.id, transaction.revision, category, note, excluded, type)
                    runCatching { input.validate(categories) }.onFailure { error = it.message ?: "Kiểm tra thông tin vừa nhập." }.onSuccess {
                        val payload = JSONObject().put("requestId", id).put("transactionId", transaction.id).put("baseRevision", transaction.revision)
                            .put("category", category).put("note", note).put("excluded", excluded).put("type", type)
                        saving = true; onSavingChanged(true); error = null
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
                            finally { saving = false; onSavingChanged(false) }
                        }
                    }
                }) { Text(if (saving) "Đang lưu…" else if (verifying) "Đang kiểm tra bản mới nhất…" else "Lưu thay đổi") }
                TextButton(enabled = !saving, onClick = onDismiss) { Text("Hủy") }
        }
    }
    if (embedded) form() else Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(shape = FinanceTheme.shapes.xLarge) { form() }
    }
}

@Composable
private fun CategoryTile(name: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint = CategoryVisuals.color(name, FinanceTheme.colors)
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 96.dp), shape = FinanceTheme.shapes.medium,
        color = if (selected) FinanceTheme.colors.primaryContainer else FinanceTheme.colors.surface,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, FinanceTheme.colors.primary) else null) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(36.dp).background(tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(CategoryVisuals.icon(name), null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Text(name, style = FinanceTheme.typography.caption, maxLines = 2, minLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}
