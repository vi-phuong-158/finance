package vn.phuong.financebridge

import org.junit.Assert.*
import org.junit.Test

class SensitiveFilterTest {
    private val transaction = "GD TK ACCOUNT-TEST +100,000 VND SD 200,000 VND"

    @Test fun blocksSensitiveTitlesEvenWithTransactionSignals() {
        listOf("OTP", "Smart OTP", "verification code", "Mã xác thực", "Mật khẩu", "Đăng nhập",
            "Thiết bị mới", "Sinh trắc học", "Khóa thẻ", "Mở khóa thẻ", "CVV", "Khuyến mại", "Ưu đãi")
            .forEach { title -> assertFalse(title, SensitiveFilter.shouldProcess(title, transaction)) }
    }

    @Test fun blocksSensitiveBodies() {
        assertFalse(SensitiveFilter.shouldProcess("VCB", "$transaction Mã xác thực"))
    }

    @Test fun requiresBothSignals() {
        assertTrue(SensitiveFilter.shouldProcess("VCB", transaction))
        assertFalse(SensitiveFilter.shouldProcess("VCB", "SD TK ACCOUNT-TEST"))
        assertFalse(SensitiveFilter.shouldProcess("VCB", "+100,000 VND"))
        assertFalse(SensitiveFilter.shouldProcess("VCB", ""))
    }
}
