package vn.phuong.financebridge.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.TextScaleHelper

@Composable
fun AmountText(
    amountText: String,
    modifier: Modifier = Modifier,
    type: TransactionType? = null,
    style: TextStyle = FinanceTheme.typography.titleMedium,
    color: Color? = null,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    softWrap: Boolean = false,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    autoShrink: Boolean = true,
    minScaleRatio: Float = TextScaleHelper.DEFAULT_MIN_SCALE_RATIO,
) {
    val sanitizedText = remember(amountText) {
        TextScaleHelper.formatWithNonBreakingCurrency(amountText)
    }

    val textColor = color ?: when (type) {
        TransactionType.THU -> FinanceTheme.colors.income
        TransactionType.CHI -> FinanceTheme.colors.expense
        null -> FinanceTheme.colors.textPrimary
    }

    if (!autoShrink) {
        Text(
            text = sanitizedText,
            modifier = modifier,
            style = style,
            color = textColor,
            fontWeight = fontWeight ?: style.fontWeight,
            maxLines = maxLines,
            softWrap = softWrap,
            overflow = overflow,
        )
        return
    }

    val initialSp = style.fontSize.value
    var currentStyle by remember(sanitizedText, style) { mutableStateOf(style) }
    var readyToDraw by remember(sanitizedText, style) { mutableStateOf(false) }

    Text(
        text = sanitizedText,
        modifier = modifier.drawWithContent {
            if (readyToDraw) drawContent()
        },
        style = currentStyle,
        color = textColor,
        fontWeight = fontWeight ?: currentStyle.fontWeight,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = overflow,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.hasVisualOverflow) {
                val currentSp = currentStyle.fontSize.value
                val floorSp = TextScaleHelper.computeFloorSp(initialSp, minScaleRatio)
                if (currentSp > floorSp) {
                    val nextSp = TextScaleHelper.nextSteppedDownSp(currentSp, floorSp)
                    currentStyle = currentStyle.copy(fontSize = nextSp.sp)
                } else {
                    readyToDraw = true
                }
            } else {
                readyToDraw = true
            }
        },
    )
}
