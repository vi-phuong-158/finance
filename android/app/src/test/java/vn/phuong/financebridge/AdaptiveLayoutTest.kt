package vn.phuong.financebridge

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.phuong.financebridge.ui.util.AdaptiveLayoutDefaults
import vn.phuong.financebridge.ui.util.WindowWidthTier
import vn.phuong.financebridge.ui.util.computeHorizontalGutterDp
import vn.phuong.financebridge.ui.util.computeWindowWidthTier

class AdaptiveLayoutTest {

    @Test
    fun classifiesZFold4CoverScreenAsNarrow() {
        // Samsung Galaxy Z Fold 4 outer/cover screen is ~344dp width
        assertEquals(WindowWidthTier.NARROW_FOLD, computeWindowWidthTier(344))
        assertEquals(WindowWidthTier.NARROW_FOLD, computeWindowWidthTier(320))
        assertEquals(WindowWidthTier.NARROW_FOLD, computeWindowWidthTier(359))
    }

    @Test
    fun classifiesStandardPhonesCorrectly() {
        assertEquals(WindowWidthTier.STANDARD_PHONE, computeWindowWidthTier(360))
        assertEquals(WindowWidthTier.STANDARD_PHONE, computeWindowWidthTier(390))
        assertEquals(WindowWidthTier.STANDARD_PHONE, computeWindowWidthTier(412))
        assertEquals(WindowWidthTier.STANDARD_PHONE, computeWindowWidthTier(599))
    }

    @Test
    fun classifiesZFold4InnerScreenAsExpanded() {
        // Samsung Galaxy Z Fold 4 unfolded inner screen is ~670-700dp width
        assertEquals(WindowWidthTier.EXPANDED_FOLD, computeWindowWidthTier(600))
        assertEquals(WindowWidthTier.EXPANDED_FOLD, computeWindowWidthTier(673))
        assertEquals(WindowWidthTier.EXPANDED_FOLD, computeWindowWidthTier(700))
        assertEquals(WindowWidthTier.EXPANDED_FOLD, computeWindowWidthTier(840))
    }

    @Test
    fun assignsAdaptiveGuttersAccordingToSpec() {
        // Spec: Narrow screen uses 12dp gutter, standard uses 16dp, wide uses 20dp
        assertEquals(12, computeHorizontalGutterDp(344))
        assertEquals(16, computeHorizontalGutterDp(412))
        assertEquals(20, computeHorizontalGutterDp(673))
    }

    @Test
    fun defaultMaxConstraintsAreSensible() {
        assertEquals(600f, AdaptiveLayoutDefaults.MaxContentWidthDp.value, 0.001f)
        assertEquals(480f, AdaptiveLayoutDefaults.MaxNavWidthDp.value, 0.001f)
    }
}
