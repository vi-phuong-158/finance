package vn.phuong.financebridge

import org.junit.Assert.*
import org.junit.Test
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.screens.transactions.TransactionOptimisticUpdate

class TransactionOptimisticUpdateTest {
    private fun sampleTransaction(id: String = "tx-1", type: TransactionType = TransactionType.CHI) =
        UiTransaction.create(
            id = id,
            type = type,
            amount = 150000,
            title = "Sample Payment",
            category = "Ăn uống",
            note = "Old note",
            excluded = false,
            revision = "r1",
            pendingSync = false,
        )

    @Test
    fun applyUpdatesAllEditedFieldsAndMarksPendingSync() {
        val original = sampleTransaction()
        val fields = EditFields(
            type = "CHI",
            category = "Di chuyển",
            note = "Taxi Mai Linh",
            excluded = true,
        )
        val updated = TransactionOptimisticUpdate.apply(original, fields)
        assertEquals(TransactionType.CHI, updated.type)
        assertEquals("Di chuyển", updated.category)
        assertEquals("Taxi Mai Linh", updated.note)
        assertTrue(updated.excluded)
        assertTrue(updated.pendingSync)
        assertEquals(original.id, updated.id)
        assertEquals(original.amount, updated.amount)
        assertEquals(original.revision, updated.revision)
    }

    @Test
    fun applyUpdatingDirectionRecalculatesFormattedAmountSign() {
        val original = sampleTransaction(type = TransactionType.CHI)
        assertTrue(original.formattedAmount.startsWith("-"))

        val incomeFields = EditFields(
            type = "THU",
            category = "Thu nhập",
            note = "Hoàn tiền",
            excluded = false,
        )
        val updated = TransactionOptimisticUpdate.apply(original, incomeFields)
        assertEquals(TransactionType.THU, updated.type)
        assertTrue(updated.formattedAmount.startsWith("+"))
        assertTrue(updated.pendingSync)
    }

    @Test
    fun applyToRowsOnlyModifiesTargetRowAndPreservesOthers() {
        val row1 = sampleTransaction(id = "tx-1")
        val row2 = sampleTransaction(id = "tx-2")
        val row3 = sampleTransaction(id = "tx-3")
        val rows = listOf(row1, row2, row3)

        val fields = EditFields(
            type = "CHI",
            category = "Mua sắm",
            note = "Siêu thị",
            excluded = false,
        )
        val result = TransactionOptimisticUpdate.applyToRows(rows, "tx-2", fields)

        assertEquals(3, result.size)
        assertEquals(row1, result[0])
        assertFalse(result[0].pendingSync)

        assertEquals("tx-2", result[1].id)
        assertEquals("Mua sắm", result[1].category)
        assertEquals("Siêu thị", result[1].note)
        assertTrue(result[1].pendingSync)

        assertEquals(row3, result[2])
        assertFalse(result[2].pendingSync)
    }
}
