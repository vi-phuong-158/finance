package vn.phuong.financebridge.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.phuong.financebridge.ui.components.*
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiDashboard
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.theme.FinanceTheme

enum class TransactionFilter(val label: String) {
    ALL("Tất cả"),
    EXPENSE("Chi tiêu"),
    INCOME("Thu nhập")
}

@Composable
fun TransactionsScreen(
    transactions: List<UiTransaction> = UiDashboard.preview().recentTransactions,
    onTransactionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(TransactionFilter.ALL) }

    val filteredList = remember(transactions, searchQuery, selectedFilter) {
        transactions.filter { tx ->
            val matchesFilter = when (selectedFilter) {
                TransactionFilter.ALL -> true
                TransactionFilter.EXPENSE -> tx.type == TransactionType.CHI
                TransactionFilter.INCOME -> tx.type == TransactionType.THU
            }
            val matchesQuery = searchQuery.isBlank() ||
                tx.title.contains(searchQuery, ignoreCase = true) ||
                tx.category.contains(searchQuery, ignoreCase = true) ||
                tx.accountOrBank.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FinanceTheme.colors.background)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Title
        Text(
            text = "Lịch sử giao dịch",
            style = FinanceTheme.typography.headlineLarge,
            color = FinanceTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 8.dp),
        )

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Tìm kiếm giao dịch, danh mục, ngân hàng...") },
            singleLine = true,
            shape = FinanceTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )

        // Filter Chips Row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransactionFilter.values().forEach { filter ->
                FinanceChip(
                    text = filter.label,
                    isSelected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                )
            }
        }

        // Transactions List
        if (filteredList.isEmpty()) {
            EmptyState(
                title = "Không tìm thấy giao dịch",
                subtitle = "Thử thay đổi từ khóa tìm kiếm hoặc bộ lọc.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 70.dp),
            ) {
                items(filteredList, key = { it.id }) { tx ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = FinanceTheme.shapes.large,
                        contentPadding = 8.dp,
                    ) {
                        TransactionRow(
                            transaction = tx,
                            onClick = { onTransactionClick(tx.id) },
                        )
                    }
                }
            }
        }
    }
}
