package vn.phuong.financebridge

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

class UploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = uploadMutex.withLock {
        withContext(Dispatchers.IO) {
            val eventId = inputData.getString(INPUT_EVENT_ID) ?: return@withContext Result.failure()
            val outbox = TransactionOutbox(applicationContext)
            if (outbox.get(eventId) == null) {
                migrateLegacyRequest(eventId, outbox) ?: return@withContext Result.failure()
            }

            val credentialStore = WebhookCredentialStore(applicationContext)
            val secret = credentialStore.getSecret()
            if (secret == null) {
                outbox.markBlocked(eventId, "MISSING_CREDENTIAL")
                return@withContext Result.failure()
            }

            val processor = UploadProcessor(
                outbox,
                post = { event -> WebhookClient.post(buildTransactionPayload(event), secret = secret) },
                payloadBytes = { event -> buildTransactionPayload(event).toString().toByteArray(StandardCharsets.UTF_8).size },
            )
            when (processor.upload(eventId)) {
                UploadOutcome.SUCCESS -> Result.success()
                UploadOutcome.RETRY -> Result.retry()
                UploadOutcome.BLOCKED -> Result.failure()
            }
        }
    }

    private fun migrateLegacyRequest(eventId: String, outbox: TransactionOutbox): PendingTransaction? {
        val bank = inputData.getString("bank") ?: return null
        val packageName = inputData.getString("packageName") ?: return null
        val title = inputData.getString("title").orEmpty()
        val text = inputData.getString("text") ?: return null
        val postedAt = inputData.getLong("postedAt", 0L)
        if (!SensitiveFilter.shouldProcess(title, text)) return null

        val normalized = SensitiveFilter.normalizeText(text)
        val legacyId = EventIdentity.createEventId(bank, postedAt, normalized)
        if (legacyId != eventId || !postedAt.isValidPostedTime()) return null

        val transaction = PendingTransaction(
            eventId = legacyId,
            bank = bank,
            packageName = packageName,
            title = title,
            text = normalized,
            postedAt = postedAt,
            state = TransactionOutbox.STATE_PENDING,
        )
        if (!outbox.enqueueIfNewOrPending(transaction)) return null
        return outbox.get(eventId)
    }

    private fun Long.isValidPostedTime(): Boolean = this >= 0L && this <= MAX_POSTED_AT

    companion object {
        const val INPUT_EVENT_ID = "eventId"
        private const val MAX_POSTED_AT = 253_402_300_799_999L
        private val uploadMutex = Mutex()

    }
}

