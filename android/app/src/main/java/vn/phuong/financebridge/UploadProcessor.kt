package vn.phuong.financebridge

import java.io.IOException
import kotlinx.coroutines.CancellationException

interface UploadOutbox {
    fun get(eventId: String): PendingTransaction?
    fun markAttempt(eventId: String)
    fun markRetryable(eventId: String, errorCode: String)
    fun markBlocked(eventId: String, errorCode: String)
    fun markTerminal(eventId: String, resultStatus: String)
}

enum class UploadOutcome { SUCCESS, RETRY, BLOCKED }

/** Runs the same state transitions in WorkManager and isolated JVM tests. */
class UploadProcessor(
    private val outbox: UploadOutbox,
    private val post: (PendingTransaction) -> WebhookResponse,
    private val payloadBytes: (PendingTransaction) -> Int,
) {
    fun upload(eventId: String): UploadOutcome {
        val event = outbox.get(eventId) ?: return UploadOutcome.BLOCKED
        if (event.state in terminalStates) return UploadOutcome.SUCCESS
        if (event.state != TransactionOutbox.STATE_PENDING) return UploadOutcome.BLOCKED
        if (EventIdentity.createEventId(event.bank, event.postedAt, event.text) != eventId) {
            outbox.markBlocked(eventId, "EVENT_ID_MISMATCH")
            return UploadOutcome.BLOCKED
        }
        if (payloadBytes(event) > 16 * 1024) {
            outbox.markBlocked(eventId, "PAYLOAD_TOO_LARGE")
            return UploadOutcome.BLOCKED
        }
        outbox.markAttempt(eventId)
        return try {
            val response = post(event)
            when {
                WebhookClient.isTerminalSuccess(response) -> {
                    outbox.markTerminal(eventId, requireNotNull(response.status))
                    UploadOutcome.SUCCESS
                }
                WebhookClient.isRetryable(response) -> {
                    outbox.markRetryable(eventId, WebhookClient.code(response))
                    UploadOutcome.RETRY
                }
                else -> {
                    outbox.markBlocked(eventId, WebhookClient.code(response))
                    UploadOutcome.BLOCKED
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            outbox.markRetryable(eventId, "NETWORK_UNAVAILABLE")
            UploadOutcome.RETRY
        } catch (_: RuntimeException) {
            outbox.markBlocked(eventId, "INVALID_SERVER_RESPONSE")
            UploadOutcome.BLOCKED
        }
    }

    companion object {
        private val terminalStates = setOf("SAVED_TRANSACTION", "SAVED_RAW_CAPTURE", "DUPLICATE_IGNORED", "DROPPED_SENSITIVE", "DROPPED_NON_TRANSACTION")
    }
}
