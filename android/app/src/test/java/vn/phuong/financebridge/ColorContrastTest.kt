package vn.phuong.financebridge

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.phuong.financebridge.ui.theme.*

class ColorContrastTest {

    private fun channelLuminance(c: Float): Double {
        return if (c <= 0.04045f) {
            (c / 12.92).toDouble()
        } else {
            Math.pow(((c + 0.055) / 1.055), 2.4)
        }
    }

    private fun relativeLuminance(color: Color): Double {
        val r = channelLuminance(color.red)
        val g = channelLuminance(color.green)
        val b = channelLuminance(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(c1: Color, c2: Color): Double {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        val lighter = Math.max(l1, l2)
        val darker = Math.min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun lightMode_textSecondary_on_mintBackground_meetsWcagAA() {
        val ratio = contrastRatio(TextSecondaryLight, BackgroundLight)
        // Normal text WCAG AA requires >= 4.5:1
        assertTrue("TextSecondary on BackgroundLight ratio is $ratio, expected >= 4.5", ratio >= 4.5)
    }

    @Test
    fun lightMode_textPrimary_on_mintBackground_meetsWcagAAA() {
        val ratio = contrastRatio(TextPrimaryLight, BackgroundLight)
        // High contrast AAA requires >= 7.0:1
        assertTrue("TextPrimary on BackgroundLight ratio is $ratio, expected >= 7.0", ratio >= 7.0)
    }

    @Test
    fun activeTab_whiteText_on_primary_meetsWcagAA() {
        val ratio = contrastRatio(Color.White, Primary500)
        // White on Primary500 (#078553)
        assertTrue("White on Primary500 ratio is $ratio, expected >= 4.5", ratio >= 4.5)
    }

    @Test
    fun heroCard_whiteText_on_heroDeep_meetsWcagAAA() {
        val ratio = contrastRatio(Color.White, Primary700)
        // White on Primary700 (#055737)
        assertTrue("White on heroDeep ratio is $ratio, expected >= 7.0", ratio >= 7.0)
    }

    @Test
    fun darkMode_whiteText_on_darkHero_meetsWcagAAA() {
        val ratioPrimary700 = contrastRatio(Color.White, Primary700)
        val ratioPrimary800 = contrastRatio(Color.White, Primary800)
        assertTrue("White on dark hero Primary700 ratio is $ratioPrimary700, expected >= 7.0", ratioPrimary700 >= 7.0)
        assertTrue("White on dark heroDeep Primary800 ratio is $ratioPrimary800, expected >= 7.0", ratioPrimary800 >= 7.0)
    }

    @Test
    fun darkMode_text_on_backgroundDark_meetsWcagAA() {
        val ratioPrimary = contrastRatio(TextPrimaryDark, BackgroundDark)
        val ratioSecondary = contrastRatio(TextSecondaryDark, BackgroundDark)
        assertTrue("TextPrimaryDark on BackgroundDark ratio is $ratioPrimary, expected >= 7.0", ratioPrimary >= 7.0)
        assertTrue("TextSecondaryDark on BackgroundDark ratio is $ratioSecondary, expected >= 4.5", ratioSecondary >= 4.5)
    }
}
