package vn.phuong.financebridge.ui.screens.transactions

import vn.phuong.financebridge.EditFields
import vn.phuong.financebridge.EditGate
import vn.phuong.financebridge.EditGuard
import vn.phuong.financebridge.ui.model.UiTransaction

data class EditVerification(val base: UiTransaction, val fields: EditFields, val canSave: Boolean, val message: String? = null)

object TransactionEditFlow {
    fun fields(t: UiTransaction) = EditFields(t.type.name, t.category, t.note, t.excluded)

    fun pending(base: UiTransaction, draft: EditFields) = EditVerification(base, draft, false, "Đang kiểm tra bản mới nhất…")

    fun online(shown: UiTransaction, latest: UiTransaction?, draft: EditFields, enabled: Boolean): EditVerification {
        if (latest == null || latest.id != shown.id) return EditVerification(shown, draft, false, "Không còn tìm thấy giao dịch này trên máy chủ. Hãy tải lại danh sách.")
        if (!enabled) return EditVerification(shown, draft, false, "Chưa mở chỉnh sửa trên máy chủ.")
        if (latest.revision == shown.revision) return EditVerification(latest, draft, true)
        val before = fields(shown); val fresh = fields(latest)
        // Rebase only untouched fields. A response must never erase typing done during the read.
        val merged = EditFields(if (draft.type != before.type) draft.type else fresh.type,
            if (draft.category != before.category) draft.category else fresh.category,
            if (draft.note != before.note) draft.note else fresh.note,
            if (draft.excluded != before.excluded) draft.excluded else fresh.excluded)
        return EditVerification(latest, merged, true, "Bản mới nhất đã thay đổi. Đã giữ các trường bạn đang sửa; hãy kiểm tra trước khi lưu.")
    }

    fun offline(shown: UiTransaction, draft: EditFields, latestSyncedEdit: String?): EditVerification =
        if (EditGuard.offline(latestSyncedEdit, shown.revision) == EditGate.READY_SHOWN)
            EditVerification(shown, draft, true, "Đang ngoại tuyến. Thay đổi sẽ được kiểm tra lại khi đồng bộ.")
        else EditVerification(shown, draft, false, "Cần kết nối mạng để tải bản mới nhất trước khi sửa.")
}
