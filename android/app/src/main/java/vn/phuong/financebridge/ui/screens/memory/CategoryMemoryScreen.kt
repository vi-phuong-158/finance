package vn.phuong.financebridge.ui.screens.memory

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import vn.phuong.financebridge.CategoryMemoryIndex
import vn.phuong.financebridge.MemoryCommand
import vn.phuong.financebridge.MemoryRule
import vn.phuong.financebridge.ui.components.EmptyState
import vn.phuong.financebridge.ui.components.FinanceCard
import vn.phuong.financebridge.ui.components.LoadingState
import vn.phuong.financebridge.ui.components.SecondaryButton
import vn.phuong.financebridge.ui.data.CategoryMemoryRepository
import vn.phuong.financebridge.ui.data.queueMutation
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.FinanceFormatters
import java.util.UUID

@Composable
fun CategoryMemoryScreen(context: Context, modifier: Modifier = Modifier) {
    val repository = remember(context) { CategoryMemoryRepository(context) }
    val scope = rememberCoroutineScope()
    var memory by remember { mutableStateOf<CategoryMemoryIndex?>(null) }
    var loading by remember { mutableStateOf(true) }
    var disabled by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<MemoryRule?>(null) }
    var deleting by remember { mutableStateOf<MemoryRule?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        loading = true
        memory = repository.cached()
        val state = repository.refresh()
        memory = state.index; disabled = state.disabled; offline = state.offline
        loading = false
    }

    fun send(command: MemoryCommand) {
        val index = memory ?: return
        runCatching { command.validate(index) }.onFailure { message = it.message ?: "Yêu cầu chưa hợp lệ." }.onSuccess {
            val payload = JSONObject().put("requestId", command.requestId).put("action", command.action)
            if (command.action != "RESET_ALL") payload.put("identityKey", command.identityKey).put("type", command.type)
            if (command.action == "SET") payload.put("category", command.category)
            scope.launch {
                message = try {
                    queueMutation(context, command.requestId, payload.toString(), "category-memory")
                    "Đã lưu yêu cầu trên máy. Ghi nhớ cập nhật sau khi đồng bộ và tải lại."
                } catch (e: CancellationException) { throw e } catch (_: Exception) { "Chưa lưu được yêu cầu. Vui lòng thử lại." }
            }
        }
    }

    Column(modifier.fillMaxSize().background(FinanceTheme.colors.background).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ghi nhớ phân loại", style = FinanceTheme.typography.headlineLarge, color = FinanceTheme.colors.textPrimary)
        Text("App nhớ danh mục bạn từng chọn cho cùng người nhận, cửa hàng hoặc nội dung và gợi ý lại. Bạn luôn là người quyết định.",
            style = FinanceTheme.typography.caption, color = FinanceTheme.colors.textSecondary)
        if (offline) Text("Đang dùng dữ liệu đã lưu trên máy.", style = FinanceTheme.typography.caption, color = FinanceTheme.colors.textMuted)
        message?.let { Text(it, style = FinanceTheme.typography.bodyMedium) }
        val index = memory
        when {
            loading && index == null -> LoadingState()
            disabled -> EmptyState(title = "Tính năng chưa bật", subtitle = "Gợi ý từ lịch sử đang tắt trên máy chủ.")
            index == null -> EmptyState(title = "Chưa tải được ghi nhớ", subtitle = "Kiểm tra kết nối rồi thử lại.")
            index.rules.isEmpty() -> EmptyState(title = "Chưa có ghi nhớ", subtitle = "Khi bạn chọn hoặc sửa danh mục, app sẽ nhớ để gợi ý lần sau.")
            else -> {
                if (!index.writesEnabled) Text("Chỉnh sửa ghi nhớ chưa bật trên máy chủ.", style = FinanceTheme.typography.caption, color = FinanceTheme.colors.textMuted)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(index.rules, key = { "${it.key}|${it.type}" }) { rule ->
                        MemoryRuleRow(rule, index.writesEnabled, onEdit = { editing = rule }, onDelete = { deleting = rule })
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { reload++ }) { Text("Tải lại") }
            if (index?.writesEnabled == true && index.rules.isNotEmpty()) TextButton(onClick = { confirmReset = true }) {
                Text("Đặt lại toàn bộ", color = FinanceTheme.colors.expense)
            }
        }
        Spacer(Modifier.height(48.dp))
    }

    editing?.let { rule ->
        var menu by remember { mutableStateOf(false) }
        var choice by remember(rule) { mutableStateOf(rule.category) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("Sửa ghi nhớ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(rule.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Box {
                        OutlinedButton(onClick = { menu = true }) { Text("Danh mục: $choice", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            memory?.categories.orEmpty().filter { it.type == rule.type || it.type == "BOTH" }.forEach {
                                DropdownMenuItem(text = { Text(it.name) }, onClick = { choice = it.name; menu = false })
                            }
                        }
                    }
                    Text("Lần sau app sẽ chọn sẵn danh mục này. Nếu bạn chọn khác nhiều lần, ghi nhớ tự đổi theo.", style = FinanceTheme.typography.caption)
                }
            },
            confirmButton = { TextButton(onClick = {
                send(MemoryCommand(UUID.randomUUID().toString(), "SET", rule.key, rule.type, choice)); editing = null
            }) { Text("Lưu") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Hủy") } })
    }
    deleting?.let { rule ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Xóa ghi nhớ?") },
            text = { Text("App sẽ quên ${rule.label} → ${rule.category}. Giao dịch đã lưu không thay đổi.") },
            confirmButton = { TextButton(onClick = {
                send(MemoryCommand(UUID.randomUUID().toString(), "DELETE", rule.key, rule.type)); deleting = null
            }) { Text("Xóa", color = FinanceTheme.colors.expense) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Hủy") } })
    }
    if (confirmReset) {
        AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Đặt lại toàn bộ ghi nhớ?") },
            text = { Text("App sẽ bắt đầu học lại từ các lựa chọn tiếp theo. Giao dịch và lịch sử sửa đổi được giữ nguyên.") },
            confirmButton = { TextButton(onClick = { send(MemoryCommand(UUID.randomUUID().toString(), "RESET_ALL")); confirmReset = false }) {
                Text("Đặt lại", color = FinanceTheme.colors.expense)
            } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Hủy") } })
    }
}

@Composable
private fun MemoryRuleRow(rule: MemoryRule, writesEnabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    FinanceCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(rule.label, style = FinanceTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = FinanceTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("→ ${rule.category}", style = FinanceTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = FinanceTheme.colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val strength = when (rule.level) { "STRONG" -> "Chọn sẵn · rất thường dùng"; "PRESELECT" -> "Chọn sẵn"; "HINT" -> "Chỉ gợi ý"; else -> "Chưa chắc chắn" }
            val lastUsed = runCatching { FinanceFormatters.formatFriendlyDate(java.time.Instant.parse(rule.lastUsedAt).toEpochMilli()) }.getOrDefault("")
            Text("${if (rule.type == "THU") "Thu" else "Chi"} · ${rule.count}/${rule.total} lần gần đây · $strength${if (lastUsed.isNotEmpty()) " · $lastUsed" else ""}",
                style = FinanceTheme.typography.caption, color = FinanceTheme.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (writesEnabled) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SecondaryButton(text = "Sửa", onClick = onEdit)
                TextButton(onClick = onDelete) { Text("Xóa", color = FinanceTheme.colors.expense) }
            }
        }
    }
}
