package vn.phuong.financebridge.ui.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object FinanceFormatters {
    private val vietnamLocale = Locale("vi", "VN")
    private val vietnamTimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

    private val vndNumberFormat = DecimalFormat("#,###", DecimalFormatSymbols(vietnamLocale).apply {
        groupingSeparator = '.'
    })

    fun formatVnd(amount: Long, includeSign: Boolean = false, isIncome: Boolean? = null): String {
        val absAmount = kotlin.math.abs(amount)
        val formattedNumber = vndNumberFormat.format(absAmount)
        val sign = when {
            !includeSign -> ""
            isIncome == true -> "+ "
            isIncome == false -> "- "
            amount > 0 -> "+ "
            amount < 0 -> "- "
            else -> ""
        }
        return "$sign$formattedNumber ₫"
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

    fun parseIsoDateToMillis(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { timeZone = vietnamTimeZone }
                val parsed = sdf.parse(isoString)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }
}
