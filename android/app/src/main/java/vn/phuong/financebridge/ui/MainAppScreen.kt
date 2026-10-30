package vn.phuong.financebridge.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import vn.phuong.financebridge.ManualSyncWorker
import vn.phuong.financebridge.ui.components.FinanceBottomBar
import vn.phuong.financebridge.ui.components.NavigationTab
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.screens.accounts.AccountsScreen
import vn.phuong.financebridge.ui.screens.dashboard.DashboardRepository
import vn.phuong.financebridge.ui.screens.dashboard.DashboardScreen
import vn.phuong.financebridge.ui.screens.interest.InterestScreen
import vn.phuong.financebridge.ui.screens.more.MoreScreen
import vn.phuong.financebridge.ui.screens.record.ExtendedTransactionType
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
    // Preserve navigation state across configuration changes (e.g. Z Fold 4 fold/unfold)
    var currentTab by rememberSaveable { mutableStateOf(NavigationTab.DASHBOARD) }
    var openTransaction by remember { mutableStateOf<UiTransaction?>(null) }
    var openTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
    var showInterest by rememberSaveable { mutableStateOf(false) }
    var recordInitialType by rememberSaveable { mutableStateOf(ExtendedTransactionType.CHI) }

    val repository = remember(context) { DashboardRepository(context) }
    val scope = rememberCoroutineScope()

    var dashboardState by remember { mutableStateOf<UiDashboard?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }

    fun loadData() {
        loadJob?.cancel()
        isLoading = true
        errorMessage = null
        loadJob = scope.launch {
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
        ManualSyncWorker.schedule(context)
        loadData()
    }

    // System BackHandler: prevents accidental app exit when navigating sub-routes or secondary tabs
    val hasSubRoute = showInterest || currentTab != NavigationTab.DASHBOARD
    BackHandler(enabled = hasSubRoute) {
        if (showInterest) {
            showInterest = false
        } else if (currentTab != NavigationTab.DASHBOARD) {
            currentTab = NavigationTab.DASHBOARD
        }
    }

    FinanceBridgeTheme {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = FinanceTheme.colors.background,
            bottomBar = {
                FinanceBottomBar(
                    currentTab = currentTab,
                    onTabSelected = { selected ->
                        showInterest = false
                        if (selected == NavigationTab.RECORD) {
                            recordInitialType = ExtendedTransactionType.CHI
                        }
                        currentTab = selected
                        if (selected == NavigationTab.DASHBOARD) {
                            loadData()
                        }
                    },
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
                        onTransactionClick = { id ->
                            openTransaction = dashboardState?.recentTransactions?.firstOrNull { it.id == id }
                            openTransactionId = id
                            currentTab = NavigationTab.TRANSACTIONS
                        },
                        onQuickRecord = { isIncome ->
                            recordInitialType = if (isIncome) ExtendedTransactionType.THU else ExtendedTransactionType.CHI
                            currentTab = NavigationTab.RECORD
                        },
                        onOpenAccounts = { currentTab = NavigationTab.ACCOUNTS },
                        onOpenInterest = { showInterest = true },
                        onOpenMemory = { currentTab = NavigationTab.MORE },
                        onOpenSync = { currentTab = NavigationTab.MORE },
                    )

                    NavigationTab.TRANSACTIONS -> TransactionsScreen(
                        context = context, initialTransactionId = openTransactionId, initialTransaction = openTransaction, onInitialConsumed = { openTransactionId = null; openTransaction = null },
                    )

                    NavigationTab.RECORD -> RecordScreen(
                        context = context,
                        initialType = recordInitialType,
                        onSaved = { loadData() },
                    )

                    NavigationTab.REPORTS -> ReportsScreen(
                        context = context,
                    )

                    NavigationTab.MORE -> MoreScreen(
                        context = context,
                    )

                    NavigationTab.ACCOUNTS -> AccountsScreen(
                        onViewInterest = { showInterest = true },
                        onViewTransactions = { currentTab = NavigationTab.TRANSACTIONS },
                    )
                }
            }
        }
    }
}
