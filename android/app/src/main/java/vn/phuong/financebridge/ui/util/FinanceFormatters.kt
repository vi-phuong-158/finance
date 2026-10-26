package vn.phuong.financebridge.ui.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object FinanceFormatters {
    private val vietnamLocale = Locale("vi", "VN")
    private val vietnamTimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

    private val vndNumberFormat = DecimalFormat("#,###", DecimalFormatSymbols(vietnamLocale).apply {
        groupingSeparator = '.'
    })

    @Synchronized
    fun formatVnd(amount: Long, includeSign: Boolean = false, isIncome: Boolean? = null): String {
        val absAmount = kotlin.math.abs(amount)
        val formattedNumber = vndNumberFormat.format(absAmount)
        val sign = when {
            !includeSign -> if (amount < 0) "-" else ""
            isIncome == true -> "+"
            isIncome == false -> "-"
            amount > 0 -> "+"
            amount < 0 -> "-"
            else -> ""
        }
        return "$sign$formattedNumber\u00A0đ"
    }

    fun formatFriendlyDate(epochMillis: Long): String {
        val nowCal = Calendar.getInstance(vietnamTimeZone)
        val targetCal = Calendar.getInstance(vietnamTimeZone).apply { timeInMillis = epochMillis }

        val isSameYear = nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR)
        val isToday = isSameYear && nowCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)

        val yesterdayCal = Calendar.getInstance(vietnamTimeZone).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val isYesterday = yesterdayCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
            yesterdayCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)

        val timeFormat = SimpleDateFormat("HH:mm", vietnamLocale).apply { timeZone = vietnamTimeZone }
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", vietnamLocale).apply { timeZone = vietnamTimeZone }

        return when {
            isToday -> "Hôm nay, ${timeFormat.format(Date(epochMillis))}"
            isYesterday -> "Hôm qua, ${timeFormat.format(Date(epochMillis))}"
            else -> dateFormat.format(Date(epochMillis))
        }
    }

    fun formatIsoDate(date: String): String = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))

    fun formatAnnualRate(rate: Double): String {
        require(rate.isFinite() && rate >= 0 && rate <= 1)
        return java.math.BigDecimal.valueOf(rate).multiply(java.math.BigDecimal.valueOf(100))
            .stripTrailingZeros().toPlainString().replace('.', ',') + "%/năm"
    }

    fun parseIsoDateToMillis(isoString: String?): Long {
        require(!isoString.isNullOrBlank()) { "INVALID_DATE" }
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        if (isoString.length == 10) return LocalDate.parse(isoString).atStartOfDay(zone).toInstant().toEpochMilli()
        return runCatching { OffsetDateTime.parse(isoString).toInstant().toEpochMilli() }.getOrElse {
            LocalDateTime.parse(isoString).atZone(zone).toInstant().toEpochMilli()
        }
    }
}
