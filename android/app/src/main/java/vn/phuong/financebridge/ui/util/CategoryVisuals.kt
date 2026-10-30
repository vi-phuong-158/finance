package vn.phuong.financebridge.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import vn.phuong.financebridge.ui.theme.CategoryPalette
import vn.phuong.financebridge.ui.theme.FinanceColors
import java.text.Normalizer
import java.util.Locale

object CategoryVisuals {
    private fun key(category: String) = Normalizer.normalize(category.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)

    fun icon(category: String): ImageVector = when (key(category)) {
        "các chi phí khác" -> Icons.Rounded.MoreHoriz
        "ăn uống", "ăn tiệm", "ăn vặt" -> Icons.Rounded.Restaurant
        "cafe", "cà phê" -> Icons.Rounded.LocalCafe
        "xăng", "xăng xe" -> Icons.Rounded.LocalGasStation
        "taxi" -> Icons.Rounded.LocalTaxi
        "di chuyển", "đi lại", "bảo dưỡng xe" -> Icons.Rounded.DirectionsCar
        "mua sắm", "siêu thị" -> Icons.Rounded.ShoppingCart
        "lương", "tiền chuyển đến", "thu nhập khác", "thu nhập / nhận ck" -> Icons.Rounded.Payments
        "con cái" -> Icons.Rounded.ChildCare
        "hoá đơn nước", "hóa đơn nước", "hoá đơn điện", "hóa đơn điện", "hoá đơn điện thoại", "hóa đơn điện thoại", "hoá đơn internet", "hóa đơn internet", "hoá đơn & tiện ích", "hóa đơn & tiện ích", "điện nước & net" -> Icons.Rounded.ElectricBolt
        "quà tặng & quyên góp", "hiếu hỷ" -> Icons.Rounded.CardGiftcard
        "gia đình", "dịch vụ gia đình", "đồ gia dụng" -> Icons.Rounded.Home
        "sức khỏe", "sức khoẻ", "khám sức khỏe", "khám sức khoẻ", "y tế" -> Icons.Rounded.MedicalServices
        "giải trí" -> Icons.Rounded.SportsEsports
        "bảo hiểm" -> Icons.Rounded.Security
        "đồ dùng cá nhân" -> Icons.Rounded.Person
        "làm đẹp" -> Icons.Rounded.Face
        "trả lãi" -> Icons.Rounded.Percent
        "dịch vụ trực tuyến" -> Icons.Rounded.Language
        "cho vay" -> Icons.Rounded.AccountBalance
        "chuyển nội bộ" -> Icons.Rounded.SwapHoriz
        "chưa phân loại" -> Icons.Rounded.HelpOutline
        else -> Icons.Rounded.Receipt
    }

    fun color(category: String, colors: FinanceColors): Color = color(category, colors.categories)

    fun color(category: String, palette: CategoryPalette): Color = with(palette) {
        when (icon(category)) {
            Icons.Rounded.Restaurant, Icons.Rounded.LocalCafe -> food
            Icons.Rounded.LocalGasStation, Icons.Rounded.LocalTaxi, Icons.Rounded.DirectionsCar -> travel
            Icons.Rounded.ElectricBolt, Icons.Rounded.Language -> bills
            Icons.Rounded.ShoppingCart, Icons.Rounded.Person -> shopping
            Icons.Rounded.ChildCare, Icons.Rounded.Home -> family
            Icons.Rounded.MedicalServices, Icons.Rounded.Face -> health
            Icons.Rounded.Security, Icons.Rounded.Percent, Icons.Rounded.AccountBalance -> finance
            Icons.Rounded.CardGiftcard, Icons.Rounded.SportsEsports -> leisure
            Icons.Rounded.Payments -> earnings
            else -> neutral
        }
    }

    fun columns(widthDp: Int): Int = when {
        widthDp < 360 -> 3
        widthDp < 600 -> 4
        widthDp < 690 -> 5
        else -> 6
    }
}
