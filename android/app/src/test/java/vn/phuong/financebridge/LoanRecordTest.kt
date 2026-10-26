package vn.phuong.financebridge

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LoanRecordTest {
    private val today = LocalDate.of(2024, 4, 26)
    private fun input() = LoanFormInput("11111111-1111-4111-8111-111111111111", LoanOperation.CREATE_LOAN,
        borrowerName = "Synthetic borrower", amount = "12000000", ratePercent = "5,9", date = "29/02/2024", maturity = "28/02/2025")

    @Test fun parsesVietnameseRateAndCalendarWithoutCreatingHistory() {
        val draft = input().parse(today)
        assertEquals(0.059, draft.annualRate!!, 0.00000001)
        assertEquals(12000000L, draft.amount)
        assertEquals("2024-02-29", draft.date)
        assertNull(draft.loanId)
        assertEquals("2025-02-28", draft.maturity)
    }

    @Test fun rejectsInvalidAmountFutureDateMaturityRateAndMissingBorrower() {
        listOf(input().copy(amount = "0"), input().copy(amount = "1.5"), input().copy(date = "30/02/2024"),
            input().copy(date = "27/04/2024"), input().copy(maturity = "01/01/2024"), input().copy(ratePercent = "101"),
            input().copy(borrowerName = "")).forEach { assertTrue(runCatching { it.parse(today) }.isFailure) }
    }

    @Test fun editAndReceiptShapesRequireOnlyTheirOwnBusinessFields() {
        val edit = LoanFormInput(input().requestId, LoanOperation.UPDATE_INFO, "LN-A", "New name", note = "Synthetic").parse(today)
        assertNull(edit.amount); assertNull(edit.date); assertNull(edit.annualRate)
        val receipt = LoanFormInput(input().requestId, LoanOperation.PRINCIPAL_PAYMENT, "LN-A", amount = "50000", date = "25/04/2024").parse(today)
        assertEquals(50000L, receipt.amount); assertNull(receipt.borrowerName)
    }

    @Test fun queuedNewMutationStatusesRetainPayloadUntilRecognizedSuccess() {
        assertEquals("SYNCED", manualSyncState(200, "SAVED_LOAN_EVENT"))
        assertEquals("SYNCED", manualSyncState(200, "SAVED_TRANSACTION_EDIT"))
        assertEquals("BLOCKED", manualSyncState(503, "LOAN_WRITES_DISABLED"))
        assertEquals("BLOCKED", manualSyncState(503, "TRANSACTION_EDITS_DISABLED"))
        assertEquals("PENDING", manualSyncState(503, "LOAN_RETRY_REQUIRED"))
    }
}
