package vn.phuong.financebridge.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.ZoneId
import vn.phuong.financebridge.ui.components.*
import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.WindowWidthTier
import vn.phuong.financebridge.ui.util.adaptiveContentWidth
import vn.phuong.financebridge.ui.util.rememberWindowWidthTier

@Composable
fun DashboardScreen(
    dashboardState: UiDashboard?,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onViewAllTransactions: () -> Unit,
    onTransactionClick: (String) -> Unit = {},
    onQuickRecord: (isIncome: Boolean) -> Unit = {},
    onOpenAccounts: () -> Unit = {},
    onOpenInterest: () -> Unit = {},
    onOpenMemory: () -> Unit = {},
    onOpenSync: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (isLoading && dashboardState == null) {
        LoadingState(modifier = modifier)
        return
    }

    if (errorMessage != null && dashboardState == null) {
        ErrorState(
            message = "Không thể tải dữ liệu giao dịch. Vui lòng kiểm tra kết nối mạng và thử lại.",
            onRetry = onRefresh,
            modifier = modifier,
        )
        return
    }

    val data = dashboardState ?: run {
        EmptyState(
            title = "Chưa có dữ liệu",
            subtitle = "Nhấn làm mới để tải dữ liệu thu chi từ máy chủ.",
            modifier = modifier,
        )
        return
    }

    val windowTier = rememberWindowWidthTier()
    val isNarrow = windowTier == WindowWidthTier.NARROW_FOLD
    val horizontalPadding = if (isNarrow) 12.dp else 16.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FinanceTheme.colors.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .adaptiveContentWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Header: Lời chào thân thiện + Tên thương hiệu "Vigil Finance" + Icon Đồng bộ (>=48dp touch target)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = getGreetingText(),
                        style = FinanceTheme.typography.bodyMedium,
                        color = FinanceTheme.colors.textSecondary,
                    )
                    Text(
                        text = "Vigil Finance",
                        style = FinanceTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = FinanceTheme.colors.textPrimary,
                        ),
                    )
                }

                // Sync button with >= 48dp touch target
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(FinanceTheme.shapes.pill)
                        .clickable(enabled = !isLoading, onClick = onRefresh),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(FinanceTheme.colors.primaryContainer, FinanceTheme.shapes.pill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = "Đồng bộ",
                            tint = FinanceTheme.colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // 2. Financial Hero Card: Dòng tiền ròng trong kỳ (bo 28dp, eye toggle >=48dp, tự co font chữ)
            BalanceCard(
                balanceText = data.formattedNetDiff,
                incomeText = data.formattedIncome,
                expenseText = data.formattedExpense,
                periodLabel = data.periodLabel,
            )

            // 3. Hai Quick Action Cards: "Ghi khoản chi" & "Ghi khoản thu" mở thẳng RecordScreen đúng trạng thái
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickActionCard(
                    title = "Ghi khoản chi",
                    subtitle = "Tiền ra",
                    icon = Icons.Rounded.NorthEast,
                    accentColor = FinanceTheme.colors.expense,
                    onClick = { onQuickRecord(false) },
                    modifier = Modifier.weight(1f),
                )
                QuickActionCard(
                    title = "Ghi khoản thu",
                    subtitle = "Tiền vào",
                    icon = Icons.Rounded.SouthWest,
                    accentColor = FinanceTheme.colors.income,
                    onClick = { onQuickRecord(true) },
                    modifier = Modifier.weight(1f),
                )
            }

            // 4. Lưới Tiện ích tài chính 4 ô (Tài khoản · Vay & lãi · Ghi nhớ · Đồng bộ)
            FinanceCard(
                modifier = Modifier.fillMaxWidth(),
                shape = FinanceTheme.shapes.large,
                contentPadding = 14.dp,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Tiện ích tài chính",
                        style = FinanceTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = FinanceTheme.colors.textPrimary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ServiceGridItem(
                            title = "Tài khoản",
                            icon = Icons.Rounded.AccountBalance,
                            iconTint = FinanceTheme.colors.primary,
                            iconBg = FinanceTheme.colors.primaryContainer,
                            onClick = onOpenAccounts,
                            modifier = Modifier.weight(1f),
                        )
                        ServiceGridItem(
                            title = "Vay & lãi",
                            icon = Icons.Rounded.Percent,
                            iconTint = Color(0xFF159BD7),
                            iconBg = Color(0xFF159BD7).copy(alpha = 0.12f),
                            onClick = onOpenInterest,
                            modifier = Modifier.weight(1f),
                        )
                        ServiceGridItem(
                            title = "Ghi nhớ",
                            icon = Icons.Rounded.History,
                            iconTint = Color(0xFFAB47BC),
                            iconBg = Color(0xFFAB47BC).copy(alpha = 0.12f),
                            onClick = onOpenMemory,
                            modifier = Modifier.weight(1f),
                        )
                        ServiceGridItem(
                            title = "Đồng bộ",
                            icon = Icons.Rounded.SyncAlt,
                            iconTint = FinanceTheme.colors.warning,
                            iconBg = FinanceTheme.colors.warning.copy(alpha = 0.14f),
                            onClick = onOpenSync,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // 5. Biểu đồ xu hướng chi tiêu (nếu có dữ liệu)
            if (data.possibleDuplicateCount > 0) {
                Text(
                    text = "Có giao dịch có thể trùng giữa nguồn nhập lịch sử và ngân hàng. Cần đối soát trước khi dùng tổng số.",
                    color = FinanceTheme.colors.expense,
                    style = FinanceTheme.typography.caption,
                )
            }
            if (data.hasReportBreakdowns) {
                SpendingChart(daily = data.daily)
            }

            // 6. Giao dịch gần đây (tối đa 5 giao dịch, kết nối onTransactionClick)
            FinanceCard(
                modifier = Modifier.fillMaxWidth(),
                shape = FinanceTheme.shapes.large,
                contentPadding = 16.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SectionHeader(
                        title = "Giao dịch gần đây",
                        actionLabel = "Xem tất cả",
                        onActionClick = onViewAllTransactions,
                    )

                    if (data.recentTransactions.isEmpty()) {
                        EmptyState(
                            title = "Chưa có giao dịch",
                            subtitle = "Các giao dịch từ ngân hàng hoặc lịch sử sẽ hiển thị ở đây.",
                        )
                    } else {
                        data.recentTransactions.take(5).forEach { tx ->
                            TransactionRow(
                                transaction = tx,
                                onClick = { onTransactionClick(tx.id) },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

/**
 * Returns dynamic greeting based on Vietnam time of day.
 */
fun getGreetingText(): String {
    val hour = runCatching {
        LocalTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).hour
    }.getOrDefault(LocalTime.now().hour)

    return when (hour) {
        in 5..11 -> "Chào buổi sáng"
        in 12..17 -> "Chào buổi chiều"
        else -> "Chào buổi tối"
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = FinanceTheme.shapes.medium
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 72.dp)
            .shadow(
                elevation = FinanceTheme.spacing.elevationLow,
                shape = cardShape,
                ambientColor = FinanceTheme.colors.shadowAmbient,
                spotColor = FinanceTheme.colors.shadowSpot,
            )
            .clip(cardShape)
            .background(FinanceTheme.colors.surfaceCard)
            .border(1.dp, FinanceTheme.colors.borderGlass, cardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(FinanceTheme.shapes.pill)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = title,
                    style = FinanceTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = FinanceTheme.colors.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = FinanceTheme.typography.caption.copy(fontSize = 11.sp),
                    color = FinanceTheme.colors.textMuted,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ServiceGridItem(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 56.dp)
            .clip(FinanceTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(FinanceTheme.shapes.medium)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            style = FinanceTheme.typography.caption.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = FinanceTheme.colors.textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
