package vn.phuong.financebridge

import java.text.Normalizer

object SensitiveFilter {

    private val BLACKLIST_REGEX = Regex(
        pattern = "\\b(otp|smart\\s*otp|soft\\s*otp|ma\\s*xac\\s*thuc|ma\\s*kich\\s*hoat|ma\\s*bao\\s*mat|verification\\s*code|activation\\s*code|passcode|khong\\s*cung\\s*cap\\s*ma|khong\\s*chia\\s*se\\s*ma|dang\\s*nhap|thiet\\s*bi\\s*moi|mat\\s*khau|doi\\s*mat\\s*khau|kich\\s*hoat|sinh\\s*trac\\s*hoc|khoa\\s*the|mo\\s*khoa\\s*the|cvv|so\\s*the|han\\s*muc\\s*tin\\s*dung|bao\\s*tri\\s*he\\s*thong|khuyen\\s*mai|uu\\s*dai)\\b",
        option = RegexOption.IGNORE_CASE
    )

    private val MONEY_SIGNAL_REGEX = Regex(
        pattern = "(VND|VNĐ|[+-]\\s*\\d[\\d.,]{2,})",
        option = RegexOption.IGNORE_CASE
    )

    private val ACCOUNT_SIGNAL_REGEX = Regex(
        pattern = "\\b(so\\s*du|sd|tk|tai\\s*khoan|gd|giao\\s*dich|ps|phat\\s*sinh|chuyen\\s*khoan|thanh\\s*toan)\\b",
        option = RegexOption.IGNORE_CASE
    )

    fun normalizeText(raw: String): String {
        return Normalizer.normalize(raw, Normalizer.Form.NFC)
            .replace('\u00A0', ' ')
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun shouldProcess(title: String, normalizedText: String): Boolean {
        if (normalizedText.isBlank()) return false
        val combinedUnaccented = removeAccents("$title $normalizedText")

        // 1. Chặn cứng OTP, bảo mật, thẻ, quảng cáo
        if (BLACKLIST_REGEX.containsMatchIn(combinedUnaccented)) {
            return false
        }

        // 2. Bắt buộc có dấu hiệu số tiền và biến động tài khoản
        val hasMoney = MONEY_SIGNAL_REGEX.containsMatchIn(normalizedText)
        val hasAccountKeyword = ACCOUNT_SIGNAL_REGEX.containsMatchIn(combinedUnaccented)

        return hasMoney && hasAccountKeyword
    }

    private fun removeAccents(input: String): String {
        val nfd = Normalizer.normalize(input, Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
    }
}

