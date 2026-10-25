package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.phuong.financebridge.ui.theme.FinanceTheme

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = FinanceTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = FinanceTheme.colors.textPrimary,
        )

        if (actionLabel != null && onActionClick != null) {
            Box(
                modifier = Modifier
                    .clip(FinanceTheme.shapes.small)
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = actionLabel,
                    style = FinanceTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = FinanceTheme.colors.primary,
                )
            }
        }
    }
}
