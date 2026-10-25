package vn.phuong.financebridge.ui.screens.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vn.phuong.financebridge.ui.components.FinanceCard
import vn.phuong.financebridge.ui.components.FinanceChip
import vn.phuong.financebridge.ui.components.PrimaryButton
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.theme.FinanceTheme

@Composable
fun RecordScreen(
    onSave: (TransactionType, Long, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var type by remember { mutableStateOf(TransactionType.CHI) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Ăn uống") }

    val categories = listOf("Ăn uống", "Xăng xe", "Con cái", "Mua sắm", "Du lịch", "Dịch vụ", "Khác")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FinanceTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Ghi chép",
            style = FinanceTheme.typography.headlineLarge,
            color = FinanceTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FinanceChip(
                text = "Chi tiền",
                isSelected = type == TransactionType.CHI,
                onClick = { type = TransactionType.CHI },
                modifier = Modifier.weight(1f),
            )
            FinanceChip(
                text = "Thu tiền",
                isSelected = type == TransactionType.THU,
                onClick = { type = TransactionType.THU },
                modifier = Modifier.weight(1f),
            )
        }

        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 20.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Số tiền",
                    style = FinanceTheme.typography.bodyMedium,
                    color = FinanceTheme.colors.textSecondary,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit) },
                    placeholder = { Text("0 ₫") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = FinanceTheme.shapes.medium,
                )
            }
        }

        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 18.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Danh mục",
                    style = FinanceTheme.typography.titleMedium,
                    color = FinanceTheme.colors.textPrimary,
                )
                categories.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowItems.forEach { category ->
                            FinanceChip(
                                text = category,
                                isSelected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }

        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 18.dp,
        ) {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Diễn giải") },
                placeholder = { Text("Ví dụ: Cà phê, siêu thị, tiền điện...") },
                modifier = Modifier.fillMaxWidth(),
                shape = FinanceTheme.shapes.medium,
            )
        }

        Text(
            text = "Ngày ghi nhận: Hôm nay",
            style = FinanceTheme.typography.bodyMedium,
            color = FinanceTheme.colors.textSecondary,
        )

        PrimaryButton(
            text = "Lưu giao dịch",
            enabled = (amountText.toLongOrNull() ?: 0L) > 0L,
            onClick = {
                onSave(
                    type,
                    amountText.toLongOrNull() ?: 0L,
                    selectedCategory,
                    description,
                )
            },
        )

        Text(
            text = "Ghi tay hiện đang ở lớp giao diện; luồng tự động từ thông báo ngân hàng vẫn hoạt động độc lập.",
            style = FinanceTheme.typography.caption,
            color = FinanceTheme.colors.textMuted,
        )

        Spacer(modifier = Modifier.height(56.dp))
    }
}
