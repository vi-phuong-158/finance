package vn.phuong.financebridge

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.screens.transactions.TransactionDetailPresentation
import vn.phuong.financebridge.ui.screens.transactions.amountWithSign
import vn.phuong.financebridge.ui.screens.transactions.reportStatusLabel
import vn.phuong.financebridge.ui.screens.transactions.sourceLabel

class TransactionDetailPresentationTest {

    @Test
    fun amountWithSignFormatsIncomeAndExpenseCorrectly() {
        assertEquals("+1.250.000\u00A0đ", amountWithSign(TransactionType.THU, 1250000L))
        assertEquals("-300.000\u00A0đ", amountWithSign(TransactionType.CHI, 300000L))
        assertEquals("+0\u00A0đ", amountWithSign(TransactionType.THU, 0L))
        assertEquals("-0\u00A0đ", amountWithSign(TransactionType.CHI, 0L))

        // Supports negative amounts (format uses absolute value)
        assertEquals("-500.000\u00A0đ", amountWithSign(TransactionType.CHI, -500000L))
        assertEquals("+500.000\u00A0đ", amountWithSign(TransactionType.THU, -500000L))
    }

    @Test
    fun amountWithSignSupportsStringTypes() {
        assertEquals("+45.000\u00A0đ", amountWithSign("THU", 45000L))
        assertEquals("+45.000\u00A0đ", amountWithSign("thu", 45000L))
        assertEquals("-85.000\u00A0đ", amountWithSign("CHI", 85000L))
        assertEquals("-85.000\u00A0đ", amountWithSign("chi", 85000L))
        assertEquals("+10.000\u00A0đ", amountWithSign("OTHER", 10000L))
    }

    @Test
    fun sourceLabelMapsKnownSourcesAndDefaultsToBankNotification() {
        assertEquals("Nhập lịch sử", sourceLabel("MONEYLOVER"))
        assertEquals("Ghi chép thủ công", sourceLabel("MANUAL"))
        assertEquals("Thông báo ngân hàng", sourceLabel("BANK_NOTIFICATION"))
        assertEquals("Thông báo ngân hàng", sourceLabel(null))
        assertEquals("Thông báo ngân hàng", sourceLabel(""))
        assertEquals("Thông báo ngân hàng", sourceLabel("UNKNOWN_SOURCE"))
    }

    @Test
    fun reportStatusLabelMapsExcludedFlagCorrectly() {
        assertEquals("Không tính vào báo cáo", reportStatusLabel(true))
        assertEquals("Tính vào báo cáo", reportStatusLabel(false))
    }

    @Test
    fun objectMethodsMatchTopLevelAliases() {
        assertEquals(
            TransactionDetailPresentation.amountWithSign(TransactionType.THU, 100000L),
            amountWithSign(TransactionType.THU, 100000L)
        )
        assertEquals(
            TransactionDetailPresentation.sourceLabel("MANUAL"),
            sourceLabel("MANUAL")
        )
        assertEquals(
            TransactionDetailPresentation.reportStatusLabel(true),
            reportStatusLabel(true)
        )
    }
}
