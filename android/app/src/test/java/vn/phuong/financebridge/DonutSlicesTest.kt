package vn.phuong.financebridge

import org.junit.Assert.*
import org.junit.Test
import vn.phuong.financebridge.ui.util.CategoryShareInput
import vn.phuong.financebridge.ui.util.DonutSlices

class DonutSlicesTest {

    @Test
    fun testEmptyCategoriesReturnsEmpty() {
        val slices = DonutSlices.calculate(emptyList())
        assertTrue(slices.isEmpty())
    }

    @Test
    fun testZeroAmountCategoriesReturnsEmpty() {
        val input = listOf(
            CategoryShareInput("Ăn uống", 0L),
            CategoryShareInput("Di chuyển", -100L)
        )
        val slices = DonutSlices.calculate(input)
        assertTrue(slices.isEmpty())
    }

    @Test
    fun testSingleCategoryGives100Percent() {
        val input = listOf(
            CategoryShareInput("Ăn uống", 500_000L)
        )
        val slices = DonutSlices.calculate(input)
        assertEquals(1, slices.size)
        assertEquals("Ăn uống", slices[0].name)
        assertEquals(500_000L, slices[0].amount)
        assertEquals(100, slices[0].percentage)
        assertEquals(360f, slices[0].sweepAngle, 0.01f)
        assertFalse(slices[0].isOther)
    }

    @Test
    fun testFiveCategoriesKeepsAllWithoutOther() {
        val input = listOf(
            CategoryShareInput("Ăn uống", 400_000L),
            CategoryShareInput("Mua sắm", 300_000L),
            CategoryShareInput("Hóa đơn", 150_000L),
            CategoryShareInput("Di chuyển", 100_000L),
            CategoryShareInput("Giải trí", 50_000L)
        )
        val slices = DonutSlices.calculate(input)
        assertEquals(5, slices.size)
        // Check sorted descending
        for (i in 0 until slices.size - 1) {
            assertTrue("Should be descending", slices[i].amount >= slices[i + 1].amount)
        }
        // Total amount matches
        assertEquals(1_000_000L, slices.sumOf { it.amount })
        // Sum of percentages is exactly 100
        assertEquals(100, slices.sumOf { it.percentage })
        // No "Khác" slice
        assertTrue(slices.none { it.isOther })
    }

    @Test
    fun testSixCategoriesKeepsAllSixWithoutOther() {
        val input = listOf(
            CategoryShareInput("Ăn uống", 300_000L),
            CategoryShareInput("Mua sắm", 200_000L),
            CategoryShareInput("Hóa đơn", 150_000L),
            CategoryShareInput("Di chuyển", 150_000L),
            CategoryShareInput("Giải trí", 100_000L),
            CategoryShareInput("Y tế", 100_000L)
        )
        val slices = DonutSlices.calculate(input)
        assertEquals(6, slices.size)
        assertEquals(1_000_000L, slices.sumOf { it.amount })
        assertEquals(100, slices.sumOf { it.percentage })
        assertTrue(slices.none { it.isOther })
    }

    @Test
    fun testTenCategoriesProducesTop5PlusOther() {
        val input = (1..10).map { i ->
            CategoryShareInput("Danh mục $i", i * 100_000L)
        }
        val totalExpected = input.sumOf { it.amount }
        val slices = DonutSlices.calculate(input)

        // Top 5 + 1 "Khác" = 6 slices
        assertEquals(6, slices.size)
        assertEquals(totalExpected, slices.sumOf { it.amount })
        assertEquals(100, slices.sumOf { it.percentage })

        // First 5 are top amounts: 10, 9, 8, 7, 6
        assertEquals("Danh mục 10", slices[0].name)
        assertEquals(1_000_000L, slices[0].amount)
        assertFalse(slices[0].isOther)

        // Sixth slice is "Khác"
        val otherSlice = slices.last()
        assertTrue(otherSlice.isOther)
        assertEquals("Khác", otherSlice.name)
        // Sub-categories should contain remaining 5 items (1..5)
        assertEquals(5, otherSlice.subCategories.size)
        val expectedOtherAmount = (1..5).sumOf { it * 100_000L }
        assertEquals(expectedOtherAmount, otherSlice.amount)
    }

    @Test
    fun testLargestRemainderDistributionSumsExactly100() {
        // Values that produce fractional percentages: 1/3, 1/3, 1/3
        val input = listOf(
            CategoryShareInput("A", 100L),
            CategoryShareInput("B", 100L),
            CategoryShareInput("C", 100L)
        )
        val slices = DonutSlices.calculate(input)
        assertEquals(3, slices.size)
        // 33.333% each -> floors are 33, 33, 33 (sum 99). One item gets +1 -> 34, 33, 33 (sum 100)
        assertEquals(100, slices.sumOf { it.percentage })
        assertEquals(300L, slices.sumOf { it.amount })
    }

    @Test
    fun testIncomeCategoriesSupport() {
        val incomeList = listOf(
            CategoryShareInput("Lương", 25_000_000L),
            CategoryShareInput("Thưởng", 5_000_000L),
            CategoryShareInput("Đầu tư", 2_000_000L)
        )
        val slices = DonutSlices.calculate(incomeList)
        assertEquals(3, slices.size)
        assertEquals(32_000_000L, slices.sumOf { it.amount })
        assertEquals(100, slices.sumOf { it.percentage })
        assertEquals("Lương", slices[0].name)
        assertEquals(25_000_000L, slices[0].amount)
    }
}
