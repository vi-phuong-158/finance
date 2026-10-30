package vn.phuong.financebridge.ui.screens.transactions

import org.json.JSONObject
import vn.phuong.financebridge.EditFields
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiTransaction
import vn.phuong.financebridge.ui.util.FinanceFormatters

data class PendingEditState(
    val targetId: String,
    val state: String,
    val fields: EditFields? = null,
)

object TransactionOptimisticUpdate {
    fun parseEditPayload(payload: String?): EditFields? = runCatching {
        if (payload.isNullOrBlank()) null
        else {
            val json = JSONObject(payload)
            EditFields(
                type = json.getString("type"),
                category = json.getString("category"),
                note = json.optString("note", ""),
                excluded = json.optBoolean("excluded", false),
            )
        }
    }.getOrNull()

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
            blocked = false,
        )
    }

    fun applyToRows(
        rows: List<UiTransaction>,
        transactionId: String,
        fields: EditFields,
    ): List<UiTransaction> = rows.map { if (it.id == transactionId) apply(it, fields) else it }

    fun overlayPendingEdits(
        serverRows: List<UiTransaction>,
        pendingEdits: Map<String, PendingEditState>,
    ): List<UiTransaction> = serverRows.map { row ->
        val pending = pendingEdits[row.id]
        when {
            pending == null -> row.copy(pendingSync = false, blocked = false)
            pending.state == "BLOCKED" -> row.copy(pendingSync = false, blocked = true)
            pending.fields != null && (pending.state == "PENDING" || pending.state == "SYNCING") ->
                apply(row, pending.fields).copy(blocked = false)
            else -> row.copy(pendingSync = true, blocked = false)
        }
    }
}
