package vn.phuong.financebridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.phuong.financebridge.CategorySuggestion
import vn.phuong.financebridge.ui.theme.FinanceTheme

// Compact, rule-based hint under a category picker. Wording stays "từ lịch sử": nothing here is AI.
@Composable
fun CategorySuggestionCard(
    suggestion: CategorySuggestion,
    applied: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(FinanceTheme.colors.primaryContainer, FinanceTheme.shapes.medium)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = null,
            tint = FinanceTheme.colors.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = suggestion.headline,
                style = FinanceTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = FinanceTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (applied) "Đã chọn sẵn · ${suggestion.detail}" else suggestion.detail,
                style = FinanceTheme.typography.caption,
                color = FinanceTheme.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!applied) {
            TextButton(
                onClick = onApply,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text(
                    text = "Dùng",
                    maxLines = 1,
                    style = FinanceTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = FinanceTheme.colors.primary,
                )
            }
        }
        // Guaranteed touch target >= 48dp as required by a11y & UI V2 spec
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Bỏ gợi ý",
                tint = FinanceTheme.colors.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
