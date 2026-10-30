package vn.phuong.financebridge.ui.screens.transactions

import vn.phuong.financebridge.EditFields
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.util.FinanceFormatters

object TransactionOptimisticUpdate {
    fun apply(original: UiTransaction, fields: EditFields): UiTransaction {
        val newType = TransactionType.valueOf(fields.type)
        val formattedAmount = FinanceFormatters.formatVnd(
            amount = original.amount,
            includeSign = true,
            isIncome = newType == TransactionType.THU,
        )
        return original.copy(
            type = newType,
            formattedAmount = formattedAmount,
            category = fields.category,
            note = fields.note,
            excluded = fields.excluded,
            pendingSync = true,
        )
    }

    fun applyToRows(
        rows: List<UiTransaction>,
        transactionId: String,
        fields: EditFields,
    ): List<UiTransaction> = rows.map { if (it.id == transactionId) apply(it, fields) else it }
}
