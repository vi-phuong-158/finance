package vn.phuong.financebridge

import org.junit.Assert.*
import org.junit.Test

class TransactionEditTest {
    @Test fun editsValidateCategoryTypeRevisionAndNoteWithoutChangingMoney() {
        val choices = listOf(CategoryChoice("Ăn uống", "CHI"), CategoryChoice("Lương", "THU"))
        val draft = TransactionEditInput("11111111-1111-4111-8111-111111111111", "bank:synthetic", "0", "Ăn uống", "Synthetic", false, "CHI")
        draft.validate(choices)
        listOf(draft.copy(type = "THU"), draft.copy(category = "Unknown"), draft.copy(note = "á".repeat(513)), draft.copy(baseRevision = "bad"))
            .forEach { assertTrue(runCatching { it.validate(choices) }.isFailure) }
    }
}
