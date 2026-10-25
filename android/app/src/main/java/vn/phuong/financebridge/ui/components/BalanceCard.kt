package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.background
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
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.theme.HeroEnd
import vn.phuong.financebridge.ui.theme.HeroStart

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
        colors = listOf(HeroStart, HeroEnd),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = FinanceTheme.spacing.elevationMedium,
                shape = cardShape,
                ambientColor = Color(0x12000000),
                spotColor = Color(0x24177CF2),
            )
            .clip(cardShape)
            .background(gradientBrush)
            .padding(22.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Tài chính hiện tại",
                style = FinanceTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.88f),
            )

            Text(
                text = balanceText,
                style = FinanceTheme.typography.balance.copy(fontSize = 34.sp),
                color = Color.White,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryTile(
                    title = "Tổng thu",
                    value = incomeText,
                    modifier = Modifier.weight(1f),
                )
                SummaryTile(
                    title = "Tổng chi",
                    value = expenseText,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = periodLabel,
                style = FinanceTheme.typography.caption,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun SummaryTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(FinanceTheme.shapes.large)
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = FinanceTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.82f),
        )
        Text(
            text = value,
            style = FinanceTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
        )
    }
}
