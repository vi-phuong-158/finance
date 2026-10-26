package vn.phuong.financebridge

import java.util.UUID

data class CategoryChoice(val name: String, val type: String)
data class TransactionEditInput(val requestId: String, val transactionId: String, val baseRevision: String,
    val category: String, val note: String, val excluded: Boolean, val type: String) {
    fun validate(categories: List<CategoryChoice>) {
        require(UUID.fromString(requestId).version() == 4)
        require(transactionId.isNotBlank() && transactionId.length <= 200)
        require(baseRevision == "0" || UUID.fromString(baseRevision).version() == 4)
        require(type in setOf("THU", "CHI"))
        require(categories.any { it.name == category && (it.type == type || it.type == "BOTH") }) { "Chọn danh mục có sẵn phù hợp Thu/Chi." }
        require(note.toByteArray(Charsets.UTF_8).size <= 1024) { "Ghi chú quá dài." }
    }
}
