package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.theme.HeroEnd
import vn.phuong.financebridge.ui.theme.HeroStart

@Suppress("UNUSED_PARAMETER")
@Composable
fun BalanceCard(
    balanceText: String,
    incomeText: String,
    expenseText: String,
    periodLabel: String,
    modifier: Modifier = Modifier,
) {
    var isAmountVisible by remember { mutableStateOf(true) }
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
                spotColor = Color(0x28177CF2),
            )
            .clip(cardShape)
            .background(gradientBrush)
            .padding(22.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tài chính hiện tại",
                    style = FinanceTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.88f),
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(FinanceTheme.shapes.pill)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { isAmountVisible = !isAmountVisible },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isAmountVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = "Ẩn/hiện số dư",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Text(
                text = if (isAmountVisible) balanceText else "•••••••• đ",
                style = FinanceTheme.typography.balance.copy(fontSize = 34.sp),
                color = Color.White,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tổng hợp từ dữ liệu giao dịch",
                    style = FinanceTheme.typography.caption,
                    color = Color.White.copy(alpha = 0.80f),
                )
                Box(
                    modifier = Modifier
                        .clip(FinanceTheme.shapes.pill)
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = periodLabel,
                        style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                        color = Color.White,
                    )
                }
            }
        }
    }
}
