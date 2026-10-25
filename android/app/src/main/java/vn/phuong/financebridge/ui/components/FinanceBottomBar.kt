package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.phuong.financebridge.ui.theme.FinanceTheme

enum class NavigationTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Tổng quan", Icons.Rounded.Home),
    ACCOUNTS("Tài khoản", Icons.Rounded.AccountBalanceWallet),
    RECORD("Ghi chép", Icons.Rounded.Add),
    REPORTS("Báo cáo", Icons.Rounded.PieChart),
    MORE("Khác", Icons.Rounded.GridView),
    TRANSACTIONS("Giao dịch", Icons.Rounded.AccountBalanceWallet), // internal route, not shown in bottom bar
}

@Composable
fun FinanceBottomBar(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val barShape = FinanceTheme.shapes.xxl
    val visibleTabs = listOf(
        NavigationTab.DASHBOARD,
        NavigationTab.ACCOUNTS,
        NavigationTab.RECORD,
        NavigationTab.REPORTS,
        NavigationTab.MORE,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(
                elevation = FinanceTheme.spacing.elevationFloating,
                shape = barShape,
                ambientColor = Color(0x12000000),
                spotColor = Color(0x2608A9D6),
            )
            .clip(barShape)
            .background(FinanceTheme.colors.primary)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleTabs.forEach { tab ->
            if (tab == NavigationTab.RECORD) {
                RecordTabItem(
                    isSelected = currentTab == NavigationTab.RECORD,
                    onSelect = { onTabSelected(NavigationTab.RECORD) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                TabItem(
                    tab = tab,
                    isSelected = currentTab == tab,
                    onSelect = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: NavigationTab,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = Color.White
    val inactiveColor = Color.White.copy(alpha = 0.68f)

    Column(
        modifier = modifier
            .clip(FinanceTheme.shapes.medium)
            .clickable(onClick = onSelect)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            modifier = Modifier.size(24.dp),
            tint = if (isSelected) activeColor else inactiveColor,
        )
        Text(
            text = tab.label,
            style = FinanceTheme.typography.caption.copy(
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (isSelected) activeColor else inactiveColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun RecordTabItem(
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(FinanceTheme.shapes.medium)
            .clickable(onClick = onSelect)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(FinanceTheme.shapes.pill)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Ghi chép",
                modifier = Modifier.size(28.dp),
                tint = FinanceTheme.colors.primary,
            )
        }
        Text(
            text = "Ghi chép",
            style = FinanceTheme.typography.caption.copy(
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = Color.White,
            maxLines = 1,
        )
    }
}
