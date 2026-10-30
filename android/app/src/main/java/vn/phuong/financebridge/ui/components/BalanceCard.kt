package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val gradientBrush = Brush.linearGradient(
        colors = listOf(FinanceTheme.colors.heroStart, FinanceTheme.colors.heroEnd),
    )

    val sanitizedBalance = remember(balanceText) {
        TextScaleHelper.formatWithNonBreakingCurrency(balanceText)
    }

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
            .background(gradientBrush),
    ) {
        // Subtle background decorative vector shapes (modern fintech feel, single consistent style, no raster images)
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            // Soft decorative concentric circles
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = height * 0.75f,
                center = Offset(width * 0.95f, height * 0.20f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.03f),
                radius = height * 1.15f,
                center = Offset(width * 0.95f, height * 0.20f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header: Title + Eye toggle (touch target >= 48dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "DÒNG TIỀN RÒNG TRONG KỲ",
                    style = FinanceTheme.typography.caption.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        fontSize = 11.sp,
                    ),
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

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
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isAmountVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = if (isAmountVisible) "Ẩn số tiền" else "Hiện số tiền",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Hero Amount (Auto-shrinking, non-breaking currency, maxLines = 1)
            AmountText(
                amountText = if (isAmountVisible) sanitizedBalance else "••••••••\u00A0đ",
                style = FinanceTheme.typography.balance.copy(fontSize = 32.sp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                autoShrink = true,
                minScaleRatio = 0.70f,
                modifier = Modifier.fillMaxWidth(),
            )

            // Bottom info: Short explanation + Period chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Thu: $incomeText  ·  Chi: $expenseText",
                    style = FinanceTheme.typography.caption.copy(fontSize = 12.sp),
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(FinanceTheme.shapes.pill)
                        .background(Color.White.copy(alpha = 0.20f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = periodLabel,
                        style = FinanceTheme.typography.caption.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                        ),
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
