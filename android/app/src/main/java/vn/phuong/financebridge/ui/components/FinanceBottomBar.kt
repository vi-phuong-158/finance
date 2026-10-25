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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.phuong.financebridge.ui.theme.FinanceTheme

enum class NavigationTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Tổng quan", Icons.Rounded.Home),
    ACCOUNTS("Tài khoản", Icons.Rounded.AccountBalanceWallet),
    RECORD("Ghi chép", Icons.Rounded.Add),
    REPORTS("Báo cáo", Icons.Rounded.PieChart),
    MORE("Khác", Icons.Rounded.GridView),
    TRANSACTIONS("Giao dịch", Icons.Rounded.AccountBalanceWallet), // internal route
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .shadow(
                    elevation = FinanceTheme.spacing.elevationFloating,
                    shape = barShape,
                    ambientColor = Color(0x14000000),
                    spotColor = Color(0x2E08A9D6),
                )
                .clip(barShape)
                .background(FinanceTheme.colors.primary)
                .padding(horizontal = 4.dp, vertical = 6.dp),
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
}

@Composable
private fun TabItem(
    tab: NavigationTab,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = Color.White
    val inactiveColor = Color.White.copy(alpha = 0.65f)

    Column(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 56.dp)
            .clip(FinanceTheme.shapes.medium)
            .clickable(onClick = onSelect)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(FinanceTheme.shapes.pill)
                .background(if (isSelected) Color.White.copy(alpha = 0.20f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                modifier = Modifier.size(22.dp),
                tint = if (isSelected) activeColor else inactiveColor,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = tab.label,
            style = FinanceTheme.typography.caption.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (isSelected) activeColor else inactiveColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
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
            .defaultMinSize(minWidth = 48.dp, minHeight = 56.dp)
            .clip(FinanceTheme.shapes.medium)
            .clickable(onClick = onSelect)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = FinanceTheme.shapes.pill,
                    ambientColor = Color(0x18000000),
                    spotColor = Color(0x30000000),
                )
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
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Ghi chép",
            style = FinanceTheme.typography.caption.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
