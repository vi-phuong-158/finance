package vn.phuong.financebridge.ui.screens.more

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import vn.phuong.financebridge.ui.components.FinanceCard
import vn.phuong.financebridge.ui.components.SecondaryButton
import vn.phuong.financebridge.ui.screens.settings.SettingsScreen
import vn.phuong.financebridge.ui.theme.FinanceTheme

@Composable
fun MoreScreen(
    context: Context,
    modifier: Modifier = Modifier,
) {
    var showSystemSettings by remember { mutableStateOf(false) }
    var activeDialog by remember { mutableStateOf<String?>(null) }

    if (showSystemSettings) {
        Column(modifier = modifier.fillMaxSize().background(FinanceTheme.colors.background)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSystemSettings = false }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = FinanceTheme.colors.primary,
                )
                Text(
                    text = "Khác",
                    style = FinanceTheme.typography.titleMedium,
                    color = FinanceTheme.colors.primary,
                )
            }
            SettingsScreen(
                context = context,
                modifier = Modifier.weight(1f),
            )
        }
        return
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
            text = "Khác",
            style = FinanceTheme.typography.headlineLarge,
            color = FinanceTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 8.dp),
        )

        // 1. Kết nối & hệ thống
        FinanceCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSystemSettings = true },
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 18.dp,
        ) {
            MoreRow(
                icon = Icons.Rounded.Settings,
                iconTint = FinanceTheme.colors.primary,
                iconBg = FinanceTheme.colors.primaryContainer,
                title = "Kết nối & hệ thống",
                subtitle = "Quản lý mã kết nối máy chủ và quyền đọc thông báo",
            )
        }

        // 2. Trạng thái đồng bộ
        FinanceCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSystemSettings = true },
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 18.dp,
        ) {
            MoreRow(
                icon = Icons.Rounded.Sync,
                iconTint = FinanceTheme.colors.income,
                iconBg = FinanceTheme.colors.income.copy(alpha = 0.12f),
                title = "Trạng thái đồng bộ",
                subtitle = "Theo dõi tiến độ gửi giao dịch tự động lên sổ",
            )
        }

        // 3. Danh mục chi tiêu
        FinanceCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { activeDialog = "categories" },
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 18.dp,
        ) {
            MoreRow(
                icon = Icons.Rounded.Category,
                iconTint = Color(0xFFAB47BC),
                iconBg = Color(0xFFAB47BC).copy(alpha = 0.12f),
                title = "Danh mục chi tiêu",
                subtitle = "Xem bảng phân loại thu chi và danh mục mặc định",
            )
        }

        // 4. Cài đặt hiển thị
        FinanceCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { activeDialog = "display" },
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 18.dp,
        ) {
            MoreRow(
                icon = Icons.Rounded.DarkMode,
                iconTint = Color(0xFFFFA726),
                iconBg = Color(0xFFFFA726).copy(alpha = 0.12f),
                title = "Cài đặt hiển thị",
                subtitle = "Tùy chọn giao diện sáng, tối hoặc theo hệ thống",
            )
        }

        // 5. Thông tin ứng dụng
        FinanceCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { activeDialog = "about" },
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 18.dp,
        ) {
            MoreRow(
                icon = Icons.Rounded.Info,
                iconTint = Color(0xFF78909C),
                iconBg = Color(0xFF78909C).copy(alpha = 0.12f),
                title = "Thông tin ứng dụng",
                subtitle = "Finance Bridge V3.1 · Phiên bản 1.0 (Build 1)",
            )
        }

        Spacer(modifier = Modifier.height(56.dp))
    }

    if (activeDialog != null) {
        Dialog(onDismissRequest = { activeDialog = null }) {
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
                    when (activeDialog) {
                        "categories" -> {
                            Text("Danh mục chi tiêu", style = FinanceTheme.typography.titleLarge)
                            Text(
                                "Hệ thống hỗ trợ 34 danh mục chuẩn hóa khớp với Money Lover và ngân hàng (Ăn uống, Xăng xe, Mua sắm, Con cái, Nhà ở, Lương...).",
                                style = FinanceTheme.typography.bodyMedium,
                                color = FinanceTheme.colors.textSecondary,
                            )
                        }
                        "display" -> {
                            Text("Cài đặt hiển thị", style = FinanceTheme.typography.titleLarge)
                            Text(
                                "Giao diện hiện tại tự động điều chỉnh theo chế độ Sáng / Tối (Dark Theme) của cài đặt hệ thống Android.",
                                style = FinanceTheme.typography.bodyMedium,
                                color = FinanceTheme.colors.textSecondary,
                            )
                        }
                        "about" -> {
                            Text("Finance Bridge", style = FinanceTheme.typography.titleLarge)
                            Text(
                                "Ứng dụng tự động ghi nhận biến động số dư ngân hàng và quản lý tài chính cá nhân.\nPhiên bản 1.0 (Build 1)",
                                style = FinanceTheme.typography.bodyMedium,
                                color = FinanceTheme.colors.textSecondary,
                            )
                        }
                    }
                    SecondaryButton(
                        text = "Đóng",
                        onClick = { activeDialog = null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
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
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = FinanceTheme.typography.caption,
                color = FinanceTheme.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = FinanceTheme.colors.textMuted,
        )
    }
}
