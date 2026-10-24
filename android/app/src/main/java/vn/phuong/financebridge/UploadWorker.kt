package vn.phuong.financebridge

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.StandardCharsets

class UploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = uploadMutex.withLock {
        withContext(Dispatchers.IO) {
            val eventId = inputData.getString(INPUT_EVENT_ID) ?: return@withContext Result.failure()
            val outbox = TransactionOutbox(applicationContext)
            var transaction = outbox.get(eventId)

            if (transaction == null) {
                transaction = migrateLegacyRequest(eventId, outbox) ?: return@withContext Result.failure()
            }

            if (transaction.state in TERMINAL_STATES) return@withContext Result.success()
            if (transaction.state != TransactionOutbox.STATE_PENDING) return@withContext Result.failure()

            if (EventIdentity.createEventId(transaction.bank, transaction.postedAt, transaction.text) != eventId) {
                outbox.markBlocked(eventId, "EVENT_ID_MISMATCH")
                return@withContext Result.failure()
            }

            val payload = buildTransactionPayload(transaction)
            if (payload.toString().toByteArray(StandardCharsets.UTF_8).size > MAX_REQUEST_BYTES) {
                outbox.markBlocked(eventId, "PAYLOAD_TOO_LARGE")
                return@withContext Result.failure()
            }

            outbox.markAttempt(eventId)

            try {
                val response = WebhookClient.post(payload)
                when {
                    WebhookClient.isTerminalSuccess(response) -> {
                        outbox.markTerminal(eventId, requireNotNull(response.status))
                        Result.success()
                    }

                    WebhookClient.isRetryable(response) -> {
                        outbox.markRetryable(eventId, WebhookClient.code(response))
                        Result.retry()
                    }

                    else -> {
                        outbox.markBlocked(eventId, WebhookClient.code(response))
                        Result.failure()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                outbox.markRetryable(eventId, "NETWORK_UNAVAILABLE")
                Result.retry()
            } catch (_: RuntimeException) {
                outbox.markBlocked(eventId, "INVALID_SERVER_RESPONSE")
                Result.failure()
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
        private const val MAX_REQUEST_BYTES = 16 * 1024
        private const val MAX_POSTED_AT = 253_402_300_799_999L
        private val uploadMutex = Mutex()
        private val TERMINAL_STATES = setOf(
            TransactionOutbox.STATE_SAVED_TRANSACTION,
            TransactionOutbox.STATE_SAVED_RAW_CAPTURE,
            TransactionOutbox.STATE_DUPLICATE,
            TransactionOutbox.STATE_DROPPED,
            TransactionOutbox.STATE_DROPPED_NON_TRANSACTION,
        )
    }
}

