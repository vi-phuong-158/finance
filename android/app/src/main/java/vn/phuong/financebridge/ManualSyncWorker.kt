package vn.phuong.financebridge

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ManualSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        ManualOutbox(applicationContext).use { outbox ->
            val secret = WebhookCredentialStore(applicationContext).getSecret().orEmpty()
            val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).build()
            for ((id, payload, endpoint) in outbox.pending()) {
                if (endpoint !in setOf("manual-transaction", "loan-event", "transaction-edit")) {
                    outbox.update(id, "BLOCKED")
                    continue
                }
                if (!WebhookConfiguration.isValid(BuildConfig.WEBHOOK_URL, secret)) {
                    outbox.update(id, "BLOCKED")
                    continue
                }
                outbox.update(id, "SYNCING")
                try {
                    val request = Request.Builder().url(BuildConfig.WEBHOOK_URL.removeSuffix("/api/transaction") + "/api/$endpoint")
                        .header("X-Webhook-Secret", secret).post(payload.toRequestBody("application/json".toMediaType())).build()
                    val state = client.newCall(request).execute().use { response ->
                        val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                        manualSyncState(response.code, json?.optString("status")?.takeIf { it.isNotEmpty() } ?: json?.optString("error"))
                    }
                    outbox.update(id, state)
                    if (state == "PENDING") return@withContext Result.retry()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    outbox.update(id, "PENDING")
                    return@withContext Result.retry()
                }
            }
            if (outbox.pending().isNotEmpty()) Result.retry() else Result.success()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val work = OneTimeWorkRequestBuilder<ManualSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            // Append avoids losing a save that arrives just as an earlier drain finishes.
            WorkManager.getInstance(context).enqueueUniqueWork("manual-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, work)
        }
    }
}
