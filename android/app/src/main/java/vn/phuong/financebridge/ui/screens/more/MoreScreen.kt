package vn.phuong.financebridge.ui.screens.more

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.phuong.financebridge.ui.components.FinanceCard
import vn.phuong.financebridge.ui.screens.settings.SettingsScreen
import vn.phuong.financebridge.ui.theme.FinanceTheme

@Composable
fun MoreScreen(
    context: Context,
    modifier: Modifier = Modifier,
) {
    var showSystemSettings by remember { mutableStateOf(false) }

    if (showSystemSettings) {
        Column(modifier = modifier.fillMaxSize()) {
            Text(
                text = "‹  Khác",
                style = FinanceTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = FinanceTheme.colors.primary,
                modifier = Modifier
                    .clickable { showSystemSettings = false }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
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

        FinanceCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSystemSettings = true },
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 18.dp,
        ) {
            MoreRow(
                icon = Icons.Rounded.Settings,
                title = "Kết nối & hệ thống",
                subtitle = "Quyền thông báo, kết nối dữ liệu và trạng thái đồng bộ",
            )
        }

        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 18.dp,
        ) {
            MoreRow(
                icon = Icons.Rounded.Info,
                title = "Thông tin ứng dụng",
                subtitle = "Finance Bridge · phiên bản 1.0",
            )
        }

        Spacer(modifier = Modifier.height(56.dp))
    }
}

@Composable
private fun MoreRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(FinanceTheme.colors.primaryContainer, FinanceTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = FinanceTheme.colors.primary)
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
