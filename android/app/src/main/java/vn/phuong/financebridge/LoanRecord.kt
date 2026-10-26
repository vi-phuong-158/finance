package vn.phuong.financebridge

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID

enum class LoanOperation(val label: String) {
    CREATE_LOAN("Khoản vay mới"), DISBURSEMENT("Giải ngân thêm"), PRINCIPAL_PAYMENT("Thu gốc"),
    INTEREST_PAYMENT("Thu lãi"), UPDATE_INFO("Sửa thông tin")
}

data class LoanMutation(val requestId: String, val operation: LoanOperation, val loanId: String?,
    val borrowerName: String?, val amount: Long?, val annualRate: Double?, val date: String?, val maturity: String?, val note: String)

data class LoanFormInput(val requestId: String, val operation: LoanOperation, val loanId: String? = null,
    val borrowerName: String = "", val amount: String = "", val ratePercent: String = "", val date: String = "", val maturity: String = "", val note: String = "") {
    fun parse(today: LocalDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))): LoanMutation {
        require(UUID.fromString(requestId).version() == 4) { "Yêu cầu chưa hợp lệ. Hãy mở lại biểu mẫu." }
        val creating = operation == LoanOperation.CREATE_LOAN
        val disbursing = creating || operation == LoanOperation.DISBURSEMENT
        val editing = operation == LoanOperation.UPDATE_INFO
        require(creating || (!loanId.isNullOrBlank() && loanId.length <= 200)) { "Chưa chọn khoản vay." }
        require(note.toByteArray(Charsets.UTF_8).size <= 1024) { "Ghi chú quá dài." }
        val name = if (creating || editing) borrowerName.trim().also { require(it.isNotEmpty() && it.length <= 200) { "Nhập tên người vay, tối đa 200 ký tự." } } else null
        val money = if (!editing) amount.toLongOrNull().also { require(it != null && it in 1..1_000_000_000_000L) { "Nhập số tiền nguyên dương, tối đa 1.000 tỷ đồng." } } else null
        val format = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
        val eventDate = if (!editing) runCatching { LocalDate.parse(date, format) }.getOrNull().also {
            require(it != null && it.year >= 1970 && !it.isAfter(today)) { "Ngày giao dịch phải hợp lệ và không sau hôm nay." }
        } else null
        val maturityDate = if (disbursing && maturity.isNotBlank()) runCatching { LocalDate.parse(maturity, format) }.getOrNull().also {
            require(it != null && !it.isBefore(eventDate) && it.year <= 2200) { "Ngày đáo hạn chưa hợp lệ hoặc trước ngày giải ngân." }
        } else null
        val rate = if (disbursing) ratePercent.replace(',', '.').toBigDecimalOrNull().also {
            require(it != null && it >= java.math.BigDecimal.ZERO && it <= java.math.BigDecimal.valueOf(100)) { "Lãi suất năm phải từ 0 đến 100%." }
        }!!.toDouble() / 100 else null
        return LoanMutation(requestId, operation, if (creating) null else loanId, name, money, rate, eventDate?.toString(),
            if (disbursing) maturityDate?.toString().orEmpty() else null, note.trim())
    }
}
