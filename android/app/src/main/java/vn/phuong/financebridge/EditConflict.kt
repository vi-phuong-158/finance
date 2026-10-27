package vn.phuong.financebridge

data class QueuedEdit(val requestId: String, val state: String, val lastError: String?)
data class EditFields(val type: String, val category: String, val note: String, val excluded: Boolean)
data class FieldDiff(val label: String, val server: String, val mine: String) { val changed get() = server != mine }

enum class EditGate { WAIT_FOR_SYNC, RESOLVE_CONFLICT, READY_SHOWN, NEEDS_NETWORK }

// An edit is only valid against the revision it was based on (the latest edit's requestId on the server),
// so a second edit is never queued on top of one that has not been acknowledged.
object EditGuard {
    fun queued(active: QueuedEdit?): EditGate? = when {
        active == null -> null
        active.state == "BLOCKED" && blockKind(active.lastError) == BlockKind.EDIT_CONFLICT -> EditGate.RESOLVE_CONFLICT
        else -> EditGate.WAIT_FOR_SYNC
    }

    // Offline the list cannot be refreshed: it is only trusted if it already shows this phone's last acknowledged edit.
    fun offline(latestSyncedEdit: String?, shownRevision: String): EditGate =
        if (latestSyncedEdit == null || latestSyncedEdit == shownRevision) EditGate.READY_SHOWN else EditGate.NEEDS_NETWORK

    fun diff(server: EditFields, mine: EditFields): List<FieldDiff> {
        fun type(t: String) = if (t == "THU") "Thu" else "Chi"
        fun excluded(e: Boolean) = if (e) "Không tính vào báo cáo" else "Tính vào báo cáo"
        return listOf(
            FieldDiff("Loại", type(server.type), type(mine.type)),
            FieldDiff("Danh mục", server.category, mine.category),
            FieldDiff("Ghi chú", server.note.ifBlank { "(trống)" }, mine.note.ifBlank { "(trống)" }),
            FieldDiff("Báo cáo", excluded(server.excluded), excluded(mine.excluded)),
        )
    }
}
