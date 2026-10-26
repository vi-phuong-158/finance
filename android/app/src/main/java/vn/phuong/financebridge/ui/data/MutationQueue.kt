package vn.phuong.financebridge.ui.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import vn.phuong.financebridge.ManualOutbox
import vn.phuong.financebridge.ManualSyncWorker

suspend fun queueMutation(context: Context, id: String, payload: String, endpoint: String) {
    // Once save begins, leaving a screen must not cancel the durable insert/schedule pair.
    withContext(Dispatchers.IO + NonCancellable) {
        ManualOutbox(context).use { it.enqueueRequest(id, payload, endpoint) }
        ManualSyncWorker.schedule(context)
    }
}
