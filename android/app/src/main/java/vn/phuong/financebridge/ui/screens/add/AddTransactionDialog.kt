package vn.phuong.financebridge.ui.screens.add

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import vn.phuong.financebridge.ui.components.FinanceChip
import vn.phuong.financebridge.ui.components.GlassCard
import vn.phuong.financebridge.ui.components.PrimaryButton
import vn.phuong.financebridge.ui.components.SecondaryButton
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.theme.FinanceTheme

@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (TransactionType, Long, String, String) -> Unit,
) {
    var type by remember { mutableStateOf(TransactionType.CHI) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Ăn uống") }

    val categories = listOf("Ăn uống", "Mua sắm", "Xăng", "Con cái", "Gia đình", "Lương", "Khác")

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 24.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Thêm giao dịch mới",
                    style = FinanceTheme.typography.titleLarge,
                    color = FinanceTheme.colors.textPrimary,
                )

                // Type Toggle: Thu vs Chi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        FinanceChip(
                            text = "Chi tiêu",
                            isSelected = type == TransactionType.CHI,
                            onClick = { type = TransactionType.CHI },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        FinanceChip(
                            text = "Thu nhập",
                            isSelected = type == TransactionType.THU,
                            onClick = { type = TransactionType.THU },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // Amount Input
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Số tiền (VND)") },
                    placeholder = { Text("0 ₫") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = FinanceTheme.shapes.medium,
                )

                // Description Input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Mô tả / Ghi chú") },
                    placeholder = { Text("Vd: Cà phê, Siêu thị, Tiền điện...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FinanceTheme.shapes.medium,
                )

                // Category chips
                Text(
                    text = "Danh mục",
                    style = FinanceTheme.typography.labelLarge,
                    color = FinanceTheme.colors.textMuted,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    categories.take(4).forEach { cat ->
                        FinanceChip(
                            text = cat,
                            isSelected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Actions: Save & Cancel
                PrimaryButton(
                    text = "Lưu giao dịch",
                    enabled = amountText.isNotBlank() && (amountText.toLongOrNull() ?: 0L) > 0L,
                    onClick = {
                        val amount = amountText.toLongOrNull() ?: 0L
                        onSave(type, amount, selectedCategory, description)
                    },
                )
                SecondaryButton(
                    text = "Hủy",
                    onClick = onDismiss,
                )
            }
        }
    }
}
