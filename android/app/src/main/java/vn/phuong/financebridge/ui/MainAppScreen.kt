package vn.phuong.financebridge.ui

import android.content.Context
import vn.phuong.financebridge.ui.screens.interest.InterestScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import vn.phuong.financebridge.ui.components.FinanceBottomBar
import vn.phuong.financebridge.ui.components.NavigationTab
import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.screens.accounts.AccountsScreen
import vn.phuong.financebridge.ui.screens.dashboard.DashboardRepository
import vn.phuong.financebridge.ui.screens.dashboard.DashboardScreen
import vn.phuong.financebridge.ui.screens.more.MoreScreen
import vn.phuong.financebridge.ui.screens.record.RecordScreen
import vn.phuong.financebridge.ui.screens.reports.ReportsScreen
import vn.phuong.financebridge.ui.screens.transactions.TransactionsScreen
import vn.phuong.financebridge.ui.theme.FinanceBridgeTheme
import vn.phuong.financebridge.ui.theme.FinanceTheme

@Composable
fun MainAppScreen(
    context: Context,
    modifier: Modifier = Modifier,
) {
    var currentTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }
    var showInterest by remember { mutableStateOf(false) }

    val repository = remember(context) { DashboardRepository(context) }
    val scope = rememberCoroutineScope()

    var dashboardState by remember { mutableStateOf<UiDashboard?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadData() {
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = repository.fetchDashboard()
            result.onSuccess { data ->
                dashboardState = data
                isLoading = false
            }.onFailure { err ->
                errorMessage = err.message ?: "Không tải được dữ liệu"
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    val data = dashboardState ?: UiDashboard.preview()
    val bottomBarTab = if (currentTab == NavigationTab.TRANSACTIONS) NavigationTab.ACCOUNTS else currentTab

    FinanceBridgeTheme {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = FinanceTheme.colors.background,
            bottomBar = {
                FinanceBottomBar(
                    currentTab = bottomBarTab,
                    onTabSelected = { showInterest = false; currentTab = it },
                )
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                if (showInterest) {
                    InterestScreen(context = context, onBack = { showInterest = false })
                } else when (currentTab) {
                    NavigationTab.DASHBOARD -> DashboardScreen(
                        dashboardState = dashboardState,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onRefresh = { loadData() },
                        onViewAllTransactions = { currentTab = NavigationTab.TRANSACTIONS },
                    )

                    NavigationTab.ACCOUNTS -> AccountsScreen(
                        dashboardData = data,
                        onViewInterest = { showInterest = true },
                        onViewTransactions = { currentTab = NavigationTab.TRANSACTIONS },
                    )

                    NavigationTab.RECORD -> RecordScreen(
                        onSave = { _, _, _, _ ->
                            // UI-only in this phase. Manual write API is implemented in a later phase.
                        },
                    )

                    NavigationTab.REPORTS -> ReportsScreen(
                        dashboardData = data,
                    )

                    NavigationTab.MORE -> MoreScreen(
                        context = context,
                    )

                    NavigationTab.TRANSACTIONS -> TransactionsScreen(
                        transactions = data.recentTransactions,
                    )
                }
            }
        }
    }
}
