package vn.phuong.financebridge.ui.screens.reports

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch
import vn.phuong.financebridge.ui.components.*
import vn.phuong.financebridge.ui.data.FinanceApi
import vn.phuong.financebridge.ui.data.parseTransaction
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.screens.dashboard.DashboardRepository
import vn.phuong.financebridge.ui.screens.transactions.DetailInfoItem
import vn.phuong.financebridge.ui.screens.transactions.TransactionSaver
import vn.phuong.financebridge.ui.screens.transactions.amountWithSign
import vn.phuong.financebridge.ui.screens.transactions.reportStatusLabel
import vn.phuong.financebridge.ui.screens.transactions.sourceLabel
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.*

/**
 * Category History Screen (PROMPT 6 v2):
 * - Navigated via state in ReportsScreen (or TransactionsScreen) with BackHandler.
 * - Header: category icon, name, Thu/Chi chip, back button.
 * - Period chips: "Kỳ đang xem" (default), "3 tháng", "6 tháng", "12 tháng", "Tùy chọn" (DateRangePicker).
 * - Key metrics: total, transaction count, monthly/daily average, max transaction, comparison vs previous period.
 * - Canvas bar chart: <= 31 days is daily, > 31 days is monthly (aggregated on client). Tapping a bar filters the list. Fits 344dp.
 * - Transactions grouped by date with day total, cursor pagination, reusable detail sheet on item click.
 * - Memory cached via ReportCache.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryHistoryScreen(
    context: Context,
    category: String,
    isIncome: Boolean,
    modifier: Modifier = Modifier,
    currentPeriodFrom: LocalDate? = null,
    currentPeriodTo: LocalDate? = null,
    defaultPreset: HistoryPeriodPreset = HistoryPeriodPreset.CURRENT,
    onBack: () -> Unit = {},
) {
    BackHandler(onBack = onBack)

    val api = remember(context) { FinanceApi(context) }
    val repository = remember(context) { DashboardRepository(context) }
    val scope = rememberCoroutineScope()

    var preset by rememberSaveable { mutableStateOf(defaultPreset) }
    val anchorDate = remember { LocalDate.now(VIETNAM_ZONE_ID) }

    var fromDate by rememberSaveable {
        val initialPair = ReportPeriodHelper.calculatePresetDates(defaultPreset, anchorDate, currentPeriodFrom, currentPeriodTo)
        mutableStateOf(initialPair.first.toString())
    }
    var toDate by rememberSaveable {
        val initialPair = ReportPeriodHelper.calculatePresetDates(defaultPreset, anchorDate, currentPeriodFrom, currentPeriodTo)
        mutableStateOf(initialPair.second.toString())
    }

    var showDateRangePicker by remember { mutableStateOf(false) }
    var selectedBarKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTransaction by rememberSaveable(stateSaver = TransactionSaver) { mutableStateOf<UiTransaction?>(null) }

    var dashboardData by remember { mutableStateOf<UiDashboard?>(null) }
    var prevDashboardData by remember { mutableStateOf<UiDashboard?>(null) }
    var transactions by remember { mutableStateOf<List<UiTransaction>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }

    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val resolvedFrom = remember(fromDate) { runCatching { LocalDate.parse(fromDate) }.getOrDefault(LocalDate.now(VIETNAM_ZONE_ID)) }
    val resolvedTo = remember(toDate) { runCatching { LocalDate.parse(toDate) }.getOrDefault(LocalDate.now(VIETNAM_ZONE_ID)) }

    val categoryColor = CategoryVisuals.color(category, FinanceTheme.colors)
    val categoryIcon = CategoryVisuals.icon(category)

    fun loadData(isRefresh: Boolean = false) {
        val typeStr = if (isIncome) "THU" else "CHI"
        val queryMap = mapOf(
            "category" to category,
            "type" to typeStr,
            "from" to fromDate,
            "to" to toDate
        )
        val (prevFrom, prevTo) = ReportPeriodHelper.calculatePreviousPeriod(resolvedFrom, resolvedTo)
        val prevQueryMap = mapOf(
            "category" to category,
            "type" to typeStr,
            "from" to prevFrom.toString(),
            "to" to prevTo.toString()
        )
        val cacheKey = ReportCache.normalizeQueryKey("category-history", queryMap)
        val prevCacheKey = ReportCache.normalizeQueryKey("category-history-prev", prevQueryMap)

        val cachedDashboard = ReportCache.getDashboard(cacheKey)
        val cachedPrevDashboard = ReportCache.getDashboard(prevCacheKey)
        val cachedTransactions = ReportCache.getTransactions(cacheKey)
        val cachedNextCursor = ReportCache.getNextCursor(cacheKey)

        if (!isRefresh && cachedDashboard != null && cachedTransactions != null) {
            dashboardData = cachedDashboard
            prevDashboardData = cachedPrevDashboard
            transactions = cachedTransactions
            nextCursor = cachedNextCursor
            loading = false
        } else {
            loading = true
        }
        error = null

        scope.launch {
            try {
                // 1. Fetch current dashboard for this category
                val dashResult = repository.fetchDashboard(queryMap)
                dashResult.onSuccess { dash ->
                    dashboardData = dash
                    ReportCache.putDashboard(cacheKey, dash)
                }

                // 2. Fetch previous period dashboard for comparison
                repository.fetchDashboard(prevQueryMap).onSuccess { prevDash ->
                    prevDashboardData = prevDash
                    ReportCache.putDashboard(prevCacheKey, prevDash)
                }

                // 3. Fetch transactions list for this category
                api.read("transactions", queryMap + mapOf("limit" to "100")) { json ->
                    val array = json.getJSONArray("transactions")
                    val list = (0 until array.length()).map { parseTransaction(array.getJSONObject(it)) }
                    val cursor = if (json.isNull("nextCursor")) null else json.optString("nextCursor")
                    list to cursor
                }.onSuccess { (list, cursor) ->
                    transactions = list
                    nextCursor = cursor
                    ReportCache.putTransactions(cacheKey, list, cursor)
                }.onFailure {
                    if (dashboardData == null) error = "Không tải được danh sách giao dịch."
                }
            } catch (e: Exception) {
                if (dashboardData == null) error = "Không tải được dữ liệu lịch sử."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(category, fromDate, toDate, isIncome) {
        selectedBarKey = null
        loadData()
    }

    val totalAmount = remember(dashboardData, isIncome) {
        if (isIncome) dashboardData?.monthlyIncome ?: 0L else dashboardData?.monthlyExpense ?: 0L
    }
    val previousTotalAmount = remember(prevDashboardData, isIncome) {
        if (isIncome) prevDashboardData?.monthlyIncome ?: 0L else prevDashboardData?.monthlyExpense ?: 0L
    }

    val daysCount = remember(resolvedFrom, resolvedTo) {
        (ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) + 1).coerceAtLeast(1)
    }
    val monthsCount = remember(resolvedFrom, resolvedTo) {
        (ChronoUnit.MONTHS.between(YearMonth.from(resolvedFrom), YearMonth.from(resolvedTo)) + 1).coerceAtLeast(1)
    }

    val comparison = remember(totalAmount, previousTotalAmount) {
        ReportPeriodHelper.calculateComparison(totalAmount, previousTotalAmount)
    }

    // Chart bars: daily if <= 31 days, monthly if > 31 days
    val chartBars = remember(dashboardData, resolvedFrom, resolvedTo, daysCount, isIncome) {
        val daily = dashboardData?.daily ?: emptyList()
        if (daysCount <= 31) {
            ReportPeriodHelper.aggregateDailyToDaily(daily, resolvedFrom, resolvedTo, isIncome = isIncome)
        } else {
            ReportPeriodHelper.aggregateDailyToMonthly(daily, resolvedFrom, resolvedTo, isIncome = isIncome)
        }
    }

    // Filter transactions list if a bar is selected
    val displayedTransactions = remember(transactions, selectedBarKey) {
        if (selectedBarKey == null) {
            transactions
        } else {
            transactions.filter { tx ->
                ReportPeriodHelper.matchesBarFilter(tx.timestamp, selectedBarKey!!)
            }
        }
    }

    // Group displayed transactions by day
    val groupedTransactions = remember(displayedTransactions) {
        displayedTransactions.groupBy { it.formattedDate }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FinanceTheme.colors.background)
    ) {
        if (loading && dashboardData != null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = FinanceTheme.colors.primary,
                trackColor = Color.Transparent
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = FinanceTheme.colors.textPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(categoryColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = category,
                            tint = categoryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = category,
                            style = FinanceTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = FinanceTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Lịch sử giao dịch",
                            style = FinanceTheme.typography.caption,
                            color = FinanceTheme.colors.textSecondary
                        )
                    }

                    Surface(
                        shape = FinanceTheme.shapes.pill,
                        color = if (isIncome) FinanceTheme.colors.income.copy(alpha = 0.12f) else FinanceTheme.colors.expense.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (isIncome) "Thu nhập" else "Chi tiêu",
                            style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isIncome) FinanceTheme.colors.income else FinanceTheme.colors.expense,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Period Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistoryPeriodPreset.values().forEach { p ->
                        val isSelected = preset == p
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                preset = p
                                if (p == HistoryPeriodPreset.CUSTOM) {
                                    showDateRangePicker = true
                                } else {
                                    val (newFrom, newTo) = ReportPeriodHelper.calculatePresetDates(
                                        p,
                                        anchorDate,
                                        currentPeriodFrom,
                                        currentPeriodTo
                                    )
                                    fromDate = newFrom.toString()
                                    toDate = newTo.toString()
                                }
                            },
                            label = {
                                Text(
                                    text = p.label,
                                    style = FinanceTheme.typography.caption.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FinanceTheme.colors.primary,
                                selectedLabelColor = FinanceTheme.colors.onPrimary
                            )
                        )
                    }
                }
            }

            // Range label
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${FinanceFormatters.formatIsoDate(fromDate)} – ${FinanceFormatters.formatIsoDate(toDate)}",
                        style = FinanceTheme.typography.caption,
                        color = FinanceTheme.colors.textSecondary
                    )
                    if (preset == HistoryPeriodPreset.CUSTOM) {
                        TextButton(
                            onClick = { showDateRangePicker = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Đổi khoảng ngày", style = FinanceTheme.typography.caption)
                        }
                    }
                }
            }

            // Loading / Error / Overview Stats
            if (loading && dashboardData == null) {
                item {
                    LoadingState()
                }
            } else if (error != null && dashboardData == null) {
                item {
                    ErrorState(message = error!!, onRetry = { loadData(isRefresh = true) })
                }
            } else {
                // Overview Stats Card
                item {
                    FinanceCard(contentPadding = 16.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Tổng ${if (isIncome) "thu" else "chi"}",
                                        style = FinanceTheme.typography.caption,
                                        color = FinanceTheme.colors.textSecondary
                                    )
                                    AmountText(
                                        amountText = FinanceFormatters.formatVnd(totalAmount, includeSign = false),
                                        type = if (isIncome) TransactionType.THU else TransactionType.CHI,
                                        style = FinanceTheme.typography.headlineLarge,
                                        autoShrink = true
                                    )
                                }
                                Surface(
                                    shape = FinanceTheme.shapes.small,
                                    color = FinanceTheme.colors.surfaceCard
                                ) {
                                    Text(
                                        text = "${dashboardData?.totalTransactionsCount ?: transactions.size} giao dịch",
                                        style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = FinanceTheme.colors.textSecondary
                                    )
                                }
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = FinanceTheme.colors.borderGlass)

                            // Average & Max transaction
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val averageText = if (monthsCount >= 2) {
                                    val avg = totalAmount / monthsCount
                                    "TB ~${FinanceFormatters.formatVnd(avg, includeSign = false)}/tháng"
                                } else {
                                    val avg = totalAmount / daysCount
                                    "TB ~${FinanceFormatters.formatVnd(avg, includeSign = false)}/ngày"
                                }
                                Text(
                                    text = averageText,
                                    style = FinanceTheme.typography.caption,
                                    color = FinanceTheme.colors.textPrimary
                                )

                                val maxTx = transactions
                                    .filter { !it.excluded && it.category != "Chuyển nội bộ" }
                                    .maxOfOrNull { it.amount } ?: 0L
                                if (maxTx > 0) {
                                    Text(
                                        text = "Lớn nhất: ${FinanceFormatters.formatVnd(maxTx, includeSign = false)}",
                                        style = FinanceTheme.typography.caption,
                                        color = FinanceTheme.colors.textSecondary
                                    )
                                }
                            }

                            // Comparison vs previous period
                            Surface(
                                shape = FinanceTheme.shapes.small,
                                color = when {
                                    comparison.percentChange == null -> FinanceTheme.colors.surfaceCard
                                    comparison.isHigher -> (if (isIncome) FinanceTheme.colors.income else FinanceTheme.colors.expense).copy(alpha = 0.1f)
                                    else -> (if (isIncome) FinanceTheme.colors.expense else FinanceTheme.colors.income).copy(alpha = 0.1f)
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = comparison.formattedSummary,
                                        style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                                        color = when {
                                            comparison.percentChange == null -> FinanceTheme.colors.textMuted
                                            comparison.isHigher -> if (isIncome) FinanceTheme.colors.income else FinanceTheme.colors.expense
                                            else -> if (isIncome) FinanceTheme.colors.expense else FinanceTheme.colors.income
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Interactive Bar Chart Canvas (<=31 days: daily; >31 days: monthly)
                if (chartBars.isNotEmpty() && chartBars.any { it.amount > 0 }) {
                    item {
                        FinanceCard(contentPadding = 16.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (daysCount <= 31) "Xu hướng theo ngày" else "Xu hướng theo tháng",
                                        style = FinanceTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Chạm cột để lọc",
                                        style = FinanceTheme.typography.caption,
                                        color = FinanceTheme.colors.textMuted
                                    )
                                }

                                val maxBarAmount = chartBars.maxOf { it.amount }.coerceAtLeast(1L)
                                val primary = categoryColor
                                val mutedBar = if (FinanceTheme.colors.isDark) Color(0xFF2C322E) else Color(0xFFE2E8E4)

                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .pointerInput(chartBars) {
                                            detectTapGestures { offset ->
                                                val slotWidth = size.width / chartBars.size
                                                val tappedIdx = (offset.x / slotWidth).toInt().coerceIn(0, chartBars.size - 1)
                                                val tappedBar = chartBars[tappedIdx]
                                                selectedBarKey = if (selectedBarKey == tappedBar.key) null else tappedBar.key
                                            }
                                        }
                                ) {
                                    val availWidth = size.width
                                    val availHeight = size.height - 18.dp.toPx()
                                    val slotWidth = availWidth / chartBars.size
                                    val barWidth = (slotWidth * 0.68f).coerceIn(2.dp.toPx(), 16.dp.toPx())

                                    for ((idx, bar) in chartBars.withIndex()) {
                                        val isSelected = bar.key == selectedBarKey
                                        val barHeight = if (bar.amount > 0) {
                                            ((bar.amount.toFloat() / maxBarAmount) * availHeight).coerceAtLeast(3.dp.toPx())
                                        } else {
                                            1.5.dp.toPx()
                                        }

                                        val x = idx * slotWidth + (slotWidth - barWidth) / 2f
                                        val y = availHeight - barHeight

                                        val color = when {
                                            isSelected -> primary
                                            selectedBarKey != null -> primary.copy(alpha = 0.35f)
                                            bar.amount > 0 -> primary.copy(alpha = 0.85f)
                                            else -> mutedBar
                                        }

                                        drawRoundRect(
                                            color = color,
                                            topLeft = Offset(x, y),
                                            size = Size(barWidth, barHeight),
                                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                                        )
                                    }
                                }

                                // Minimalist labels: start, middle (if >=5), end
                                if (chartBars.size >= 2) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = chartBars.first().label,
                                            style = FinanceTheme.typography.caption.copy(fontSize = 10.sp),
                                            color = FinanceTheme.colors.textMuted
                                        )
                                        if (chartBars.size >= 5) {
                                            Text(
                                                text = chartBars[chartBars.size / 2].label,
                                                style = FinanceTheme.typography.caption.copy(fontSize = 10.sp),
                                                color = FinanceTheme.colors.textMuted,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        Text(
                                            text = chartBars.last().label,
                                            style = FinanceTheme.typography.caption.copy(fontSize = 10.sp),
                                            color = FinanceTheme.colors.textMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Filter Active Banner
                if (selectedBarKey != null) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = FinanceTheme.shapes.small,
                            color = FinanceTheme.colors.primaryContainer.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Đang lọc: $selectedBarKey (${displayedTransactions.size} giao dịch)",
                                    style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                                    color = FinanceTheme.colors.primary
                                )
                                TextButton(
                                    onClick = { selectedBarKey = null },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("Bỏ lọc", style = FinanceTheme.typography.caption)
                                }
                            }
                        }
                    }
                }

                // Transaction list section header
                item {
                    SectionHeader(
                        title = "Danh sách giao dịch",
                        actionLabel = if (displayedTransactions.isNotEmpty()) "${displayedTransactions.size} giao dịch" else null
                    )
                }

                // Empty state if no transactions
                if (displayedTransactions.isEmpty()) {
                    item {
                        EmptyState(
                            title = "Chưa có giao dịch",
                            subtitle = if (selectedBarKey != null) "Không có giao dịch trong mốc $selectedBarKey." else "Không có giao dịch $category trong khoảng thời gian này."
                        )
                    }
                } else {
                    // Grouped transactions by date
                    groupedTransactions.forEach { (dateHeader, dayTransactions) ->
                        val dayTotal = dayTransactions.filter { !it.excluded && it.category != "Chuyển nội bộ" }.sumOf { it.amount }

                        item(key = "header-$dateHeader") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dateHeader,
                                    style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                                    color = FinanceTheme.colors.textSecondary
                                )
                                if (dayTotal > 0) {
                                    Text(
                                        text = "${if (isIncome) "+" else "-"}${FinanceFormatters.formatVnd(dayTotal, includeSign = false)}",
                                        style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                                        color = if (isIncome) FinanceTheme.colors.income else FinanceTheme.colors.expense
                                    )
                                }
                            }
                        }

                        items(dayTransactions, key = { it.id }) { tx ->
                            val isNotCounted = tx.excluded || tx.category == "Chuyển nội bộ"
                            FinanceCard(contentPadding = 8.dp) {
                                Column {
                                    TransactionRow(
                                        transaction = tx,
                                        smartStatus = null,
                                        onClick = { selectedTransaction = tx }
                                    )
                                    if (isNotCounted) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 62.dp, bottom = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = FinanceTheme.shapes.pill,
                                                color = FinanceTheme.colors.textMuted.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = "Không tính",
                                                    style = FinanceTheme.typography.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                                    color = FinanceTheme.colors.textSecondary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Cursor pagination "Xem thêm"
                    if (nextCursor != null && selectedBarKey == null) {
                        item {
                            SecondaryButton(
                                text = if (loadingMore) "Đang tải…" else "Xem thêm giao dịch",
                                enabled = !loadingMore,
                                onClick = {
                                    loadingMore = true
                                    scope.launch {
                                        val typeStr = if (isIncome) "THU" else "CHI"
                                        val queryMap = mapOf(
                                            "category" to category,
                                            "type" to typeStr,
                                            "from" to fromDate,
                                            "to" to toDate,
                                            "cursor" to nextCursor!!,
                                            "limit" to "100"
                                        )
                                        api.read("transactions", queryMap) { json ->
                                            val array = json.getJSONArray("transactions")
                                            val list = (0 until array.length()).map { parseTransaction(array.getJSONObject(it)) }
                                            val cursor = if (json.isNull("nextCursor")) null else json.optString("nextCursor")
                                            list to cursor
                                        }.onSuccess { (moreList, cursor) ->
                                            transactions = transactions + moreList
                                            nextCursor = cursor
                                        }
                                        loadingMore = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // DateRangePicker Dialog
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
                            fromDate = start.toString()
                            toDate = end.toString()
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

    // Reusable Transaction Detail BottomSheet
    selectedTransaction?.let { tx ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { selectedTransaction = null }
        ) {
            val txColor = CategoryVisuals.color(tx.category, FinanceTheme.colors)
            val txIcon = CategoryVisuals.icon(tx.category)
            val isTxIncome = tx.type == TransactionType.THU
            var expandedDesc by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header of sheet
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(txColor.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = txIcon,
                                contentDescription = tx.category,
                                tint = txColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AmountText(
                                amountText = amountWithSign(tx.type, tx.amount),
                                type = tx.type,
                                style = FinanceTheme.typography.headlineLarge,
                                autoShrink = true
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tx.category,
                                    style = FinanceTheme.typography.titleMedium,
                                    color = FinanceTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    shape = FinanceTheme.shapes.pill,
                                    color = if (isTxIncome) FinanceTheme.colors.income.copy(alpha = 0.12f) else FinanceTheme.colors.expense.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = if (isTxIncome) "Thu" else "Chi",
                                        style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                                        color = if (isTxIncome) FinanceTheme.colors.income else FinanceTheme.colors.expense,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Card of details
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = FinanceTheme.shapes.medium,
                        color = FinanceTheme.colors.surfaceCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, FinanceTheme.colors.borderGlass)
                    ) {
                        Column {
                            DetailInfoItem(
                                icon = androidx.compose.material.icons.Icons.Rounded.AccountBalanceWallet,
                                label = "Tài khoản",
                                value = tx.accountOrBank
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = FinanceTheme.colors.borderGlass)
                            DetailInfoItem(
                                icon = Icons.Rounded.CalendarToday,
                                label = "Thời gian",
                                value = tx.formattedDate
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = FinanceTheme.colors.borderGlass)
                            DetailInfoItem(
                                icon = when (tx.source) {
                                    "MONEYLOVER" -> androidx.compose.material.icons.Icons.Rounded.History
                                    "MANUAL" -> androidx.compose.material.icons.Icons.Rounded.Edit
                                    else -> androidx.compose.material.icons.Icons.Rounded.Notifications
                                },
                                label = "Nguồn",
                                value = sourceLabel(tx.source)
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = FinanceTheme.colors.borderGlass)
                            DetailInfoItem(
                                icon = androidx.compose.material.icons.Icons.Rounded.BarChart,
                                label = "Báo cáo",
                                value = reportStatusLabel(tx.excluded)
                            )

                            if (tx.title.isNotBlank()) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = FinanceTheme.colors.borderGlass)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 56.dp)
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Rounded.Description,
                                        contentDescription = null,
                                        tint = FinanceTheme.colors.textSecondary,
                                        modifier = Modifier.size(24.dp).padding(top = 2.dp)
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(text = "Mô tả / Ghi chú", style = FinanceTheme.typography.caption, color = FinanceTheme.colors.textSecondary)
                                        Text(
                                            text = tx.title,
                                            style = FinanceTheme.typography.bodyMedium,
                                            color = FinanceTheme.colors.textPrimary,
                                            maxLines = if (expandedDesc) Int.MAX_VALUE else 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        TextButton(
                                            onClick = { expandedDesc = !expandedDesc },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(
                                                text = if (expandedDesc) "Thu gọn" else "Xem đầy đủ",
                                                style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                                                color = FinanceTheme.colors.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
