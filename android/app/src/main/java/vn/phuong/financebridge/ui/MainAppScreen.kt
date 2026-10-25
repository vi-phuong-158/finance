package vn.phuong.financebridge.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import vn.phuong.financebridge.ui.components.FinanceBottomBar
import vn.phuong.financebridge.ui.components.NavigationTab
import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.screens.add.AddTransactionDialog
import vn.phuong.financebridge.ui.screens.dashboard.DashboardRepository
import vn.phuong.financebridge.ui.screens.dashboard.DashboardScreen
import vn.phuong.financebridge.ui.screens.reports.ReportsScreen
import vn.phuong.financebridge.ui.screens.settings.SettingsScreen
import vn.phuong.financebridge.ui.screens.transactions.TransactionsScreen
import vn.phuong.financebridge.ui.theme.FinanceBridgeTheme
import vn.phuong.financebridge.ui.theme.FinanceTheme

@Composable
fun MainAppScreen(
    context: Context,
    modifier: Modifier = Modifier,
) {
    var currentTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }
    var showAddDialog by remember { mutableStateOf(false) }

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

    FinanceBridgeTheme {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = FinanceTheme.colors.background,
            bottomBar = {
                FinanceBottomBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it },
                    onAddClick = { showAddDialog = true },
                )
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                when (currentTab) {
                    NavigationTab.DASHBOARD -> DashboardScreen(
                        dashboardState = dashboardState,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onRefresh = { loadData() },
                        onViewAllTransactions = { currentTab = NavigationTab.TRANSACTIONS },
                    )
                    NavigationTab.TRANSACTIONS -> TransactionsScreen(
                        transactions = dashboardState?.recentTransactions ?: UiDashboard.preview().recentTransactions,
                    )
                    NavigationTab.REPORTS -> ReportsScreen(
                        dashboardData = dashboardState ?: UiDashboard.preview(),
                    )
                    NavigationTab.SETTINGS -> SettingsScreen(
                        context = context,
                    )
                }
            }

            if (showAddDialog) {
                AddTransactionDialog(
                    onDismiss = { showAddDialog = false },
                    onSave = { _, _, _, _ ->
                        showAddDialog = false
                        // Refresh data after saving
                        loadData()
                    },
                )
            }
        }
    }
}
