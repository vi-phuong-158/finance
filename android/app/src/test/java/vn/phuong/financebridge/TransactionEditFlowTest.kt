package vn.phuong.financebridge

import org.junit.Assert.*
import org.junit.Test
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.screens.transactions.TransactionEditFlow

class TransactionEditFlowTest {
    private fun shown(revision: String = "r1") = UiTransaction.create("fixture", TransactionType.CHI, 120000,
        "Fixture transaction", category = "Ăn uống", timestamp = 1700000000000, revision = revision, note = "Original")

    @Test fun formOpensWithShownDraftButSaveWaitsForTheRevisionCheck() {
        val t = shown(); val f = TransactionEditFlow.fields(t)
        val pending = TransactionEditFlow.pending(t, f)
        assertEquals(t, pending.base); assertEquals(f, pending.fields); assertFalse(pending.canSave)
        val checked = TransactionEditFlow.online(t, t, f, true)
        assertTrue(checked.canSave); assertEquals("r1", checked.base.revision)
    }

    @Test fun changedRevisionUpdatesBaseWithoutOverwritingFieldsTypedDuringRead() {
        val t = shown(); val draft = TransactionEditFlow.fields(t).copy(note = "Typing during read", category = "Gia đình")
        val latest = t.copy(revision = "r2", category = "Taxi", note = "Changed elsewhere", excluded = true)
        val checked = TransactionEditFlow.online(t, latest, draft, true)
        assertEquals("r2", checked.base.revision); assertEquals("Typing during read", checked.fields.note)
        assertEquals("Gia đình", checked.fields.category); assertTrue(checked.fields.excluded)
        assertTrue(checked.canSave); assertNotNull(checked.message)
    }

    @Test fun failedReadUsesTheExistingOfflineRevisionGuardExactly() {
        val t = shown(); val f = TransactionEditFlow.fields(t)
        assertTrue(TransactionEditFlow.offline(t, f, null).canSave)
        assertTrue(TransactionEditFlow.offline(t, f, "r1").canSave)
        assertFalse(TransactionEditFlow.offline(t, f, "newer-synced").canSave)
        assertEquals(f, TransactionEditFlow.offline(t, f, "newer-synced").fields)
    }

    @Test fun deletedMismatchedOrDisabledServerTransactionNeverAllowsSave() {
        val t = shown(); val f = TransactionEditFlow.fields(t)
        assertFalse(TransactionEditFlow.online(t, null, f, true).canSave)
        assertFalse(TransactionEditFlow.online(t, t.copy(id = "other"), f, true).canSave)
        assertFalse(TransactionEditFlow.online(t, t, f, false).canSave)
    }
}
