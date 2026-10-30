package vn.phuong.financebridge.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

    fun color(category: String, colors: FinanceColors): Color = when (icon(category)) {
        Icons.Rounded.Payments, Icons.Rounded.AccountBalance -> colors.income
        Icons.Rounded.Restaurant, Icons.Rounded.LocalCafe, Icons.Rounded.CardGiftcard -> colors.expense
        Icons.Rounded.LocalGasStation, Icons.Rounded.LocalTaxi, Icons.Rounded.DirectionsCar -> colors.info
        Icons.Rounded.HelpOutline, Icons.Rounded.MoreHoriz, Icons.Rounded.Receipt -> colors.textSecondary
        else -> colors.primary
    }
}
