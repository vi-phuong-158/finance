package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
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
import vn.phuong.financebridge.ui.util.WindowWidthTier
import vn.phuong.financebridge.ui.util.adaptiveNavWidth
import vn.phuong.financebridge.ui.util.rememberWindowWidthTier

enum class NavigationTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Tổng quan", Icons.Rounded.Home),
    TRANSACTIONS("Giao dịch", Icons.AutoMirrored.Rounded.ReceiptLong),
    RECORD("+", Icons.Rounded.Add),
    REPORTS("Báo cáo", Icons.Rounded.PieChart),
    MORE("Cá nhân", Icons.Rounded.Person), // Renamed from "Khác" to "Cá nhân" per UI V2 spec
    ACCOUNTS("Tài khoản", Icons.Rounded.AccountBalanceWallet), // internal route
}

@Composable
fun FinanceBottomBar(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val barShape = FinanceTheme.shapes.xxl
    val windowTier = rememberWindowWidthTier()
    val isNarrow = windowTier == WindowWidthTier.NARROW_FOLD

    // 5 primary navigation tabs as specified in UI V2 Phase F
    val visibleTabs = listOf(
        NavigationTab.DASHBOARD,
        NavigationTab.TRANSACTIONS,
        NavigationTab.RECORD,
        NavigationTab.REPORTS,
        NavigationTab.MORE,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                horizontal = if (isNarrow) 10.dp else 16.dp,
                vertical = 8.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .adaptiveNavWidth()
                .heightIn(min = 68.dp)
                .shadow(
                    elevation = FinanceTheme.spacing.elevationFloating,
                    shape = barShape,
                    ambientColor = FinanceTheme.colors.shadowAmbient,
                    spotColor = FinanceTheme.colors.shadowSpot,
                )
                .clip(barShape)
                .background(FinanceTheme.colors.surfaceGlass)
                .border(1.dp, FinanceTheme.colors.borderGlass, barShape)
                .padding(horizontal = if (isNarrow) 4.dp else 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            visibleTabs.forEach { tab ->
                if (tab == NavigationTab.RECORD) {
                    RecordCenterButton(
                        onSelect = { onTabSelected(NavigationTab.RECORD) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    NavigationTabItem(
                        tab = tab,
                        isSelected = currentTab == tab,
                        isNarrow = isNarrow,
                        onSelect = { onTabSelected(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationTabItem(
    tab: NavigationTab,
    isSelected: Boolean,
    isNarrow: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = FinanceTheme.colors.primary
    val inactiveColor = FinanceTheme.colors.textMuted

    Column(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 52.dp)
            .clip(FinanceTheme.shapes.medium)
            .clickable(onClick = onSelect)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Mint pill indicator for active tab
        Box(
            modifier = Modifier
                .height(30.dp)
                .defaultMinSize(minWidth = if (isSelected) 44.dp else 30.dp)
                .clip(FinanceTheme.shapes.pill)
                .background(if (isSelected) FinanceTheme.colors.primaryContainer else Color.Transparent)
                .padding(horizontal = if (isSelected) 10.dp else 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) activeColor else inactiveColor,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = tab.label,
            style = FinanceTheme.typography.caption.copy(
                fontSize = if (isNarrow) 10.sp else 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (isSelected) activeColor else inactiveColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecordCenterButton(
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = FinanceTheme.shapes.pill,
                    ambientColor = FinanceTheme.colors.shadowAmbient,
                    spotColor = FinanceTheme.colors.shadowSpot,
                )
                .clip(FinanceTheme.shapes.pill)
                .background(FinanceTheme.colors.primary)
                .clickable(onClick = onSelect),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Ghi chép",
                modifier = Modifier.size(28.dp),
                tint = Color.White,
            )
        }
    }
}
