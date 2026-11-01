package vn.phuong.financebridge.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.theme.FinanceTheme
import vn.phuong.financebridge.ui.util.CategoryVisuals
import vn.phuong.financebridge.ui.util.DonutSlice
import vn.phuong.financebridge.ui.util.FinanceFormatters
import vn.phuong.financebridge.ui.util.ReportPeriodHelper

/**
 * Donut chart component implementing UI V2 HANDOFF §4:
 * - Canvas-based drawing without third-party chart libraries.
 * - Angle-based hit testing on tap to select a slice.
 * - Selected slice expands slightly.
 * - Center of donut displays slice details (name, amount, percentage) or overall summary.
 * - Below (or side-by-side at >=600dp), a comprehensive Legend list with CategoryVisuals icons.
 * - Tapping a category row invokes [onCategoryClick] to open CategoryHistory.
 * - Tapping "Khác" expands sub-categories inline without opening history.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    isIncome: Boolean,
    modifier: Modifier = Modifier,
    onCategoryClick: (String) -> Unit = {},
) {
    if (slices.isEmpty()) {
        FinanceCard(modifier = modifier.fillMaxWidth(), contentPadding = 20.dp) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (isIncome) "Chưa có thu nhập trong kỳ này." else "Chưa có chi tiêu trong kỳ này.",
                    style = FinanceTheme.typography.bodyMedium,
                    color = FinanceTheme.colors.textMuted
                )
            }
        }
        return
    }

    var selectedSliceIndex by remember(slices) { mutableStateOf<Int?>(null) }
    var expandedOther by remember(slices) { mutableStateOf(false) }

    val totalAmount = remember(slices) { slices.sumOf { it.amount } }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isWide = maxWidth >= 600.dp

        FinanceCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionHeader(title = if (isIncome) "Cơ cấu thu nhập" else "Cơ cấu chi tiêu")

                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DonutCanvasWithCenter(
                            slices = slices,
                            selectedSliceIndex = selectedSliceIndex,
                            totalAmount = totalAmount,
                            isIncome = isIncome,
                            onSelectSlice = { index ->
                                selectedSliceIndex = if (selectedSliceIndex == index) null else index
                            },
                            modifier = Modifier.size(240.dp)
                        )
                        DonutLegend(
                            slices = slices,
                            isIncome = isIncome,
                            selectedSliceIndex = selectedSliceIndex,
                            expandedOther = expandedOther,
                            onToggleOther = { expandedOther = !expandedOther },
                            onSliceClick = { index, slice ->
                                selectedSliceIndex = index
                                if (!slice.isOther && ReportPeriodHelper.isValidCategoryForHistory(slice.name)) {
                                    onCategoryClick(slice.name)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DonutCanvasWithCenter(
                            slices = slices,
                            selectedSliceIndex = selectedSliceIndex,
                            totalAmount = totalAmount,
                            isIncome = isIncome,
                            onSelectSlice = { index ->
                                selectedSliceIndex = if (selectedSliceIndex == index) null else index
                            },
                            modifier = Modifier.size(220.dp)
                        )
                        DonutLegend(
                            slices = slices,
                            isIncome = isIncome,
                            selectedSliceIndex = selectedSliceIndex,
                            expandedOther = expandedOther,
                            onToggleOther = { expandedOther = !expandedOther },
                            onSliceClick = { index, slice ->
                                selectedSliceIndex = index
                                if (!slice.isOther && ReportPeriodHelper.isValidCategoryForHistory(slice.name)) {
                                    onCategoryClick(slice.name)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutCanvasWithCenter(
    slices: List<DonutSlice>,
    selectedSliceIndex: Int?,
    totalAmount: Long,
    isIncome: Boolean,
    onSelectSlice: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FinanceTheme.colors
    val selectedSlice = selectedSliceIndex?.let { slices.getOrNull(it) }

    // Prepare slice colors
    val sliceColors = remember(slices, colors) {
        slices.map { slice ->
            if (slice.isOther) {
                if (colors.isDark) Color(0xFF78909C) else Color(0xFF607D8B)
            } else {
                CategoryVisuals.color(slice.name, colors)
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(slices) {
                    detectTapGestures { offset ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val dx = offset.x - centerX
                        val dy = offset.y - centerY
                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                        val minDimension = kotlin.math.min(size.width, size.height).toFloat()
                        val outerRadius = minDimension / 2f
                        val innerRadius = outerRadius * 0.58f

                        // Check if tap fell inside donut ring
                        if (distance in innerRadius..outerRadius * 1.15f) {
                            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            // Normalize angle from top (-90 degrees)
                            angle = (angle + 90f + 360f) % 360f

                            var cumulative = 0f
                            var tappedIndex: Int? = null
                            for ((idx, slice) in slices.withIndex()) {
                                if (angle >= cumulative && angle < cumulative + slice.sweepAngle) {
                                    tappedIndex = idx
                                    break
                                }
                                cumulative += slice.sweepAngle
                            }
                            onSelectSlice(tappedIndex)
                        } else {
                            onSelectSlice(null)
                        }
                    }
                }
        ) {
            val strokeWidthNormal = size.minDimension * 0.16f
            val strokeWidthExpanded = size.minDimension * 0.22f
            val baseRadius = (size.minDimension - strokeWidthExpanded) / 2f

            var currentAngle = -90f // Start from 12 o'clock

            for ((idx, slice) in slices.withIndex()) {
                val isSelected = idx == selectedSliceIndex
                val strokeWidth = if (isSelected) strokeWidthExpanded else strokeWidthNormal
                val color = sliceColors[idx]

                // For selected slice, offset center slightly outward
                val midAngleRad = Math.toRadians((currentAngle + slice.sweepAngle / 2f).toDouble())
                val offsetDistance = if (isSelected) 6.dp.toPx() else 0f
                val arcCenter = Offset(
                    x = center.x + (cos(midAngleRad) * offsetDistance).toFloat(),
                    y = center.y + (sin(midAngleRad) * offsetDistance).toFloat()
                )

                val arcRadius = baseRadius
                val arcTopLeft = Offset(arcCenter.x - arcRadius, arcCenter.y - arcRadius)
                val arcSize = Size(arcRadius * 2f, arcRadius * 2f)

                drawArc(
                    color = color,
                    startAngle = currentAngle,
                    sweepAngle = (slice.sweepAngle - 1.2f).coerceAtLeast(0.5f), // subtle gap
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                currentAngle += slice.sweepAngle
            }
        }

        // Center content inside donut hole
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize(0.52f)
                .padding(4.dp)
        ) {
            if (selectedSlice != null) {
                Text(
                    text = selectedSlice.name,
                    style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = FinanceTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                AmountText(
                    amountText = FinanceFormatters.formatVnd(selectedSlice.amount, includeSign = false),
                    type = if (isIncome) TransactionType.THU else TransactionType.CHI,
                    style = FinanceTheme.typography.titleMedium,
                    autoShrink = true
                )
                Text(
                    text = "${selectedSlice.percentage}%",
                    style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = sliceColors[selectedSliceIndex],
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = if (isIncome) "Tổng thu" else "Tổng chi",
                    style = FinanceTheme.typography.caption,
                    color = FinanceTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                AmountText(
                    amountText = FinanceFormatters.formatVnd(totalAmount, includeSign = false),
                    type = if (isIncome) TransactionType.THU else TransactionType.CHI,
                    style = FinanceTheme.typography.titleMedium,
                    autoShrink = true
                )
                Text(
                    text = "${slices.size} nhóm",
                    style = FinanceTheme.typography.caption,
                    color = FinanceTheme.colors.textMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DonutLegend(
    slices: List<DonutSlice>,
    isIncome: Boolean,
    selectedSliceIndex: Int?,
    expandedOther: Boolean,
    onToggleOther: () -> Unit,
    onSliceClick: (Int, DonutSlice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FinanceTheme.colors

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        slices.forEachIndexed { index, slice ->
            val isSelected = index == selectedSliceIndex
            val sliceColor = if (slice.isOther) {
                if (colors.isDark) Color(0xFF78909C) else Color(0xFF607D8B)
            } else {
                CategoryVisuals.color(slice.name, colors)
            }
            val sliceIcon = if (slice.isOther) {
                Icons.AutoMirrored.Rounded.HelpOutline
            } else {
                CategoryVisuals.icon(slice.name)
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (slice.isOther) {
                            onToggleOther()
                        } else {
                            onSliceClick(index, slice)
                        }
                    },
                shape = FinanceTheme.shapes.small,
                color = if (isSelected) sliceColor.copy(alpha = 0.12f) else Color.Transparent,
                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, sliceColor.copy(alpha = 0.4f)) else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Category icon with colored circular tint
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(sliceColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = sliceIcon,
                            contentDescription = slice.name,
                            tint = sliceColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Category name and percentage
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = slice.name,
                                style = FinanceTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = FinanceTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = "${slice.percentage}%",
                                style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                                color = sliceColor
                            )
                        }
                    }

                    // Amount formatted
                    AmountText(
                        amountText = FinanceFormatters.formatVnd(slice.amount, includeSign = false),
                        type = if (isIncome) TransactionType.THU else TransactionType.CHI,
                        style = FinanceTheme.typography.bodyMedium,
                        autoShrink = true
                    )

                    // Trailing indicator: expand/collapse for "Khác", chevron for valid history
                    if (slice.isOther) {
                        Icon(
                            imageVector = if (expandedOther) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (expandedOther) "Thu gọn" else "Xem thêm",
                            tint = FinanceTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    } else if (ReportPeriodHelper.isValidCategoryForHistory(slice.name)) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Xem lịch sử",
                            tint = FinanceTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Expanded sub-categories under "Khác"
            if (slice.isOther && expandedOther && slice.subCategories.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 8.dp)
                        .background(FinanceTheme.colors.surfaceCard, FinanceTheme.shapes.small)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    slice.subCategories.forEach { subItem ->
                        val subColor = CategoryVisuals.color(subItem.name, colors)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(subColor, CircleShape)
                            )
                            Text(
                                text = subItem.name,
                                style = FinanceTheme.typography.caption,
                                color = FinanceTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            AmountText(
                                amountText = FinanceFormatters.formatVnd(subItem.amount, includeSign = false),
                                type = if (isIncome) TransactionType.THU else TransactionType.CHI,
                                style = FinanceTheme.typography.caption,
                                autoShrink = true
                            )
                        }
                    }
                }
            }
        }
    }
}
