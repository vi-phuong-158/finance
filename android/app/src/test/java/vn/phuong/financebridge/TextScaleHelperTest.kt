package vn.phuong.financebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.phuong.financebridge.ui.util.TextScaleHelper

class TextScaleHelperTest {

    @Test
    fun computesSeventyPercentFloorCorrectly() {
        // Spec: floor is ~70% of initial font size
        val initial30 = 30f
        val floor30 = TextScaleHelper.computeFloorSp(initial30, 0.70f)
        assertEquals(21f, floor30, 0.001f)

        val initial34 = 34f
        val floor34 = TextScaleHelper.computeFloorSp(initial34, 0.70f)
        assertEquals(23.8f, floor34, 0.001f)
    }

    @Test
    fun respectsAbsoluteFloorForVerySmallText() {
        val initial10 = 10f
        val floor10 = TextScaleHelper.computeFloorSp(initial10, 0.70f, absoluteFloorSp = 8f)
        // 10 * 0.7 = 7, but absolute floor is 8
        assertEquals(8f, floor10, 0.001f)
    }

    @Test
    fun stepsDownByOneSpUntilFloor() {
        val floor = 21f
        var current = 25f

        current = TextScaleHelper.nextSteppedDownSp(current, floor, 1f)
        assertEquals(24f, current, 0.001f)

        current = TextScaleHelper.nextSteppedDownSp(current, floor, 1f)
        assertEquals(23f, current, 0.001f)

        current = TextScaleHelper.nextSteppedDownSp(current, floor, 1f)
        assertEquals(22f, current, 0.001f)

        current = TextScaleHelper.nextSteppedDownSp(current, floor, 1f)
        assertEquals(21f, current, 0.001f)

        // Stepping below floor clamps to floor
        current = TextScaleHelper.nextSteppedDownSp(current, floor, 1f)
        assertEquals(21f, current, 0.001f)
    }

    @Test
    fun formatsWithNonBreakingSpaceBeforeCurrency() {
        assertEquals("100.000\u00A0đ", TextScaleHelper.formatWithNonBreakingCurrency("100.000 đ"))
        assertEquals("+12.450.000\u00A0đ", TextScaleHelper.formatWithNonBreakingCurrency("+12.450.000 đ"))
        assertEquals("-500.000\u00A0đ", TextScaleHelper.formatWithNonBreakingCurrency("-500.000 đ"))
        // If already non-breaking or no space, do not corrupt
        assertEquals("100.000\u00A0đ", TextScaleHelper.formatWithNonBreakingCurrency("100.000\u00A0đ"))
    }
}
