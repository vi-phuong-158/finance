package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.phuong.financebridge.ui.theme.FinanceTheme

@Composable
fun FinanceChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isSelected) {
        FinanceTheme.colors.primary
    } else {
        FinanceTheme.colors.surfaceGlass
    }

    val textColor = if (isSelected) {
        Color.White
    } else {
        FinanceTheme.colors.textSecondary
    }

    val borderColor = if (isSelected) {
        FinanceTheme.colors.primary
    } else {
        FinanceTheme.colors.borderGlass
    }

    Box(
        modifier = modifier
            .clip(FinanceTheme.shapes.pill)
            .background(bgColor)
            .border(1.dp, borderColor, FinanceTheme.shapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = FinanceTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = textColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
