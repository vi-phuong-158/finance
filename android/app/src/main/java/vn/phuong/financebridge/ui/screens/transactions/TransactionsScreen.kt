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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import vn.phuong.financebridge.ui.components.*
import vn.phuong.financebridge.ui.data.FinanceApi
import vn.phuong.financebridge.ui.data.parseTransaction
import vn.phuong.financebridge.ui.data.latestTransaction
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.FinancePeriod
import vn.phuong.financebridge.ui.util.CategoryVisuals
import vn.phuong.financebridge.EditFields
import vn.phuong.financebridge.CategoryMemory
import vn.phuong.financebridge.CategoryChoice
import vn.phuong.financebridge.CategoryMemoryIndex
import vn.phuong.financebridge.ui.data.CategoryMemoryRepository

private data class TransactionPage(val rows: List<UiTransaction>, val cursor: String?, val total: Int,
    val categories: List<CategoryChoice>, val editingEnabled: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(context: Context, modifier: Modifier = Modifier, initialTransactionId: String? = null, initialTransaction: UiTransaction? = null, onInitialConsumed: () -> Unit = {}) {
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
    var selected by rememberSaveable(stateSaver = TransactionSaver) { mutableStateOf<UiTransaction?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var editRequested by rememberSaveable { mutableStateOf(false) }
    var expandedDescription by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableStateOf(0) }
    var categories by remember { mutableStateOf<List<CategoryChoice>>(emptyList()) }
    var editingEnabled by remember { mutableStateOf(false) }
    var editing by rememberSaveable(stateSaver = TransactionSaver) { mutableStateOf<UiTransaction?>(null) }
    var editFields by rememberSaveable(stateSaver = EditFieldsSaver) { mutableStateOf<EditFields?>(null) }
    var verifyingEdit by remember { mutableStateOf(false) }
    var editAllowed by remember { mutableStateOf(false) }
    var editNotice by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf(false) }
    var savingEdit by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    val memoryRepository = remember(context) { CategoryMemoryRepository(context) }
    var memory by remember { mutableStateOf<CategoryMemoryIndex?>(null) }

    LaunchedEffect(initialTransactionId) {
        if (initialTransactionId != null) {
            selectedId = initialTransactionId
            selected = initialTransaction?.takeIf { it.id == initialTransactionId }
            val lookup = api.latestTransaction(initialTransactionId).getOrNull()
            if (selected == null) selected = lookup?.transaction
            lookup?.let { categories = it.categories; editingEnabled = it.editingEnabled }
            if (selected == null) { selectedId = null; savedMessage = "Không tải được giao dịch. Vui lòng thử lại." }
            onInitialConsumed()
        }
    }
    LaunchedEffect(rows, selectedId) {
        if (selected == null && selectedId != null) selected = rows.firstOrNull { it.id == selectedId }
    }
    LaunchedEffect(selectedId, editRequested) {
        val transaction = selected
        if (transaction != null && editRequested) {
            opening = true; verifyingEdit = true; editAllowed = false
            try {
                val shown = editing ?: transaction
                when (val start = prepareEdit(context, shown)) {
                    is EditStart.Refused -> {
                        savedMessage = start.message; editRequested = false; editing = null; editFields = null
                    }
                    is EditStart.Ready -> {
                        editing = shown
                        if (editFields == null) {
                            val proposed = CategoryMemory.preselectForEdit(CategoryMemory.suggest(memory, shown.memoryKey, shown.type.name),
                                shown.source, shown.revision, shown.category)
                            editFields = TransactionEditFlow.fields(shown).copy(category = proposed)
                        }
                        editNotice = TransactionEditFlow.pending(shown, editFields!!).message
                        // Form is already mounted. Only Save waits for this network read.
                        val lookup = api.latestTransaction(shown.id)
                        val verified = lookup.fold(onSuccess = { latest ->
                            categories = latest.categories
                            TransactionEditFlow.online(shown, latest.transaction, editFields!!, latest.editingEnabled)
                        }, onFailure = { TransactionEditFlow.offline(shown, editFields!!, start.latestSyncedEdit) })
                        editing = verified.base; editFields = verified.fields
                        editAllowed = verified.canSave; editNotice = verified.message
                    }
                }
            } finally {
                // Cancellation from close/fold cannot leave a later transaction stuck opening.
                opening = false; verifyingEdit = false
            }
        }
    }
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
                    FinanceCard(contentPadding = 8.dp) { TransactionRow(transaction, onClick = { selectedId = transaction.id; selected = transaction; expandedDescription = false }) }
                }
                item {
                    if (nextCursor != null) SecondaryButton(text = if (loading) "Đang tải…" else "Xem thêm", enabled = !loading,
                        onClick = { cursor = nextCursor })
                }
            }
        }
    }
    selected?.let { transaction ->
        val sheetState = rememberModalBottomSheetState(confirmValueChange = { !savingEdit || it != SheetValue.Hidden })
        ModalBottomSheet(sheetState = sheetState, onDismissRequest = { if (!savingEdit) { selected = null; selectedId = null; editing = null; editFields = null; editRequested = false } }) {
            if (editing != null) {
                TransactionEditForm(context, editing!!, categories, memory, embedded = true, onSavingChanged = { savingEdit = it },
                    draft = editFields, onDraftChanged = { editFields = it }, canSave = editAllowed,
                    verifying = verifyingEdit, verificationMessage = editNotice,
                    onDismiss = { editing = null; editFields = null; editRequested = false }, onQueued = {
                        editing = null; editFields = null; selected = null; selectedId = null; editRequested = false; cursor = null; revision++
                        savedMessage = "Đã lưu thay đổi trên máy. Bấm Tải lại sau khi đồng bộ để xem kết quả."
                    })
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(CategoryVisuals.icon(transaction.category), null,
                            tint = CategoryVisuals.color(transaction.category, FinanceTheme.colors))
                        Column {
                            Text(transaction.formattedAmount, style = FinanceTheme.typography.titleLarge,
                                color = if (transaction.type == TransactionType.THU) FinanceTheme.colors.income else FinanceTheme.colors.expense)
                            Text(transaction.category, style = FinanceTheme.typography.titleMedium)
                        }
                    }
                    Text(transaction.title, maxLines = if (expandedDescription) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                    if (transaction.title.isNotBlank()) TextButton(onClick = { expandedDescription = !expandedDescription }) { Text(if (expandedDescription) "Thu gọn" else "Xem đầy đủ") }
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) { Text("Tài khoản", style = FinanceTheme.typography.caption); Text(transaction.accountOrBank) }
                        Column(Modifier.weight(1f)) { Text("Thời gian", style = FinanceTheme.typography.caption); Text(transaction.formattedDate) }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) { Text("Nguồn", style = FinanceTheme.typography.caption); Text(when (transaction.source) { "MONEYLOVER" -> "Nhập lịch sử"; "MANUAL" -> "Ghi chép thủ công"; else -> "Thông báo ngân hàng" }) }
                        Column(Modifier.weight(1f)) { Text("Trạng thái", style = FinanceTheme.typography.caption); Text(if (transaction.excluded) "Không tính vào báo cáo" else "Tính vào báo cáo") }
                    }
                    if (transaction.isDuplicateCandidate) Text("Có thể trùng giao dịch từ nguồn khác. Cần đối soát.", color = FinanceTheme.colors.expense)
                    if (!editingEnabled) Text("Chưa mở chỉnh sửa trên máy chủ.", style = FinanceTheme.typography.caption)
                    if (editingEnabled) Button(enabled = !opening, onClick = { editRequested = true }, modifier = Modifier.fillMaxWidth()) { Text(if (opening) "Đang tải…" else "Sửa") }
                }
            }
        }
    }
}
