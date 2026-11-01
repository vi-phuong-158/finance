package vn.phuong.financebridge.ui.screens.reports

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import vn.phuong.financebridge.ui.components.*
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.model.UiPeriodMetric
import vn.phuong.financebridge.ui.screens.dashboard.DashboardRepository
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.*

enum class ReportPeriodMode(val label: String) {
    MONTH("Tháng"),
    YEAR("Năm"),
    CUSTOM("Tùy chọn");
}

/**
 * Redesigned ReportsScreen (PROMPT 6 v2 / §9.3):
 * 1. Period bar: < tháng MM/YYYY > or < năm YYYY > or DateRangePicker modal with month/year/custom chips.
 * 2. Summary cards: Thu / Chi / Ròng, autoShrinking amounts with signs, 2+1 layout at <360dp.
 * 3. SegmentedButton: "Chi tiêu | Thu nhập" switching Donut chart mode.
 * 4. Canvas Donut chart (Top 5 + "Khác", angle hit-test, center summary, legend with CategoryVisuals icons, side-by-side at >=600dp).
 * 5. Tapping category opens CategoryHistoryScreen; tapping "Khác" expands sub-categories.
 * 6. Canvas SpendingChart fitting 344dp width without horizontal scrolling.
 * 7. MetricList for banks and sources according to §2.3.
 * 8. In-memory process cache via ReportCache with thin progress bar for background refreshes and skeletons for initial load.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    context: Context,
    modifier: Modifier = Modifier,
    initialCategory: String? = null,
    initialIsIncome: Boolean = false,
) {
    val repository = remember(context) { DashboardRepository(context) }
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now(VIETNAM_ZONE_ID) }

    // Period selection state preserved across fold/unfold
    var periodMode by rememberSaveable { mutableStateOf(ReportPeriodMode.MONTH) }
    var selectedYear by rememberSaveable { mutableStateOf(today.year) }
    var selectedMonth by rememberSaveable { mutableStateOf(today.monthValue) }

    var customFrom by rememberSaveable { mutableStateOf(today.withDayOfMonth(1).toString()) }
    var customTo by rememberSaveable { mutableStateOf(today.toString()) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    // Type selector: 0 = Chi tiêu, 1 = Thu nhập
    var selectedTypeIndex by rememberSaveable { mutableStateOf(if (initialIsIncome) 1 else 0) }

    // Category history navigation state
    var selectedCategoryForHistory by rememberSaveable { mutableStateOf(initialCategory) }
    var selectedCategoryIsIncome by rememberSaveable { mutableStateOf(initialIsIncome) }

    // Data states
    var data by remember { mutableStateOf<UiDashboard?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Compute active query parameters based on period
    val (resolvedFrom, resolvedTo) = remember(periodMode, selectedYear, selectedMonth, customFrom, customTo) {
        when (periodMode) {
            ReportPeriodMode.MONTH -> {
                val ym = YearMonth.of(selectedYear, selectedMonth)
                ym.atDay(1) to ym.atEndOfMonth()
            }
            ReportPeriodMode.YEAR -> {
                LocalDate.of(selectedYear, 1, 1) to LocalDate.of(selectedYear, 12, 31)
            }
            ReportPeriodMode.CUSTOM -> {
                val f = runCatching { LocalDate.parse(customFrom) }.getOrDefault(today.withDayOfMonth(1))
                val t = runCatching { LocalDate.parse(customTo) }.getOrDefault(today)
                f to t
            }
        }
    }

    val activeQuery = remember(resolvedFrom, resolvedTo) {
        mapOf("from" to resolvedFrom.toString(), "to" to resolvedTo.toString())
    }

    fun loadReport(forceRefresh: Boolean = false) {
        val cacheKey = ReportCache.normalizeQueryKey("dashboard", activeQuery)
        val cached = ReportCache.getDashboard(cacheKey)

        if (!forceRefresh && cached != null) {
            data = cached
            loading = false
        } else {
            loading = true
        }
        error = null

        scope.launch {
            repository.fetchDashboard(activeQuery).onSuccess { freshData ->
                data = freshData
                ReportCache.putDashboard(cacheKey, freshData)
                error = null
            }.onFailure {
                if (data == null) {
                    error = "Không thể tải báo cáo. Vui lòng thử lại."
                }
            }
            loading = false
        }
    }

    LaunchedEffect(activeQuery) {
        loadReport()
    }

    // If viewing category history, render CategoryHistoryScreen with BackHandler
    if (selectedCategoryForHistory != null) {
        CategoryHistoryScreen(
            context = context,
            category = selectedCategoryForHistory!!,
            isIncome = selectedCategoryIsIncome,
            currentPeriodFrom = resolvedFrom,
            currentPeriodTo = resolvedTo,
            defaultPreset = HistoryPeriodPreset.CURRENT,
            onBack = { selectedCategoryForHistory = null }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FinanceTheme.colors.background)
    ) {
        // Thin top progress bar for background refreshes
        if (loading && data != null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = FinanceTheme.colors.primary,
                trackColor = Color.Transparent
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen Header & Period Navigator
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Báo cáo tài chính",
                    style = FinanceTheme.typography.headlineLarge
                )

                // Period mode selection chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReportPeriodMode.values().forEach { mode ->
                        val isSelected = periodMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                periodMode = mode
                                if (mode == ReportPeriodMode.CUSTOM) {
                                    showDateRangePicker = true
                                }
                            },
                            label = { Text(mode.label, style = FinanceTheme.typography.caption) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FinanceTheme.colors.primary,
                                selectedLabelColor = FinanceTheme.colors.onPrimary
                            )
                        )
                    }
                }

                // Stepper bar (< Month/Year/Custom >)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FinanceTheme.shapes.medium,
                    color = FinanceTheme.colors.surfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, FinanceTheme.colors.borderGlass)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (periodMode) {
                            ReportPeriodMode.MONTH -> {
                                IconButton(
                                    onClick = {
                                        if (selectedMonth == 1) {
                                            selectedMonth = 12
                                            selectedYear -= 1
                                        } else {
                                            selectedMonth -= 1
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                        contentDescription = "Tháng trước"
                                    )
                                }
                                Text(
                                    text = "Tháng ${String.format("%02d", selectedMonth)}/$selectedYear",
                                    style = FinanceTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = FinanceTheme.colors.textPrimary
                                )
                                IconButton(
                                    onClick = {
                                        if (selectedMonth == 12) {
                                            selectedMonth = 1
                                            selectedYear += 1
                                        } else {
                                            selectedMonth += 1
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = "Tháng sau"
                                    )
                                }
                            }
                            ReportPeriodMode.YEAR -> {
                                IconButton(onClick = { selectedYear -= 1 }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                        contentDescription = "Năm trước"
                                    )
                                }
                                Text(
                                    text = "Năm $selectedYear",
                                    style = FinanceTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = FinanceTheme.colors.textPrimary
                                )
                                IconButton(onClick = { selectedYear += 1 }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = "Năm sau"
                                    )
                                }
                            }
                            ReportPeriodMode.CUSTOM -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CalendarToday,
                                            contentDescription = null,
                                            tint = FinanceTheme.colors.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "${FinanceFormatters.formatIsoDate(resolvedFrom.toString())} – ${FinanceFormatters.formatIsoDate(resolvedTo.toString())}",
                                            style = FinanceTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = FinanceTheme.colors.textPrimary
                                        )
                                    }
                                    TextButton(
                                        onClick = { showDateRangePicker = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text("Thay đổi", style = FinanceTheme.typography.caption)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Body: Loading / Error / Content
            when {
                loading && data == null -> {
                    ReportSkeleton()
                }
                error != null && data == null -> {
                    ErrorState(message = error!!, onRetry = { loadReport(forceRefresh = true) })
                }
                data != null -> {
                    val dashboard = data!!

                    // Duplicate warning banner if any
                    if (dashboard.possibleDuplicateCount > 0) {
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
                                    text = "Có ${dashboard.possibleDuplicateCount} giao dịch có thể trùng. Cần đối soát.",
                                    style = FinanceTheme.typography.caption,
                                    color = if (FinanceTheme.colors.isDark) Color(0xFFFFD580) else Color(0xFF8A5300)
                                )
                            }
                        }
                    }

                    // 1b. Summary Cards: Thu / Chi / Ròng
                    ReportSummaryCard(dashboard = dashboard)

                    // 1c. Segmented Button: Chi tiêu | Thu nhập
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedTypeIndex == 0,
                            onClick = { selectedTypeIndex = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Chi tiêu (${dashboard.categories.size})")
                        }
                        SegmentedButton(
                            selected = selectedTypeIndex == 1,
                            onClick = { selectedTypeIndex = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Thu nhập (${dashboard.incomeCategories.size})")
                        }
                    }

                    // 1d & 1e. Donut Chart & Legend
                    val donutSlices = remember(dashboard, selectedTypeIndex) {
                        if (selectedTypeIndex == 0) {
                            DonutSlices.calculate(dashboard.categories.map { CategoryShareInput(it.name, it.amount, it.transactionCount) })
                        } else {
                            DonutSlices.calculate(dashboard.incomeCategories.map { CategoryShareInput(it.name, it.income, it.count) })
                        }
                    }

                    DonutChart(
                        slices = donutSlices,
                        isIncome = selectedTypeIndex == 1,
                        onCategoryClick = { clickedCategory ->
                            selectedCategoryForHistory = clickedCategory
                            selectedCategoryIsIncome = (selectedTypeIndex == 1)
                        }
                    )

                    // 1f. Canvas SpendingChart
                    if (dashboard.hasReportBreakdowns) {
                        SpendingChart(
                            daily = dashboard.daily,
                            fromDate = resolvedFrom,
                            toDate = resolvedTo
                        )
                    } else {
                        FinanceCard(contentPadding = 16.dp) {
                            Text(
                                text = "Máy chủ chưa cung cấp báo cáo theo ngày, ngân hàng và nguồn.",
                                style = FinanceTheme.typography.caption,
                                color = FinanceTheme.colors.textMuted
                            )
                        }
                    }

                    // MetricList: Banks & Sources formatted per §2.3
                    if (dashboard.hasReportBreakdowns) {
                        ReportMetricList(title = "Theo ngân hàng & ví", rows = dashboard.banks)
                        ReportMetricList(title = "Theo nguồn ghi nhận", rows = dashboard.sources)
                    }
                }
            }

            Spacer(Modifier.height(56.dp))
        }
    }

    // DateRangePicker Dialog for Custom Period
    if (showDateRangePicker) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMillis = dateRangePickerState.selectedStartDateMillis
                        val endMillis = dateRangePickerState.selectedEndDateMillis
                        if (startMillis != null && endMillis != null) {
                            val start = Instant.ofEpochMilli(startMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                            val end = Instant.ofEpochMilli(endMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                            customFrom = start.toString()
                            customTo = end.toString()
                        }
                        showDateRangePicker = false
                    },
                    enabled = dateRangePickerState.selectedStartDateMillis != null && dateRangePickerState.selectedEndDateMillis != null
                ) {
                    Text("Áp dụng")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text("Hủy")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = {
                    Text(
                        text = "Chọn khoảng thời gian",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp),
                        style = FinanceTheme.typography.titleMedium
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Summary card for Thu / Chi / Ròng:
 * - Adapts to screen width: at <360dp uses 2+1 layout (Thu and Chi on top row, Ròng below).
 * - At >=360dp uses 3 equal columns.
 * - Always includes +/− signs and uses AmountText with autoShrink.
 */
