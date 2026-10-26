package vn.phuong.financebridge.ui.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

enum class FinancePeriod(val label: String) {
    TODAY("Hôm nay"), WEEK("7 ngày"), MONTH("Tháng này"), PREVIOUS_MONTH("Tháng trước"), YEAR("Năm nay"), CUSTOM("Tùy chọn");

    fun query(from: String = "", to: String = "", today: LocalDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))): Map<String, String> {
        val range = when (this) {
            TODAY -> today to today
            WEEK -> today.minusDays(6) to today
            MONTH -> today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
            PREVIOUS_MONTH -> today.minusMonths(1).withDayOfMonth(1).let { it to it.withDayOfMonth(it.lengthOfMonth()) }
            YEAR -> today.withDayOfYear(1) to LocalDate.of(today.year, 12, 31)
            CUSTOM -> {
                val format = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
                LocalDate.parse(from, format) to LocalDate.parse(to, format)
            }
        }
        require(!range.first.isAfter(range.second) && range.first.year >= 1900 && range.second.year <= 9999)
        return mapOf("from" to range.first.toString(), "to" to range.second.toString())
    }
}
