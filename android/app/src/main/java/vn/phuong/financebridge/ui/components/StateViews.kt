package vn.phuong.financebridge.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.phuong.financebridge.ui.theme.FinanceTheme

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Skeleton Balance Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(FinanceTheme.shapes.xxl)
                .background(FinanceTheme.colors.primary.copy(alpha = alpha * 0.4f)),
        )

        // Skeleton Metric Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .clip(FinanceTheme.shapes.large)
                    .background(FinanceTheme.colors.borderGlass.copy(alpha = alpha)),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .clip(FinanceTheme.shapes.large)
                    .background(FinanceTheme.colors.borderGlass.copy(alpha = alpha)),
            )
        }

        // Skeleton Transaction List Items
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(FinanceTheme.shapes.medium)
                    .background(FinanceTheme.colors.borderGlass.copy(alpha = alpha * 0.6f)),
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String = "Chưa có giao dịch",
    subtitle: String = "Các giao dịch mới sẽ tự động xuất hiện tại đây.",
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(FinanceTheme.shapes.pill)
                .background(FinanceTheme.colors.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✓",
                style = FinanceTheme.typography.displayLarge.copy(fontSize = 32.sp),
                color = FinanceTheme.colors.primary,
            )
        }

        Text(
            text = title,
            style = FinanceTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = FinanceTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Text(
            text = subtitle,
            style = FinanceTheme.typography.bodyMedium,
            color = FinanceTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )

        if (actionLabel != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(8.dp))
            PrimaryButton(
                text = actionLabel,
                onClick = onActionClick,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
    }
}

@Composable
fun ErrorState(
    message: String = "Không tải được dữ liệu.",
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(FinanceTheme.shapes.pill)
                .background(FinanceTheme.colors.expense.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "!",
                style = FinanceTheme.typography.headlineLarge,
                color = FinanceTheme.colors.expense,
            )
        }

        Text(
            text = message,
            style = FinanceTheme.typography.titleMedium,
            color = FinanceTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        SecondaryButton(
            text = "Thử lại",
            onClick = onRetry,
            modifier = Modifier.widthIn(max = 160.dp),
        )
    }
}