@Composable
private fun ReportSummaryCard(dashboard: UiDashboard) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isNarrow = maxWidth < 360.dp

        FinanceCard(contentPadding = 16.dp) {
            if (isNarrow) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummaryItem(
                            label = "Tổng thu",
                            amount = dashboard.monthlyIncome,
                            type = TransactionType.THU,
                            icon = Icons.Rounded.ArrowDownward,
                            iconColor = FinanceTheme.colors.income,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryItem(
                            label = "Tổng chi",
                            amount = dashboard.monthlyExpense,
                            type = TransactionType.CHI,
                            icon = Icons.Rounded.ArrowUpward,
                            iconColor = FinanceTheme.colors.expense,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = FinanceTheme.colors.borderGlass)

                    SummaryItem(
                        label = "Dòng tiền ròng",
                        amount = dashboard.netDiff,
                        type = if (dashboard.netDiff >= 0) TransactionType.THU else TransactionType.CHI,
                        icon = Icons.Rounded.AccountBalanceWallet,
                        iconColor = if (dashboard.netDiff >= 0) FinanceTheme.colors.primary else FinanceTheme.colors.expense,
                        isProminent = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SummaryItem(
                        label = "Tổng thu",
                        amount = dashboard.monthlyIncome,
                        type = TransactionType.THU,
                        icon = Icons.Rounded.ArrowDownward,
                        iconColor = FinanceTheme.colors.income,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryItem(
                        label = "Tổng chi",
                        amount = dashboard.monthlyExpense,
                        type = TransactionType.CHI,
                        icon = Icons.Rounded.ArrowUpward,
                        iconColor = FinanceTheme.colors.expense,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryItem(
                        label = "Dòng tiền ròng",
                        amount = dashboard.netDiff,
                        type = if (dashboard.netDiff >= 0) TransactionType.THU else TransactionType.CHI,
                        icon = Icons.Rounded.AccountBalanceWallet,
                        iconColor = if (dashboard.netDiff >= 0) FinanceTheme.colors.primary else FinanceTheme.colors.expense,
                        isProminent = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    amount: Long,
    type: TransactionType,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    isProminent: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = FinanceTheme.typography.caption,
                color = FinanceTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val formatted = FinanceFormatters.formatVnd(amount, includeSign = true, isIncome = (type == TransactionType.THU))
        AmountText(
            amountText = formatted,
            type = type,
            style = if (isProminent) FinanceTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold) else FinanceTheme.typography.titleMedium,
            autoShrink = true
        )
    }
}

/**
 * Metric list for banks and sources according to UI V2 HANDOFF §2.3:
 * Single row with label ↔ amount, tab-separated, no stacked plain text.
 */
@Composable
private fun ReportMetricList(title: String, rows: List<UiPeriodMetric>) {
    FinanceCard(contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(title = title)
            if (rows.isEmpty()) {
                Text(
                    text = "Chưa có dữ liệu trong kỳ này.",
                    style = FinanceTheme.typography.caption,
                    color = FinanceTheme.colors.textMuted
                )
            } else {
                rows.forEachIndexed { idx, row ->
                    if (idx > 0) {
                        HorizontalDivider(thickness = 0.5.dp, color = FinanceTheme.colors.borderGlass)
                    }
                    val label = when (row.name) {
                        "BANK_NOTIFICATION" -> "Thông báo ngân hàng"
                        "MONEYLOVER" -> "Nhập lịch sử"
                        "MANUAL" -> "Ghi chép thủ công"
                        else -> row.name
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = label,
                            style = FinanceTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = FinanceTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (row.expense > 0) {
                                AmountText(
                                    amountText = FinanceFormatters.formatVnd(row.expense, includeSign = true, isIncome = false),
                                    type = TransactionType.CHI,
                                    style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                                    autoShrink = true
                                )
                            }
                            if (row.income > 0) {
                                AmountText(
                                    amountText = FinanceFormatters.formatVnd(row.income, includeSign = true, isIncome = true),
                                    type = TransactionType.THU,
                                    style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                                    autoShrink = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Skeleton placeholder matching the report cards during initial load (P6).
 */
@Composable
private fun ReportSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Summary skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(FinanceTheme.shapes.large)
                .background(FinanceTheme.colors.surfaceCard)
        )
        // Donut skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(FinanceTheme.shapes.large)
                .background(FinanceTheme.colors.surfaceCard)
        )
        // Trend chart skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(FinanceTheme.shapes.large)
                .background(FinanceTheme.colors.surfaceCard)
        )
    }
}
