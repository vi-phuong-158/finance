package vn.phuong.financebridge.ui.util

data class CategoryShareInput(
    val name: String,
    val amount: Long,
    val count: Int = 0,
)

data class DonutSlice(
    val name: String,
    val amount: Long,
    val percentage: Int, // 0..100, sum == 100 when total > 0
    val sweepAngle: Float, // for Canvas drawArc (0f..360f)
    val isOther: Boolean = false,
    val subCategories: List<CategoryShareInput> = emptyList(),
)

/**
 * Pure JVM utility for Donut chart calculations according to UI V2 HANDOFF §4:
 * - Sorts categories descending by amount.
 * - Top 5 + "Khác" (if <= 6 categories in total, keep all <= 6 without "Khác").
 * - Arithmetic calculated using Long.
 * - Percentage rounding using Largest-Remainder Method (Hare-Niemeyer) so sum of percentages is exactly 100%.
 */
object DonutSlices {
    fun calculate(
        items: List<CategoryShareInput>,
        maxTop: Int = 5,
        otherLabel: String = "Khác",
    ): List<DonutSlice> {
        val positive = items.filter { it.amount > 0 }.sortedByDescending { it.amount }
        if (positive.isEmpty()) return emptyList()

        val totalAmount = positive.sumOf { it.amount }
        if (totalAmount <= 0L) return emptyList()

        // Rule: <= 6 categories, keep all without "Khác"
        val (mainItems, otherItems) = if (positive.size <= maxTop + 1) {
            positive to emptyList()
        } else {
            positive.take(maxTop) to positive.drop(maxTop)
        }

        data class RawSlice(
            val name: String,
            val amount: Long,
            val isOther: Boolean,
            val subCategories: List<CategoryShareInput>,
            val exactPct: Double,
            var intPct: Int,
            val remainder: Double,
            val originalIndex: Int,
        )

        val rawList = mutableListOf<RawSlice>()
        var index = 0

        for (item in mainItems) {
            val exact = (item.amount.toDouble() / totalAmount) * 100.0
            val floor = exact.toInt()
            rawList.add(
                RawSlice(
                    name = item.name,
                    amount = item.amount,
                    isOther = false,
                    subCategories = emptyList(),
                    exactPct = exact,
                    intPct = floor,
                    remainder = exact - floor,
                    originalIndex = index++,
                )
            )
        }

        if (otherItems.isNotEmpty()) {
            val otherTotal = otherItems.sumOf { it.amount }
            val exact = (otherTotal.toDouble() / totalAmount) * 100.0
            val floor = exact.toInt()
            rawList.add(
                RawSlice(
                    name = otherLabel,
                    amount = otherTotal,
                    isOther = true,
                    subCategories = otherItems,
                    exactPct = exact,
                    intPct = floor,
                    remainder = exact - floor,
                    originalIndex = index,
                )
            )
        }

        // Largest-Remainder Method: distribute (100 - sum of floors) to items with largest remainder
        val currentSum = rawList.sumOf { it.intPct }
        val diff = 100 - currentSum
        if (diff > 0) {
            val sortedByRemainder = rawList.sortedWith(
                compareByDescending<RawSlice> { it.remainder }
                    .thenBy { it.originalIndex }
            )
            for (i in 0 until diff.coerceAtMost(sortedByRemainder.size)) {
                sortedByRemainder[i].intPct += 1
            }
        }

        return rawList.map { raw ->
            val sweep = ((raw.amount.toDouble() / totalAmount) * 360.0).toFloat()
            DonutSlice(
                name = raw.name,
                amount = raw.amount,
                percentage = raw.intPct,
                sweepAngle = sweep,
                isOther = raw.isOther,
                subCategories = raw.subCategories,
            )
        }
    }
}
