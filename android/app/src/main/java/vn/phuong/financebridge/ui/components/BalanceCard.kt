package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.TextScaleHelper

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

    // Formatted with non-breaking currency and signs guaranteed
    val sanitizedBalance = remember(balanceText) {
        val formatted = TextScaleHelper.formatWithNonBreakingCurrency(balanceText)
        if (!formatted.startsWith("+") && !formatted.startsWith("-") && formatted != "0\u00A0đ" && !formatted.startsWith("0")) {
            "+$formatted"
        } else {
            formatted
        }
    }

    val sanitizedIncome = remember(incomeText) {
        val formatted = TextScaleHelper.formatWithNonBreakingCurrency(incomeText)
        if (!formatted.startsWith("+") && formatted != "0\u00A0đ") "+$formatted" else formatted
    }

    val sanitizedExpense = remember(expenseText) {
        val formatted = TextScaleHelper.formatWithNonBreakingCurrency(expenseText)
        if (!formatted.startsWith("-") && formatted != "0\u00A0đ") "-$formatted" else formatted
    }

    // Flat solid primary hero background to maximize WCAG contrast (PROMPT 12)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = FinanceTheme.spacing.elevationMedium,
                shape = cardShape,
                ambientColor = FinanceTheme.colors.shadowAmbient,
                spotColor = FinanceTheme.colors.shadowSpot,
            )
            .clip(cardShape)
            .background(FinanceTheme.colors.primary),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Hàng trên: Nhãn kỳ + Nút ẩn/hiện số tiền (touch target >= 48dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Nhãn kỳ
                Box(
                    modifier = Modifier
                        .clip(FinanceTheme.shapes.pill)
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = periodLabel,
                        style = FinanceTheme.typography.caption.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                        ),
                        color = FinanceTheme.colors.onHero,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Eye toggle with guaranteed >=48dp touch target
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(FinanceTheme.shapes.pill)
                        .clickable { isAmountVisible = !isAmountVisible },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(FinanceTheme.shapes.pill)
                            .background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isAmountVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = if (isAmountVisible) "Ẩn số tiền" else "Hiện số tiền",
                            tint = FinanceTheme.colors.onHero,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Chênh lệch có dấu, chữ lớn, autoShrink
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "CHÊNH LỆCH THU CHI",
                    style = FinanceTheme.typography.caption.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        fontSize = 11.sp,
                    ),
                    color = FinanceTheme.colors.onHeroMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AmountText(
                    amountText = if (isAmountVisible) sanitizedBalance else "••••••••\u00A0đ",
                    style = FinanceTheme.typography.balance.copy(fontSize = 32.sp),
                    color = FinanceTheme.colors.onHero,
                    fontWeight = FontWeight.Bold,
                    autoShrink = true,
                    minScaleRatio = 0.70f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Hàng dưới: 2 ô con heroDeep (Thu và Chi) với icon mũi tên + số tiền chữ trắng
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HeroStatSubCard(
                    title = "Thu",
                    amount = if (isAmountVisible) sanitizedIncome else "••••••\u00A0đ",
                    icon = Icons.Rounded.SouthWest,
                    modifier = Modifier.weight(1f),
                )
                HeroStatSubCard(
                    title = "Chi",
                    amount = if (isAmountVisible) sanitizedExpense else "••••••\u00A0đ",
                    icon = Icons.Rounded.NorthEast,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroStatSubCard(
    title: String,
    amount: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val subCardShape = FinanceTheme.shapes.medium
    Box(
        modifier = modifier
            .clip(subCardShape)
            .background(FinanceTheme.colors.heroDeep)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = FinanceTheme.colors.onHeroMuted,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = title,
                    style = FinanceTheme.typography.caption.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                    ),
                    color = FinanceTheme.colors.onHeroMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AmountText(
                amountText = amount,
                style = FinanceTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = FinanceTheme.colors.onHero,
                autoShrink = true,
                minScaleRatio = 0.75f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
