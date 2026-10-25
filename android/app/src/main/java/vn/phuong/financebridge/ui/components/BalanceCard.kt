package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.theme.Primary500
import vn.phuong.financebridge.ui.theme.Primary700
import vn.phuong.financebridge.ui.theme.AccentMint

@Composable
fun BalanceCard(
    balanceText: String,
    incomeText: String,
    expenseText: String,
    periodLabel: String,
    modifier: Modifier = Modifier,
) {
    val cardShape = FinanceTheme.shapes.xxl
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            Primary700,
            Primary500,
        ),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = FinanceTheme.spacing.elevationFloating,
                shape = cardShape,
                ambientColor = Color(0x1A174E36),
                spotColor = Color(0x33174E36),
            )
            .clip(cardShape)
            .background(gradientBrush)
            .border(
                width = 1.dp,
                color = AccentMint.copy(alpha = 0.35f),
                shape = cardShape,
            )
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header Row: Label & Period badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tổng số dư khả dụng",
                    style = FinanceTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Box(
                    modifier = Modifier
                        .clip(FinanceTheme.shapes.pill)
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = periodLabel,
                        style = FinanceTheme.typography.caption,
                        color = Color.White,
                    )
                }
            }

            // Central balance amount
            Text(
                text = balanceText,
                style = FinanceTheme.typography.balance.copy(fontSize = 32.sp),
                color = Color.White,
            )

            // Divider line with subtle glass opacity
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.15f)),
            )

            // Monthly Summary: Thu & Chi sub-metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Thu nhập
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(FinanceTheme.shapes.pill)
                                .background(AccentMint),
                        )
                        Text(
                            text = "Thu tháng này",
                            style = FinanceTheme.typography.caption,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                    Text(
                        text = incomeText,
                        style = FinanceTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }

                // Chi tiêu
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(FinanceTheme.shapes.pill)
                                .background(Color(0xFFFF8B8B)),
                        )
                        Text(
                            text = "Chi tháng này",
                            style = FinanceTheme.typography.caption,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                    Text(
                        text = expenseText,
                        style = FinanceTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
