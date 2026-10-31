package vn.phuong.financebridge.ui.screens.transactions

import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.util.FinanceFormatters

object TransactionDetailPresentation {
    fun amountWithSign(type: TransactionType, amount: Long): String =
        FinanceFormatters.formatVnd(amount, includeSign = true, isIncome = type == TransactionType.THU)

    fun amountWithSign(type: String, amount: Long): String {
        val isIncome = when (type.trim().uppercase()) {
            "THU" -> true
            "CHI" -> false
            else -> null
        }
        return FinanceFormatters.formatVnd(amount, includeSign = true, isIncome = isIncome)
    }

    fun sourceLabel(source: String?): String = when (source) {
        "MONEYLOVER" -> "Nhập lịch sử"
        "MANUAL" -> "Ghi chép thủ công"
        else -> "Thông báo ngân hàng"
    }

    fun reportStatusLabel(excluded: Boolean): String =
        if (excluded) "Không tính vào báo cáo" else "Tính vào báo cáo"
}

fun amountWithSign(type: TransactionType, amount: Long): String =
    TransactionDetailPresentation.amountWithSign(type, amount)

fun amountWithSign(type: String, amount: Long): String =
    TransactionDetailPresentation.amountWithSign(type, amount)

fun sourceLabel(source: String?): String =
    TransactionDetailPresentation.sourceLabel(source)

fun reportStatusLabel(excluded: Boolean): String =
    TransactionDetailPresentation.reportStatusLabel(excluded)
