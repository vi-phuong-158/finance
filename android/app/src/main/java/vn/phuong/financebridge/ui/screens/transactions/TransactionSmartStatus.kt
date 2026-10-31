package vn.phuong.financebridge.ui.screens.transactions

import vn.phuong.financebridge.CategoryMemory
import vn.phuong.financebridge.CategoryMemoryIndex
import vn.phuong.financebridge.ui.model.UiTransaction

enum class SmartCategoryStatus {
    REMEMBERED,
    NEEDS_CHECK,
    UNCATEGORIZED,
}

object TransactionSmartStatus {
    fun derive(transaction: UiTransaction, memory: CategoryMemoryIndex?): SmartCategoryStatus? {
        if (transaction.category == "Chuyển nội bộ") {
            return null
        }
        if (transaction.category == CategoryMemory.UNCATEGORIZED) {
            return SmartCategoryStatus.UNCATEGORIZED
        }
        if (transaction.source == "MANUAL" || transaction.source == "MONEYLOVER" || transaction.revision != "0") {
            return SmartCategoryStatus.REMEMBERED
        }
        val suggestion = CategoryMemory.suggest(memory, transaction.memoryKey, transaction.type.name)
        return if (suggestion != null && suggestion.preselect && suggestion.category == transaction.category) {
            SmartCategoryStatus.REMEMBERED
        } else {
            SmartCategoryStatus.NEEDS_CHECK
        }
    }
}
