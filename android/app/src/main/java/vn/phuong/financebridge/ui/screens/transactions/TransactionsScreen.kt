package vn.phuong.financebridge.ui.screens.transactions

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.phuong.financebridge.ui.components.*
import vn.phuong.financebridge.ui.data.FinanceApi
import vn.phuong.financebridge.ui.data.parseTransaction
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.FinancePeriod
import vn.phuong.financebridge.CategoryChoice
import vn.phuong.financebridge.CategoryMemoryIndex
import vn.phuong.financebridge.ui.data.CategoryMemoryRepository

private data class TransactionPage(val rows: List<UiTransaction>, val cursor: String?, val total: Int,
    val categories: List<CategoryChoice>, val editingEnabled: Boolean)

@Composable
fun TransactionsScreen(context: Context, modifier: Modifier = Modifier) {
    val api = remember(context) { FinanceApi(context) }
    var search by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }
    var rows by remember { mutableStateOf<List<UiTransaction>>(emptyList()) }
    var selected by remember { mutableStateOf<UiTransaction?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableStateOf(0) }
    var categories by remember { mutableStateOf<List<CategoryChoice>>(emptyList()) }
    var editingEnabled by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<UiTransaction?>(null) }
    var opening by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var savedMessage by remember { mutableStateOf<String?>(null) }
    val memoryRepository = remember(context) { CategoryMemoryRepository(context) }
    var memory by remember { mutableStateOf<CategoryMemoryIndex?>(null) }

    LaunchedEffect(revision) {
        // Cache first so suggestions appear instantly and offline; refresh replaces it when online.
        memory = memoryRepository.cached()
        memory = memoryRepository.refresh().index
    }

    LaunchedEffect(filters, search, cursor, revision) {
        loading = true
        error = null
        if (cursor == null) rows = emptyList()
        delay(300)
        val query = filters + mapOf("q" to search, "limit" to "50") + (cursor?.let { mapOf("cursor" to it) } ?: emptyMap())
        api.read("transactions", query) { json ->
            val values = json.getJSONArray("transactions")
            val choices = json.optJSONArray("categories")
            TransactionPage((0 until values.length()).map { parseTransaction(values.getJSONObject(it)) },
                if (json.isNull("nextCursor")) null else json.getString("nextCursor"), json.getInt("total"),
                if (choices == null) emptyList() else (0 until choices.length()).map {
                    val choice = choices.getJSONObject(it); CategoryChoice(choice.getString("name"), choice.getString("type"))
                }, json.optBoolean("editingEnabled", false))
        }.onSuccess { page ->
            rows = if (cursor == null) page.rows else rows + page.rows
            nextCursor = page.cursor
            total = page.total
            categories = page.categories
            editingEnabled = page.editingEnabled
        }.onFailure { error = it.message }
        loading = false
    }

    Column(modifier.fillMaxSize().background(FinanceTheme.colors.background).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Lịch sử giao dịch", style = FinanceTheme.typography.headlineLarge)
        OutlinedTextField(search, { search = it; cursor = null }, placeholder = { Text("Tìm giao dịch, ghi chú…") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(text = if (showFilters) "Ẩn bộ lọc" else "Bộ lọc", onClick = { showFilters = !showFilters })
            TextButton(onClick = { cursor = null; revision++ }) { Text("Tải lại") }
        }
        if (showFilters) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("" to "Tất cả", "THU" to "Thu", "CHI" to "Chi").forEach { (value, label) ->
                    FinanceChip(text = label, isSelected = type == value, onClick = { type = value })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("" to "Mọi ngân hàng", "VCB" to "VCB", "VIETINBANK" to "VietinBank", "BIDV" to "BIDV").forEach { (value, label) ->
                    FinanceChip(text = label, isSelected = bank == value, onClick = { bank = value })
                }
            }
            OutlinedTextField(category, { category = it }, label = { Text("Danh mục (để trống = tất cả)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(from, { from = it }, label = { Text("Từ dd/MM/yyyy") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(to, { to = it }, label = { Text("Đến dd/MM/yyyy") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    runCatching {
                        val dateQuery = if (from.isBlank() && to.isBlank()) emptyMap() else FinancePeriod.CUSTOM.query(from, to)
                        dateQuery + mapOf("type" to type, "bank" to bank, "category" to category).filterValues { it.isNotBlank() }
                    }.onSuccess { filters = it; cursor = null; inputError = null; revision++ }
                        .onFailure { inputError = "Kiểm tra khoảng ngày theo dd/MM/yyyy." }
                }) { Text("Áp dụng") }
                TextButton(onClick = {
                    from = ""; to = ""; type = ""; bank = ""; category = ""; search = ""
                    filters = emptyMap(); cursor = null; inputError = null; revision++
                }) { Text("Bỏ lọc") }
            }
        }
        inputError?.let { Text(it, color = FinanceTheme.colors.expense) }
        savedMessage?.let { Text(it, style = FinanceTheme.typography.bodyMedium) }
        if (loading && rows.isEmpty()) LoadingState()
        if (error != null) ErrorState(message = error!!, onRetry = { cursor = null; revision++ })
        if (!loading && error == null && rows.isEmpty()) EmptyState(title = "Không tìm thấy giao dịch", subtitle = "Thử đổi bộ lọc hoặc từ khóa.")
        if (error == null && rows.isNotEmpty()) {
            Text("Đang hiển thị ${rows.size}/$total giao dịch", style = FinanceTheme.typography.caption)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 56.dp)) {
                items(rows, key = { it.id }) { transaction ->
                    FinanceCard(contentPadding = 8.dp) { TransactionRow(transaction, onClick = { selected = transaction }) }
                }
                item {
                    if (nextCursor != null) SecondaryButton(text = if (loading) "Đang tải…" else "Xem thêm", enabled = !loading,
                        onClick = { cursor = nextCursor })
                }
            }
        }
    }
    selected?.let { transaction ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text("Chi tiết giao dịch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(transaction.formattedAmount, color = FinanceTheme.colors.primary)
                    Text(transaction.category)
                    Text(transaction.title)
                    Text(transaction.formattedDate)
                    Text(transaction.accountOrBank)
                    Text(when (transaction.source) { "MONEYLOVER" -> "Nguồn: Nhập lịch sử"; "MANUAL" -> "Nguồn: Ghi chép thủ công"; else -> "Nguồn: Thông báo ngân hàng" })
                    if (transaction.isDuplicateCandidate) Text("Có thể trùng giao dịch từ nguồn khác. Cần đối soát.", color = FinanceTheme.colors.expense)
                    if (transaction.excluded) Text("Không tính vào tổng thu chi.")
                    if (!editingEnabled) Text("Chưa mở chỉnh sửa trên máy chủ.", style = FinanceTheme.typography.caption)
                }
            }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Đóng") } },
            dismissButton = { if (editingEnabled) TextButton(enabled = !opening, onClick = {
                scope.launch {
                    opening = true
                    when (val start = prepareEdit(context, api, transaction)) {
                        is EditStart.Ready -> { start.categories?.let { categories = it }; editing = start.transaction }
                        is EditStart.Refused -> savedMessage = start.message
                    }
                    selected = null; opening = false
                }
            }) { Text(if (opening) "Đang tải…" else "Sửa") } })
    }
    editing?.let { transaction ->
        TransactionEditForm(context, transaction, categories, memory, onDismiss = { editing = null }, onQueued = {
            editing = null; selected = null; cursor = null; revision++
            savedMessage = "Đã lưu thay đổi trên máy. Bấm Tải lại sau khi đồng bộ để xem kết quả."
        })
    }
}
