package vn.phuong.financebridge.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Receipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.Assert.assertTrue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import vn.phuong.financebridge.ui.theme.*
import kotlin.math.pow

class CategoryVisualsTest {
    // Source: vercel/lib/categories.ts. Add new registry categories here when that list changes.
    private val categories = listOf(
        "Các chi phí khác", "Ăn uống", "Quà tặng & Quyên góp", "Sức khỏe", "Con cái", "Bảo hiểm", "Siêu thị",
        "Hoá đơn nước", "Đồ dùng cá nhân", "Làm đẹp", "Xăng", "Hoá đơn điện thoại", "Ăn tiệm", "Taxi", "Mua sắm",
        "Di chuyển", "Cafe", "Hoá đơn internet", "Trả lãi", "Dịch vụ gia đình", "Giải trí", "Dịch vụ trực tuyến",
        "Khám sức khoẻ", "Hoá đơn & Tiện ích", "Ăn vặt", "Gia đình", "Hoá đơn điện", "Cho vay", "Bảo dưỡng xe",
        "Hiếu hỷ", "Đồ gia dụng", "Tiền chuyển đến", "Thu nhập khác", "Lương", "Chuyển nội bộ", "Chưa phân loại",
    )
    @Test fun allRegistryCategoriesHaveAnExplicitIcon() {
        assertEquals(36, categories.size)
        categories.forEach { assertNotEquals(it, Icons.Rounded.Receipt, CategoryVisuals.icon(it)) }
        assertEquals(Icons.Rounded.Receipt, CategoryVisuals.icon("Danh mục không biết"))
        assertNotEquals(CategoryVisuals.icon("Xăng"), CategoryVisuals.icon("Ăn uống"))
        assertNotEquals(CategoryVisuals.icon("Taxi"), CategoryVisuals.icon("Xăng"))
        assertNotEquals(CategoryVisuals.icon("Cafe"), CategoryVisuals.icon("Ăn uống"))
    }
    @Test fun aliasesAndUnicodeVariantsMatch() {
        listOf("Đi lại" to "Di chuyển", "Xăng xe" to "Xăng", "Y tế" to "Sức khỏe",
            "Điện nước & Net" to "Hoá đơn & Tiện ích", "Thu nhập / Nhận CK" to "Tiền chuyển đến",
            "Hóa đơn điện" to "Hoá đơn điện", "Hoa\u0301 đơn điện" to "Hoá đơn điện").forEach { (alias, canonical) ->
            assertEquals(CategoryVisuals.icon(canonical), CategoryVisuals.icon(alias))
        }
    }
    @Test fun categoryPalettesCoverEveryNameWithoutReusingMoneySemantics() {
        for (palette in listOf(LightCategoryPalette, DarkCategoryPalette)) {
            assertEquals(10, palette.all.toSet().size)
            categories.forEach { name ->
                val color = CategoryVisuals.color(name, palette)
                assertTrue(name, color in palette.all)
                assertNotEquals(name, IncomeGreen, color)
                assertNotEquals(name, ExpenseRed, color)
            }
        }
    }

    @Test fun categoryIconContrastAgainstItsTintIsAtLeastThreeInBothThemes() {
        fun luminance(color: Color): Double {
            fun channel(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
        }
        for ((palette, surfaces) in listOf(LightCategoryPalette to listOf(SurfaceLight, Primary100),
            DarkCategoryPalette to listOf(SurfaceDark, SurfaceElevatedDark))) {
            palette.all.forEach { icon -> surfaces.forEach { surface ->
                val tint = icon.copy(alpha = 0.12f).compositeOver(surface)
                val a = luminance(icon); val b = luminance(tint)
                assertTrue("contrast must be >=3:1", (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05) >= 3.0)
            } }
        }
    }

    @Test fun gridUsesAvailableWidthIncludingFoldAndExpandedBreakpoints() {
        assertEquals(3, CategoryVisuals.columns(304))
        assertEquals(3, CategoryVisuals.columns(344))
        assertEquals(4, CategoryVisuals.columns(360))
        assertEquals(4, CategoryVisuals.columns(412))
        assertEquals(5, CategoryVisuals.columns(600))
        assertEquals(6, CategoryVisuals.columns(690))
    }
}
