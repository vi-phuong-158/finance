package vn.phuong.financebridge.ui.screens.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import vn.phuong.financebridge.ui.components.FinanceCard
import vn.phuong.financebridge.ui.components.SecondaryButton
import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.theme.FinanceTheme

@Composable
fun AccountsScreen(
    dashboardData: UiDashboard,
    onViewTransactions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLoanPlaceholder by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FinanceTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Tài khoản",
            style = FinanceTheme.typography.headlineLarge,
            color = FinanceTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 8.dp),
        )

        // Hero: Tài chính hiện tại / Tổng tài sản
        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 20.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Tài chính hiện tại",
                    style = FinanceTheme.typography.bodyMedium,
                    color = FinanceTheme.colors.textSecondary,
                )
                Text(
                    text = dashboardData.formattedBalance,
                    style = FinanceTheme.typography.balance.copy(fontSize = 32.sp),
                    color = FinanceTheme.colors.textPrimary,
                )
                Text(
                    text = "Tổng hợp từ dữ liệu giao dịch đang có",
                    style = FinanceTheme.typography.caption,
                    color = FinanceTheme.colors.textMuted,
                )
            }
        }

        // Section: Tài khoản ngân hàng
        Text(
            text = "Tài khoản ngân hàng",
            style = FinanceTheme.typography.titleMedium,
            color = FinanceTheme.colors.textPrimary,
        )

        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BankSourceItem(
                    name = "Vietcombank",
                    syncStatus = "Đồng bộ tự động",
                    balance = "Đang kết nối",
                    badgeColor = Color(0xFFE8F5E9),
                    iconTint = Color(0xFF2E7D32),
                )
                HorizontalDivider(color = FinanceTheme.colors.borderGlass.copy(alpha = 0.6f))
                BankSourceItem(
                    name = "VietinBank",
                    syncStatus = "Đồng bộ tự động",
                    balance = "Chưa có dữ liệu số dư",
                    badgeColor = Color(0xFFE3F2FD),
                    iconTint = Color(0xFF1565C0),
                )
                HorizontalDivider(color = FinanceTheme.colors.borderGlass.copy(alpha = 0.6f))
                BankSourceItem(
                    name = "BIDV",
                    syncStatus = "Đồng bộ tự động",
                    balance = "Chưa có dữ liệu số dư",
                    badgeColor = Color(0xFFEDE7F6),
                    iconTint = Color(0xFF4527A0),
                )
            }
        }

        // Section: Quản lý & Tiện ích tài chính
        Text(
            text = "Quản lý & Tiện ích",
            style = FinanceTheme.typography.titleMedium,
            color = FinanceTheme.colors.textPrimary,
        )

        // Entry: Theo dõi tiền lãi
        FinanceCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLoanPlaceholder = true },
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 16.dp,
        ) {
            ActionRow(
                icon = Icons.Rounded.Percent,
                iconBg = FinanceTheme.colors.primaryContainer,
                iconTint = FinanceTheme.colors.primary,
                title = "Theo dõi tiền lãi",
                subtitle = "Quản lý khoản cho vay và lãi phải thu",
            )
        }

        // Entry: Lịch sử giao dịch
        FinanceCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onViewTransactions),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 16.dp,
        ) {
            ActionRow(
                icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                iconBg = FinanceTheme.colors.primaryContainer,
                iconTint = FinanceTheme.colors.primary,
                title = "Lịch sử giao dịch",
                subtitle = "Xem toàn bộ khoản thu và chi đã ghi nhận",
            )
        }

        Spacer(modifier = Modifier.height(56.dp))
    }

    if (showLoanPlaceholder) {
        Dialog(onDismissRequest = { showLoanPlaceholder = false }) {
            Surface(
                shape = FinanceTheme.shapes.xLarge,
                color = FinanceTheme.colors.surfaceCard,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(FinanceTheme.colors.primaryContainer, FinanceTheme.shapes.pill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Percent,
                            contentDescription = null,
                            tint = FinanceTheme.colors.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Text(
                        text = "Theo dõi tiền lãi",
                        style = FinanceTheme.typography.titleLarge,
                        color = FinanceTheme.colors.textPrimary,
                    )

                    Text(
                        text = "Tính năng đang được hoàn thiện",
                        style = FinanceTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = FinanceTheme.colors.primary,
                    )

                    Text(
                        text = "Theo dõi các khoản cho vay, lãi suất và lịch thu lãi định kỳ sẽ được hỗ trợ trong các phiên bản cập nhật tới.",
                        style = FinanceTheme.typography.bodyMedium,
                        color = FinanceTheme.colors.textSecondary,
                        lineHeight = 20.sp,
                    )

                    SecondaryButton(
                        text = "Đóng",
                        onClick = { showLoanPlaceholder = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BankSourceItem(
    name: String,
    syncStatus: String,
    balance: String,
    badgeColor: Color,
    iconTint: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(badgeColor, FinanceTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountBalance,
                contentDescription = name,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                style = FinanceTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = FinanceTheme.colors.textPrimary,
            )
            Text(
                text = syncStatus,
                style = FinanceTheme.typography.caption,
                color = FinanceTheme.colors.income,
            )
        }
        Text(
            text = balance,
            style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
            color = FinanceTheme.colors.textMuted,
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(iconBg, FinanceTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = FinanceTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = FinanceTheme.colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = FinanceTheme.typography.caption,
                color = FinanceTheme.colors.textMuted,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = FinanceTheme.colors.textMuted,
        )
    }
}
