package vn.phuong.financebridge

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class ConnectionCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val credentialStore = WebhookCredentialStore(applicationContext)
        val secret = credentialStore.getSecret()
        if (secret == null) {
            return@withContext output("Chưa cấu hình mã kết nối", "MISSING_CREDENTIAL")
        }

        val postedAt = System.currentTimeMillis()
        val event = PendingTransaction(
            eventId = EventIdentity.createEventId("VCB", postedAt, TEST_TRANSACTION_TEXT),
            bank = "VCB",
            packageName = "com.VCB",
            title = "Vigil Finance connection check",
            text = TEST_TRANSACTION_TEXT,
            postedAt = postedAt,
            state = TransactionOutbox.STATE_PENDING,
        )

        val response = try {
            WebhookClient.post(buildTransactionPayload(event, dryRun = true), secret = secret)
        } catch (_: IOException) {
            return@withContext Result.retry()
        } catch (_: RuntimeException) {
            return@withContext output("Phản hồi máy chủ không hợp lệ", "INVALID_SERVER_RESPONSE")
        }

        if (WebhookClient.dryRunSucceeded(response)) {
            output(WebhookClient.message(response), response.status ?: "DRY_RUN_SUCCEEDED", succeeded = true)
        } else if (WebhookClient.isRetryable(response)) {
            Result.retry()
        } else {
            output(WebhookClient.message(response), WebhookClient.code(response))
        }
    }

    private fun output(message: String, code: String, succeeded: Boolean = false) =
        if (succeeded) {
            Result.success(workDataOf("message" to message, "resultCode" to code))
        } else {
            Result.failure(workDataOf("message" to message, "errorCode" to code))
        }

    companion object {
        const val UNIQUE_WORK_NAME = "finance-bridge-connection-check"
        private const val TEST_TRANSACTION_TEXT =
            "Số dư TK VCB ACCOUNT-TEST +1,000 VND lúc 24-10-2026 11:15:00. Số dư 100,000 VND. Ref TEST.CONNECTION CHECK"
    }
}

