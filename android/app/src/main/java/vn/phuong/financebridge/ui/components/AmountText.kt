package vn.phuong.financebridge.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.theme.FinanceTheme

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
) {
    val textColor = color ?: when (type) {
        TransactionType.THU -> FinanceTheme.colors.income
        TransactionType.CHI -> FinanceTheme.colors.expense
        null -> FinanceTheme.colors.textPrimary
    }

    Text(
        text = amountText,
        modifier = modifier,
        style = style,
        color = textColor,
        fontWeight = fontWeight ?: style.fontWeight,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = overflow,
    )
}
