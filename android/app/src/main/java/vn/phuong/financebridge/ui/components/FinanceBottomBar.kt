package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.theme.Primary500

enum class NavigationTab(val label: String, val iconSymbol: String) {
    DASHBOARD("Tổng quan", "◈"),
    TRANSACTIONS("Giao dịch", "≡"),
    REPORTS("Báo cáo", "◷"),
    SETTINGS("Cài đặt", "⚙")
}

@Composable
fun FinanceBottomBar(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val barShape = FinanceTheme.shapes.xxl

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Floating Glass Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = FinanceTheme.spacing.elevationFloating,
                    shape = barShape,
                    ambientColor = Color(0x14000000),
                    spotColor = Color(0x1F2F8F63),
                )
                .clip(barShape)
                .background(FinanceTheme.colors.surfaceGlass)
                .border(
                    width = 1.dp,
                    color = FinanceTheme.colors.borderGlass,
                    shape = barShape,
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Tab 1: Tổng quan
                TabItem(
                    tab = NavigationTab.DASHBOARD,
                    isSelected = currentTab == NavigationTab.DASHBOARD,
                    onSelect = { onTabSelected(NavigationTab.DASHBOARD) },
                )

                // Tab 2: Giao dịch
                TabItem(
                    tab = NavigationTab.TRANSACTIONS,
                    isSelected = currentTab == NavigationTab.TRANSACTIONS,
                    onSelect = { onTabSelected(NavigationTab.TRANSACTIONS) },
                )

                // Center placeholder for the floating Add button
                Spacer(modifier = Modifier.width(48.dp))

                // Tab 3: Báo cáo
                TabItem(
                    tab = NavigationTab.REPORTS,
                    isSelected = currentTab == NavigationTab.REPORTS,
                    onSelect = { onTabSelected(NavigationTab.REPORTS) },
                )

                // Tab 4: Cài đặt
                TabItem(
                    tab = NavigationTab.SETTINGS,
                    isSelected = currentTab == NavigationTab.SETTINGS,
                    onSelect = { onTabSelected(NavigationTab.SETTINGS) },
                )
            }
        }

        // Center Floating Add Action Button
        Box(
            modifier = Modifier
                .offset(y = (-6).dp)
                .size(52.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = FinanceTheme.shapes.pill,
                    ambientColor = Color(0x242F8F63),
                    spotColor = Color(0x402F8F63),
                )
                .clip(FinanceTheme.shapes.pill)
                .background(Primary500)
                .clickable(onClick = onAddClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = FinanceTheme.typography.headlineLarge.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                ),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun TabItem(
    tab: NavigationTab,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val activeColor = FinanceTheme.colors.primary
    val inactiveColor = FinanceTheme.colors.textMuted

    Column(
        modifier = Modifier
            .clip(FinanceTheme.shapes.medium)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = tab.iconSymbol,
            style = FinanceTheme.typography.titleMedium.copy(fontSize = 18.sp),
            color = if (isSelected) activeColor else inactiveColor,
        )
        Text(
            text = tab.label,
            style = FinanceTheme.typography.caption.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (isSelected) activeColor else inactiveColor,
        )
    }
}
