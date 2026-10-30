package vn.phuong.financebridge.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Receipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

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
}
