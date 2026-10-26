package vn.phuong.financebridge.ui.model

import vn.phuong.financebridge.ui.util.FinanceFormatters

enum class TransactionType {
    THU,
    CHI
}

data class UiTransaction(
    val id: String,
    val type: TransactionType,
    val amount: Long,
    val formattedAmount: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val accountOrBank: String,
    val formattedDate: String,
    val timestamp: Long,
    val source: String,
    val isDuplicateCandidate: Boolean = false,
    val revision: String = "0",
    val note: String = "",
    val excluded: Boolean = false,
) {
    companion object {
        fun create(
            id: String,
            type: TransactionType,
            amount: Long,
            title: String,
            subtitle: String = "",
            category: String = "Chưa phân loại",
            accountOrBank: String = "VCB",
            timestamp: Long = System.currentTimeMillis(),
            source: String = "BANK_NOTIFICATION",
            isDuplicateCandidate: Boolean = false,
            revision: String = "0",
            note: String = "",
            excluded: Boolean = false,
        ): UiTransaction {
            val formattedAmount = FinanceFormatters.formatVnd(
                amount = amount,
                includeSign = true,
                isIncome = type == TransactionType.THU,
            )
            val formattedDate = FinanceFormatters.formatFriendlyDate(timestamp)
            return UiTransaction(
                id = id,
                type = type,
                amount = amount,
                formattedAmount = formattedAmount,
                title = title,
                subtitle = subtitle,
                category = category,
                accountOrBank = accountOrBank,
                formattedDate = formattedDate,
                timestamp = timestamp,
                source = source,
                isDuplicateCandidate = isDuplicateCandidate,
                revision = revision,
                note = note,
                excluded = excluded,
            )
        }
    }
}
