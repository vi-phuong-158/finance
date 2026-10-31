package vn.phuong.financebridge.ui.screens.transactions

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.phuong.financebridge.ManualOutbox
import vn.phuong.financebridge.ui.components.*
import vn.phuong.financebridge.ui.data.FinanceApi
import vn.phuong.financebridge.ui.data.parseTransaction
import vn.phuong.financebridge.ui.data.latestTransaction
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.FinancePeriod
import vn.phuong.financebridge.ui.util.FinanceFormatters
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
            val pendingEdits = withContext(Dispatchers.IO) {
                val outbox = ManualOutbox(context)
                try {
                    outbox.activeEdits().associate { edit ->
                        val fields = TransactionOptimisticUpdate.parseEditPayload(edit.payload)
                        edit.targetId to PendingEditState(edit.targetId, edit.state, fields)
                    }
                } finally {
                    outbox.close()
                }
            }
            val overlaid = TransactionOptimisticUpdate.overlayPendingEdits(page.rows, pendingEdits)
            rows = if (cursor == null) overlaid else rows + overlaid
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
        if (loading && rows.isNotEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = FinanceTheme.colors.primary, trackColor = FinanceTheme.colors.primaryContainer)
        } else {
            Spacer(Modifier.height(2.dp))
        }
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
                    FinanceCard(contentPadding = 8.dp) {
                        TransactionRow(
                            transaction = transaction,
                            smartStatus = TransactionSmartStatus.derive(transaction, memory),
                            onClick = { selectedId = transaction.id; selected = transaction; expandedDescription = false },
                        )
                    }
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
                        val applied = editFields ?: TransactionEditFlow.fields(editing!!)
                        rows = TransactionOptimisticUpdate.applyToRows(rows, editing!!.id, applied)
                        editing = null; editFields = null; selected = null; selectedId = null; editRequested = false
                        savedMessage = "Đã lưu trên máy. Đang chờ đồng bộ."
                    })
            } else {
                val categoryColor = CategoryVisuals.color(transaction.category, FinanceTheme.colors)
                val categoryIcon = CategoryVisuals.icon(transaction.category)
                val isIncome = transaction.type == TransactionType.THU

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1a. Phần đầu: icon tròn 48dp, số tiền có dấu autoShrink, danh mục & chip Thu/Chi + trạng thái
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(categoryColor.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = categoryIcon,
                                    contentDescription = transaction.category,
                                    tint = categoryColor,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AmountText(
                                    amountText = amountWithSign(transaction.type, transaction.amount),
                                    type = transaction.type,
                                    style = FinanceTheme.typography.headlineLarge,
                                    autoShrink = true
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = transaction.category,
                                        style = FinanceTheme.typography.titleMedium,
                                        color = FinanceTheme.colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Surface(
                                        shape = FinanceTheme.shapes.pill,
                                        color = if (isIncome) FinanceTheme.colors.income.copy(alpha = 0.12f) else FinanceTheme.colors.expense.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = if (isIncome) "Thu" else "Chi",
                                            style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                                            color = if (isIncome) FinanceTheme.colors.income else FinanceTheme.colors.expense,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (transaction.pendingSync) {
                                        Surface(
                                            shape = FinanceTheme.shapes.pill,
                                            color = FinanceTheme.colors.primaryContainer
                                        ) {
                                            Text(
                                                text = "Đang chờ đồng bộ",
                                                style = FinanceTheme.typography.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                                color = FinanceTheme.colors.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else if (transaction.blocked) {
                                        Surface(
                                            shape = FinanceTheme.shapes.pill,
                                            color = FinanceTheme.colors.warning.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "Cần xử lý",
                                                style = FinanceTheme.typography.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                                color = FinanceTheme.colors.warning,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 1b. Thẻ thông tin bo 16dp: mỗi dòng cao >= 56dp, icon dẫn, divider mảnh, mô tả có "Xem đầy đủ"
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = FinanceTheme.shapes.medium,
                            color = FinanceTheme.colors.surfaceCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, FinanceTheme.colors.borderGlass)
                        ) {
                            Column {
                                DetailInfoItem(
                                    icon = Icons.Rounded.AccountBalanceWallet,
                                    label = "Tài khoản",
                                    value = transaction.accountOrBank
                                )
                                HorizontalDivider(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = FinanceTheme.colors.borderGlass
                                )
                                DetailInfoItem(
                                    icon = Icons.Rounded.CalendarToday,
                                    label = "Thời gian",
                                    value = transaction.formattedDate
                                )
                                HorizontalDivider(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = FinanceTheme.colors.borderGlass
                                )
                                DetailInfoItem(
                                    icon = when (transaction.source) {
                                        "MONEYLOVER" -> Icons.Rounded.History
                                        "MANUAL" -> Icons.Rounded.Edit
                                        else -> Icons.Rounded.Notifications
                                    },
                                    label = "Nguồn",
                                    value = sourceLabel(transaction.source)
                                )
                                HorizontalDivider(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = FinanceTheme.colors.borderGlass
                                )
                                DetailInfoItem(
                                    icon = Icons.Rounded.BarChart,
                                    label = "Báo cáo",
                                    value = reportStatusLabel(transaction.excluded)
                                )
                                if (transaction.title.isNotBlank()) {
                                    HorizontalDivider(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                        thickness = 0.5.dp,
                                        color = FinanceTheme.colors.borderGlass
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 56.dp)
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Description,
                                            contentDescription = null,
                                            tint = FinanceTheme.colors.textSecondary,
                                            modifier = Modifier.size(24.dp).padding(top = 2.dp)
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "Mô tả / Ghi chú",
                                                style = FinanceTheme.typography.caption,
                                                color = FinanceTheme.colors.textSecondary
                                            )
                                            Text(
                                                text = transaction.title,
                                                style = FinanceTheme.typography.bodyMedium,
                                                color = FinanceTheme.colors.textPrimary,
                                                maxLines = if (expandedDescription) Int.MAX_VALUE else 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            TextButton(
                                                onClick = { expandedDescription = !expandedDescription },
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text(
                                                    text = if (expandedDescription) "Thu gọn" else "Xem đầy đủ",
                                                    style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                                                    color = FinanceTheme.colors.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 1c. Cảnh báo trùng & chưa mở chỉnh sửa dạng banner tonal có icon
                        if (transaction.isDuplicateCandidate) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = FinanceTheme.shapes.medium,
                                color = FinanceTheme.colors.warning.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, FinanceTheme.colors.warning.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = null,
                                        tint = FinanceTheme.colors.warning,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Có thể trùng giao dịch từ nguồn khác. Cần đối soát.",
                                        style = FinanceTheme.typography.bodyMedium,
                                        color = if (FinanceTheme.colors.isDark) Color(0xFFFFD580) else Color(0xFF8A5300)
                                    )
                                }
                            }
                        }
                        if (!editingEnabled) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = FinanceTheme.shapes.medium,
                                color = FinanceTheme.colors.info.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, FinanceTheme.colors.info.copy(alpha = 0.25f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = FinanceTheme.colors.info,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Chưa mở chỉnh sửa trên máy chủ.",
                                        style = FinanceTheme.typography.bodyMedium,
                                        color = if (FinanceTheme.colors.isDark) Color(0xFF8CE1BE) else Color(0xFF0F5A40)
                                    )
                                }
                            }
                        }
                    }

                    // 1d. Nút Sửa cố định ở đáy sheet
                    if (editingEnabled) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = FinanceTheme.colors.surface,
                            tonalElevation = 2.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Button(
                                    enabled = !opening,
                                    onClick = { editRequested = true },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = FinanceTheme.shapes.medium
                                ) {
                                    Text(if (opening) "Đang tải…" else "Sửa")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailInfoItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FinanceTheme.colors.textSecondary,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = FinanceTheme.typography.caption,
                color = FinanceTheme.colors.textSecondary,
            )
            Text(
                text = value,
                style = FinanceTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = FinanceTheme.colors.textPrimary,
            )
        }
    }
}

