package vn.phuong.financebridge

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.screens.transactions.PendingEditState
import vn.phuong.financebridge.ui.screens.transactions.TransactionOptimisticUpdate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

    @Test
    fun applyForInternalTransferCategoryAlwaysForcesExcludedToTrue() {
        val original = sampleTransaction()
        val fields = EditFields(
            type = "CHI",
            category = "Chuyển nội bộ",
            note = "Chuyển tiền tiết kiệm",
            excluded = false,
        )
        val updated = TransactionOptimisticUpdate.apply(original, fields)
        assertEquals("Chuyển nội bộ", updated.category)
        assertTrue(updated.excluded)
        assertTrue(updated.pendingSync)
    }

    @Test
    fun parseEditPayloadParsesValidJsonAndReturnsNullForInvalid() {
        val json = """{"type":"CHI","category":"Ăn uống","note":"Phở","excluded":false}"""
        val parsed = TransactionOptimisticUpdate.parseEditPayload(json)
        assertNotNull(parsed)
        assertEquals("CHI", parsed?.type)
        assertEquals("Ăn uống", parsed?.category)
        assertEquals("Phở", parsed?.note)
        assertEquals(false, parsed?.excluded)

        assertNull(TransactionOptimisticUpdate.parseEditPayload(null))
        assertNull(TransactionOptimisticUpdate.parseEditPayload(""))
        assertNull(TransactionOptimisticUpdate.parseEditPayload("not-json"))
    }

    @Test
    fun overlayPendingEditsWithPendingMutationAppliesFieldsAndSetsPendingSync() {
        val serverRow = sampleTransaction("tx-1")
        val pending = mapOf(
            "tx-1" to PendingEditState(
                targetId = "tx-1",
                state = "PENDING",
                fields = EditFields(type = "CHI", category = "Di chuyển", note = "Grab", excluded = true),
            ),
        )
        val result = TransactionOptimisticUpdate.overlayPendingEdits(listOf(serverRow), pending)
        assertEquals(1, result.size)
        val row = result[0]
        assertEquals("Di chuyển", row.category)
        assertEquals("Grab", row.note)
        assertTrue(row.excluded)
        assertTrue(row.pendingSync)
        assertFalse(row.blocked)
    }

    @Test
    fun overlayPendingEditsWithNoPendingMutationPreservesServerRow() {
        val serverRow = sampleTransaction("tx-1")
        val result = TransactionOptimisticUpdate.overlayPendingEdits(listOf(serverRow), emptyMap())
        assertEquals(1, result.size)
        val row = result[0]
        assertEquals("Ăn uống", row.category)
        assertEquals("Old note", row.note)
        assertFalse(row.pendingSync)
        assertFalse(row.blocked)
    }

    @Test
    fun overlayPendingEditsWithBlockedMutationPreservesServerFieldsAndSetsBlocked() {
        val serverRow = sampleTransaction("tx-1")
        val pending = mapOf(
            "tx-1" to PendingEditState(
                targetId = "tx-1",
                state = "BLOCKED",
                fields = EditFields(type = "CHI", category = "Mua sắm", note = "Draft note", excluded = true),
            ),
        )
        val result = TransactionOptimisticUpdate.overlayPendingEdits(listOf(serverRow), pending)
        assertEquals(1, result.size)
        val row = result[0]
        assertEquals("Ăn uống", row.category)
        assertEquals("Old note", row.note)
        assertFalse(row.excluded)
        assertFalse(row.pendingSync)
        assertTrue(row.blocked)
    }

    @Test
    fun overlayPendingEditsWithPendingEditNotInPageLeavesServerRowsUnmodified() {
        val row1 = sampleTransaction("tx-1")
        val row2 = sampleTransaction("tx-2")
        val pending = mapOf(
            "tx-999" to PendingEditState(
                targetId = "tx-999",
                state = "PENDING",
                fields = EditFields(type = "CHI", category = "Du lịch", note = "Khách sạn", excluded = false),
            ),
        )
        val result = TransactionOptimisticUpdate.overlayPendingEdits(listOf(row1, row2), pending)
        assertEquals(2, result.size)
        assertEquals("tx-1", result[0].id)
        assertEquals("Ăn uống", result[0].category)
        assertFalse(result[0].pendingSync)
        assertFalse(result[0].blocked)
        assertEquals("tx-2", result[1].id)
        assertEquals("Ăn uống", result[1].category)
        assertFalse(result[1].pendingSync)
        assertFalse(result[1].blocked)
    }
}
