package vn.phuong.financebridge

import org.junit.Assert.*
import org.junit.Test
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.screens.transactions.SmartCategoryStatus
import vn.phuong.financebridge.ui.screens.transactions.TransactionSmartStatus

class TransactionSmartStatusTest {
    private val categories = listOf(CategoryChoice("Ăn uống", "CHI"), CategoryChoice("Lương", "THU"))
    private val rule = MemoryRule(
        key = "ACCOUNT:11112222333344445555666677778888",
        identityType = "ACCOUNT",
        label = "Tài khoản đối ứng",
        type = "CHI",
        category = "Ăn uống",
        count = 5,
        total = 5,
        confidence = 1.0,
        level = "STRONG",
        recent = listOf("Ăn uống"),
        lastUsedAt = "2026-10-30",
        pinned = false,
    )
    private val memory = CategoryMemoryIndex("2026-10-30", true, listOf(rule), categories)

    @Test
    fun uncategorizedTransactionAlwaysYieldsUncategorizedStatus() {
        val t = UiTransaction.create("t1", TransactionType.CHI, 50000, "Cafe", category = "Chưa phân loại")
        assertEquals(SmartCategoryStatus.UNCATEGORIZED, TransactionSmartStatus.derive(t, memory))
    }

    @Test
    fun manualOrPreviouslyEditedTransactionYieldsRemembered() {
        val manual = UiTransaction.create("t2", TransactionType.CHI, 50000, "Lunch", category = "Ăn uống", source = "MANUAL")
        assertEquals(SmartCategoryStatus.REMEMBERED, TransactionSmartStatus.derive(manual, null))

        val edited = UiTransaction.create("t3", TransactionType.CHI, 50000, "Dinner", category = "Ăn uống", revision = "r-uuid-4")
        assertEquals(SmartCategoryStatus.REMEMBERED, TransactionSmartStatus.derive(edited, null))
    }

    @Test
    fun bankNotificationWithMatchingPreselectingMemoryYieldsRemembered() {
        val t = UiTransaction.create("t4", TransactionType.CHI, 80000, "Grocery", category = "Ăn uống",
            source = "BANK_NOTIFICATION", revision = "0", memoryKey = "ACCOUNT:11112222333344445555666677778888")
        assertEquals(SmartCategoryStatus.REMEMBERED, TransactionSmartStatus.derive(t, memory))
    }

    @Test
    fun bankNotificationWithoutMatchingMemoryYieldsNeedsCheck() {
        val t = UiTransaction.create("t5", TransactionType.CHI, 80000, "Grocery", category = "Ăn uống",
            source = "BANK_NOTIFICATION", revision = "0", memoryKey = "ACCOUNT:99990000111122223333444455556666")
        assertEquals(SmartCategoryStatus.NEEDS_CHECK, TransactionSmartStatus.derive(t, memory))
    }
}
