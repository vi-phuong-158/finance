package vn.phuong.financebridge.ui.screens.record

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID
import vn.phuong.financebridge.CategoryMemory
import vn.phuong.financebridge.CategoryMemoryIndex
import vn.phuong.financebridge.ManualDraft
import vn.phuong.financebridge.ManualOutbox
import vn.phuong.financebridge.ManualSyncWorker
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.phuong.financebridge.ui.components.CategorySuggestionCard
import vn.phuong.financebridge.ui.components.FinanceCard
import vn.phuong.financebridge.ui.components.FinanceChip
import vn.phuong.financebridge.ui.components.PrimaryButton
import vn.phuong.financebridge.ui.data.CategoryMemoryRepository
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.theme.FinanceTheme

data class CategoryItem(val name: String, val icon: ImageVector, val color: Color)

enum class ExtendedTransactionType(val label: String, val isEnabled: Boolean) {
    CHI("Chi tiền", true),
    THU("Thu tiền", true),
    CHO_VAY("Cho vay", false),
    DI_VAY("Đi vay", false),
    CHUYEN_KHOAN("Chuyển khoản", false),
    DIEU_CHINH("Điều chỉnh", false),
}

@Composable
fun RecordScreen(
    context: Context,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedType by remember { mutableStateOf(ExtendedTransactionType.CHI) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Ăn uống") }
    var selectedAccount by remember { mutableStateOf("Vietcombank") }
    var requestId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var dateText by rememberSaveable { mutableStateOf(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm"))) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var queueCounts by remember { mutableStateOf(0 to 0) }
    val scope = rememberCoroutineScope()
    val memoryRepository = remember(context) { CategoryMemoryRepository(context) }
    var memory by remember { mutableStateOf<CategoryMemoryIndex?>(null) }
    var categoryPicked by remember { mutableStateOf(false) }
    var autoPicked by remember { mutableStateOf<String?>(null) }
    var hiddenKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        memory = memoryRepository.cached()
        memory = memoryRepository.refresh().index
    }
    LaunchedEffect(Unit) {
        while (true) {
            queueCounts = withContext(Dispatchers.IO) { ManualOutbox(context).use { it.counts() } }
            delay(1500)
        }
    }

    val categories = if (selectedType == ExtendedTransactionType.THU) listOf(
        CategoryItem("Lương", Icons.Rounded.Payments, Color(0xFF28A765)),
        CategoryItem("Thu nhập khác", Icons.Rounded.Add, Color(0xFF42A5F5)),
        CategoryItem("Tiền chuyển đến", Icons.Rounded.AccountBalance, Color(0xFF26A69A)),
    ) else listOf(
        CategoryItem("Ăn uống", Icons.Rounded.Restaurant, Color(0xFFEF5350)),
        CategoryItem("Xăng", Icons.Rounded.LocalGasStation, Color(0xFFFFA726)),
        CategoryItem("Con cái", Icons.Rounded.ChildCare, Color(0xFF42A5F5)),
        CategoryItem("Mua sắm", Icons.Rounded.ShoppingCart, Color(0xFFAB47BC)),
        CategoryItem("Gia đình", Icons.Rounded.Home, Color(0xFF26A69A)),
        CategoryItem("Sức khỏe", Icons.Rounded.MedicalServices, Color(0xFFEC407A)),
        CategoryItem("Giải trí", Icons.Rounded.SportsEsports, Color(0xFF7E57C2)),
        CategoryItem("Các chi phí khác", Icons.Rounded.MoreHoriz, Color(0xFF78909C)),
    )

    val accounts = listOf("Vietcombank", "VietinBank", "BIDV", "Tiền mặt")
    LaunchedEffect(selectedType) { selectedCategory = categories.first().name; categoryPicked = false; autoPicked = null }
    // Manual entries have no counterparty, so the note is the (Tier 3) identity.
    val noteKey = CategoryMemory.descriptionKey(description)
    val suggestion = CategoryMemory.suggest(memory, noteKey, if (selectedType == ExtendedTransactionType.THU) "THU" else "CHI")
        ?.takeIf { noteKey != hiddenKey }
    LaunchedEffect(suggestion?.rule) {
        val (category, auto) = CategoryMemory.preselectForRecord(suggestion, categoryPicked, selectedCategory, autoPicked, categories.first().name)
        selectedCategory = category; autoPicked = auto
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FinanceTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Ghi chép",
            style = FinanceTheme.typography.headlineLarge,
            color = FinanceTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 8.dp),
        )

        // 1. Transaction Types (6 types, Chi & Thu active, 4 coming soon)
        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 14.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExtendedTransactionType.values().take(3).forEach { t ->
                        TypeSelectorChip(
                            type = t,
                            isSelected = selectedType == t,
                            onClick = { if (t.isEnabled) selectedType = t },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExtendedTransactionType.values().drop(3).take(3).forEach { t ->
                        TypeSelectorChip(
                            type = t,
                            isSelected = selectedType == t,
                            onClick = { if (t.isEnabled) selectedType = t },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // 2. Amount Input (Visual Focus, 30-34sp)
        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 20.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Số tiền",
                    style = FinanceTheme.typography.bodyMedium,
                    color = FinanceTheme.colors.textSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter(Char::isDigit) },
                        placeholder = {
                            Text(
                                "0",
                                style = FinanceTheme.typography.balance.copy(fontSize = 32.sp),
                                color = FinanceTheme.colors.textMuted,
                            )
                        },
                        textStyle = FinanceTheme.typography.balance.copy(
                            fontSize = 32.sp,
                            color = if (selectedType == ExtendedTransactionType.THU) FinanceTheme.colors.income else FinanceTheme.colors.expense,
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = FinanceTheme.shapes.medium,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "đ",
                        style = FinanceTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = FinanceTheme.colors.textSecondary,
                    )
                }
            }
        }

        // 3. Category Grid (4 columns, MISA-inspired icon tiles)
        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 16.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Danh mục",
                        style = FinanceTheme.typography.titleMedium,
                        color = FinanceTheme.colors.textPrimary,
                    )
                    Text(
                        text = selectedCategory,
                        style = FinanceTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = FinanceTheme.colors.primary,
                    )
                }

                // 4-column Grid: row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    categories.take(4).forEach { cat ->
                        CategoryTile(
                            item = cat,
                            isSelected = selectedCategory == cat.name,
                            onClick = { selectedCategory = cat.name; categoryPicked = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // 4-column Grid: row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    categories.drop(4).take(4).forEach { cat ->
                        CategoryTile(
                            item = cat,
                            isSelected = selectedCategory == cat.name,
                            onClick = { selectedCategory = cat.name; categoryPicked = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // 4. Payment Account Selector
        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 16.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Tài khoản thanh toán",
                    style = FinanceTheme.typography.titleMedium,
                    color = FinanceTheme.colors.textPrimary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    accounts.forEach { acc ->
                        FinanceChip(
                            text = acc,
                            isSelected = selectedAccount == acc,
                            onClick = { selectedAccount = acc },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // 5. Description & Metadata
        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 16.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Diễn giải / Ghi chú") },
                    placeholder = { Text("Ví dụ: Ăn trưa đồng nghiệp, mua đồ siêu thị...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = FinanceTheme.shapes.medium,
                )
                suggestion?.let { s ->
                    CategorySuggestionCard(s, applied = selectedCategory == s.category,
                        onApply = { selectedCategory = s.category; categoryPicked = true },
                        onDismiss = {
                            if (!categoryPicked && selectedCategory == s.category) selectedCategory = categories.first().name
                            hiddenKey = noteKey
                        })
                }
                OutlinedTextField(dateText, { dateText = it }, label = { Text("Ngày giờ (dd/MM/yyyy HH:mm)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }

        PrimaryButton(
            text = if (saving) "Đang lưu…" else "Lưu giao dịch",
            enabled = !saving,
            onClick = {
                val draft = runCatching {
                    val date = LocalDateTime.parse(dateText, DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT))
                    ManualDraft(requestId, date.format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm")),
                        if (selectedType == ExtendedTransactionType.THU) "THU" else "CHI", amountText.toLong(), selectedCategory,
                        when (selectedAccount) { "Vietcombank" -> "VCB"; "VietinBank" -> "VIETINBANK"; "BIDV" -> "BIDV"; else -> "CASH" }, description).also { it.validate() }
                }.getOrNull()
                if (draft == null) {
                    message = "Kiểm tra số tiền (1 đến 1.000 tỷ đồng), ngày giờ và ghi chú tối đa 1.024 byte."
                } else {
                    saving = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                                ManualOutbox(context).use { it.enqueue(draft) }
                                ManualSyncWorker.schedule(context)
                            }
                            message = "Đã lưu trên máy. Giao dịch sẽ xuất hiện trong sổ sau khi đồng bộ."
                            requestId = UUID.randomUUID().toString()
                            amountText = ""; description = ""; categoryPicked = false; hiddenKey = null
                            onSaved()
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { message = "Chưa lưu được giao dịch. Vui lòng thử lại." }
                        finally { saving = false }
                    }
                }
            },
        )

        message?.let { Text(it, style = FinanceTheme.typography.bodyMedium) }
        Text("${queueCounts.first} thay đổi đang chờ đồng bộ · ${queueCounts.second} cần xử lý", style = FinanceTheme.typography.caption)
        androidx.compose.material3.TextButton(onClick = {
            scope.launch {
                withContext(Dispatchers.IO) { ManualOutbox(context).use { it.retryBlocked() } }
                ManualSyncWorker.schedule(context)
            }
        }) { Text("Gửi lại giao dịch đang chờ") }
        if (queueCounts.second > 0) Text("Xung đột hoặc yêu cầu bị từ chối không tự gửi lại: xem Khác → Thay đổi cần xử lý.",
            style = FinanceTheme.typography.caption, color = FinanceTheme.colors.expense)

        Text(
            text = "Giao dịch được giữ trên máy khi mất mạng. Nếu cần gửi lại, hãy kiểm tra mã kết nối trong Khác. Không nhập lại cùng giao dịch để tránh trùng.",
            style = FinanceTheme.typography.caption,
            color = FinanceTheme.colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(56.dp))
    }
}

@Composable
private fun TypeSelectorChip(
    type: ExtendedTransactionType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = when {
        isSelected -> FinanceTheme.colors.primary
        !type.isEnabled -> FinanceTheme.colors.background
        else -> FinanceTheme.colors.surfaceCard
    }
    val textColor = when {
        isSelected -> Color.White
        !type.isEnabled -> FinanceTheme.colors.textMuted.copy(alpha = 0.5f)
        else -> FinanceTheme.colors.textSecondary
    }
    val borderColor = if (isSelected) FinanceTheme.colors.primary else FinanceTheme.colors.borderGlass

    Box(
        modifier = modifier
            .clip(FinanceTheme.shapes.pill)
            .background(bgColor)
            .border(1.dp, borderColor, FinanceTheme.shapes.pill)
            .clickable(enabled = type.isEnabled, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = type.label,
            style = FinanceTheme.typography.caption.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            ),
            color = textColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CategoryTile(
    item: CategoryItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(FinanceTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(FinanceTheme.shapes.large)
                .background(if (isSelected) item.color.copy(alpha = 0.22f) else item.color.copy(alpha = 0.10f))
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) item.color else Color.Transparent,
                    shape = FinanceTheme.shapes.large,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.name,
                tint = item.color,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = item.name,
            style = FinanceTheme.typography.caption.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (isSelected) FinanceTheme.colors.textPrimary else FinanceTheme.colors.textSecondary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
