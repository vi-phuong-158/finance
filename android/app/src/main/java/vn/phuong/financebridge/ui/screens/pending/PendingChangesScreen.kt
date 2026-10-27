package vn.phuong.financebridge.ui.screens.pending

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.phuong.financebridge.BlockKind
import vn.phuong.financebridge.BlockedMutation
import vn.phuong.financebridge.CategoryChoice
import vn.phuong.financebridge.EditFields
import vn.phuong.financebridge.EditGuard
import vn.phuong.financebridge.ManualOutbox
import vn.phuong.financebridge.ManualSyncWorker
import vn.phuong.financebridge.blockKind
import vn.phuong.financebridge.blockMessage
import vn.phuong.financebridge.ui.components.EmptyState
import vn.phuong.financebridge.ui.components.FinanceCard
import vn.phuong.financebridge.ui.components.SecondaryButton
import vn.phuong.financebridge.ui.data.FinanceApi
import vn.phuong.financebridge.ui.data.latestTransaction
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.screens.transactions.TransactionEditForm
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.FinanceFormatters

private data class Comparison(val row: BlockedMutation, val latest: UiTransaction, val categories: List<CategoryChoice>, val mine: EditFields)

private fun payloadOf(row: BlockedMutation): JSONObject? = row.payload?.let { runCatching { JSONObject(it) }.getOrNull() }

private fun mineOf(payload: JSONObject) = EditFields(payload.getString("type"), payload.getString("category"), payload.getString("note"), payload.getBoolean("excluded"))

private fun describe(row: BlockedMutation): Pair<String, String> {
    val p = payloadOf(row)
    val note = p?.optString("note")?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
    return when (row.endpoint) {
        "manual-transaction" -> "Ghi chép thủ công" to (p?.let {
            "${if (it.optString("type") == "THU") "Thu" else "Chi"} ${FinanceFormatters.formatVnd(it.optLong("amount"))} · ${it.optString("category")} · ${it.optString("dateTime").replace('T', ' ')}$note"
        } ?: "")
        "transaction-edit" -> "Sửa giao dịch" to (p?.let { "Đổi thành ${it.optString("category")}$note" } ?: "")
        "loan-event" -> "Khoản vay / thu tiền" to ""
        "category-memory" -> "Ghi nhớ phân loại" to ""
        else -> "Yêu cầu khác" to ""
    }
}

// Blocked requests with their stored reason. Only BLOCKED rows are acted on here, which the worker never touches.
@Composable
fun PendingChangesScreen(context: Context, modifier: Modifier = Modifier) {
    val api = remember(context) { FinanceApi(context) }
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<BlockedMutation>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var comparing by remember { mutableStateOf<Comparison?>(null) }
    var resolving by remember { mutableStateOf<Comparison?>(null) }
    var confirmCancel by remember { mutableStateOf<BlockedMutation?>(null) }
    LaunchedEffect(reload) { rows = withContext(Dispatchers.IO) { ManualOutbox(context).use { it.blocked() } } }

    fun act(block: suspend () -> String?) {
        scope.launch {
            busy = true
            message = try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { "Chưa thực hiện được. Vui lòng thử lại." }
            busy = false; reload++
        }
    }

    Column(modifier.fillMaxSize().background(FinanceTheme.colors.background).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Thay đổi cần xử lý", style = FinanceTheme.typography.headlineLarge)
        message?.let { Text(it, style = FinanceTheme.typography.bodyMedium) }
        if (rows.isEmpty()) EmptyState(title = "Không có thay đổi cần xử lý", subtitle = "Mọi thay đổi đã đồng bộ hoặc đang chờ gửi.")
        rows.forEach { row ->
            val (title, summary) = describe(row)
            val kind = blockKind(row.lastError)
            FinanceCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = FinanceTheme.typography.titleMedium)
                    if (summary.isNotBlank()) Text(summary, style = FinanceTheme.typography.bodyMedium)
                    Text(blockMessage(row.lastError), style = FinanceTheme.typography.caption, color = FinanceTheme.colors.expense)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (kind) {
                            BlockKind.RETRYABLE -> SecondaryButton(text = "Gửi lại", enabled = !busy, onClick = {
                                act {
                                    val queued = withContext(Dispatchers.IO) { ManualOutbox(context).use { it.retry(row.id) } }
                                    if (queued) ManualSyncWorker.schedule(context)
                                    if (queued) "Đã đưa vào hàng đợi gửi lại." else "Yêu cầu này không còn chờ xử lý."
                                }
                            })
                            BlockKind.EDIT_CONFLICT -> {
                                SecondaryButton(text = "Tải bản mới và đối chiếu", enabled = !busy, onClick = {
                                    act {
                                        val payload = payloadOf(row) ?: return@act "Không đọc được thay đổi đã lưu."
                                        val lookup = api.latestTransaction(row.targetId ?: payload.getString("transactionId")).getOrElse { return@act it.message }
                                        val latest = lookup.transaction ?: return@act "Không còn tìm thấy giao dịch này trên máy chủ. Bạn có thể bỏ thay đổi."
                                        comparing = Comparison(row, latest, lookup.categories, mineOf(payload))
                                        null
                                    }
                                })
                                TextButton(enabled = !busy, onClick = { confirmCancel = row }) { Text("Bỏ thay đổi") }
                            }
                            BlockKind.PERMANENT -> TextButton(enabled = !busy, onClick = { confirmCancel = row }) { Text("Hủy yêu cầu") }
                        }
                    }
                }
            }
        }
    }

    comparing?.let { c ->
        val server = EditFields(c.latest.type.name, c.latest.category, c.latest.note, c.latest.excluded)
        AlertDialog(onDismissRequest = { comparing = null }, title = { Text("Đối chiếu thay đổi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${c.latest.formattedAmount} · ${c.latest.formattedDate}", style = FinanceTheme.typography.bodyMedium)
                    EditGuard.diff(server, c.mine).forEach { d ->
                        Text(d.label, style = FinanceTheme.typography.caption)
                        Text("Trên máy chủ: ${d.server}")
                        Text("Bạn muốn đổi thành: ${d.mine}", color = if (d.changed) FinanceTheme.colors.primary else FinanceTheme.colors.textMuted)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { resolving = c; comparing = null }) { Text("Xác nhận lại") } },
            dismissButton = { TextButton(onClick = { comparing = null }) { Text("Đóng") } })
    }

    resolving?.let { c ->
        TransactionEditForm(context, c.latest, c.categories, null, initial = c.mine, replacing = c.row.id,
            onDismiss = { resolving = null },
            onQueued = { resolving = null; message = "Đã lưu thay đổi trên bản mới nhất. Sẽ gửi khi có mạng."; reload++ })
    }

    confirmCancel?.let { row ->
        val (title, summary) = describe(row)
        AlertDialog(onDismissRequest = { confirmCancel = null }, title = { Text("Bỏ yêu cầu này?") },
            text = {
                Text("$title sẽ bị xóa khỏi máy và không gửi lên sổ." + (if (summary.isNotBlank()) " Ghi lại nếu cần nhập lại: $summary" else ""))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = null
                    act { withContext(Dispatchers.IO) { ManualOutbox(context).use { it.cancel(row.id) } }; "Đã bỏ yêu cầu." }
                }) { Text("Bỏ yêu cầu", color = FinanceTheme.colors.expense) }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = null }) { Text("Giữ lại") } })
    }
}
