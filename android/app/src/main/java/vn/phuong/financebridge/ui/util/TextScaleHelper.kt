package vn.phuong.financebridge.ui.util

/**
 * Pure calculation logic for auto-shrinking text sizing.
 * Ensures font size steps down smoothly to prevent cutting with "..." on narrow screens (e.g. Z Fold 4 outer 344dp).
 */
object TextScaleHelper {
    const val DEFAULT_MIN_SCALE_RATIO = 0.70f // Floor ~70% as required by UI V2 spec

    fun computeFloorSp(initialSp: Float, minScaleRatio: Float = DEFAULT_MIN_SCALE_RATIO, absoluteFloorSp: Float = 8f): Float {
        val calculated = initialSp * minScaleRatio
        return if (calculated < absoluteFloorSp) absoluteFloorSp else calculated
    }

    fun nextSteppedDownSp(currentSp: Float, floorSp: Float, stepSp: Float = 1.0f): Float {
        val next = currentSp - stepSp
        return if (next < floorSp) floorSp else next
    }

    /**
     * Ensures currency text uses non-breaking space (\u00A0) before "đ" to prevent orphan currency symbol.
     */
    fun formatWithNonBreakingCurrency(raw: String): String {
        return raw.replace(" đ", "\u00A0đ")
    }
}
