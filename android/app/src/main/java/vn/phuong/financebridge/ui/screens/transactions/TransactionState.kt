package vn.phuong.financebridge.ui.screens.transactions

import androidx.compose.runtime.saveable.listSaver
import vn.phuong.financebridge.EditFields
import vn.phuong.financebridge.ui.model.TransactionType
import vn.phuong.financebridge.ui.model.UiTransaction

// Saved instance state only: no disk cache, auth state or extra notification payload.
val TransactionSaver = listSaver<UiTransaction?, Any>(
    save = { t -> if (t == null) emptyList() else listOf(t.id, t.type.name, t.amount, t.formattedAmount,
        t.title, t.subtitle, t.category, t.accountOrBank, t.formattedDate, t.timestamp, t.source,
        t.isDuplicateCandidate, t.revision, t.note, t.excluded, t.memoryKey.orEmpty(), t.pendingSync) },
    restore = { v -> if (v.isEmpty()) null else UiTransaction(v[0] as String, TransactionType.valueOf(v[1] as String),
        v[2] as Long, v[3] as String, v[4] as String, v[5] as String, v[6] as String, v[7] as String,
        v[8] as String, v[9] as Long, v[10] as String, v[11] as Boolean, v[12] as String,
        v[13] as String, v[14] as Boolean, (v[15] as String).ifEmpty { null },
        if (v.size > 16) v[16] as Boolean else false) },
)

val EditFieldsSaver = listSaver<EditFields?, Any>(
    save = { f -> if (f == null) emptyList() else listOf(f.type, f.category, f.note, f.excluded) },
    restore = { v -> if (v.isEmpty()) null else EditFields(v[0] as String, v[1] as String, v[2] as String, v[3] as Boolean) },
)
