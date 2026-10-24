package vn.phuong.financebridge

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class BankNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val PREFERENCES = "finance_bridge_status"
        private const val KEY_QUEUE_ERROR = "queue_error_code"
        private val PACKAGE_TO_BANK = mapOf(
            "com.VCB" to "VCB",
            "com.vietinbank.ipay" to "VIETINBANK",
            "com.vnpay.bidv" to "BIDV"
        )

        fun computeEventId(bank: String, normalizedText: String): String {
            val payload = "$bank:$normalizedText"
            val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(16)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceScope.launch {
            try {
                TransactionOutbox(applicationContext).pendingEventIds().forEach { eventId ->
                    enqueueUpload(eventId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                applicationContext.getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                    .edit().putString(KEY_QUEUE_ERROR, "QUEUE_STORAGE_ERROR").apply()
            }
        }
        activeNotifications?.forEach { handleNotification(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) handleNotification(sbn)
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        val bank = PACKAGE_TO_BANK[pkg] ?: return

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val stdText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val rawBody = bigText?.takeIf { it.isNotBlank() } ?: stdText.orEmpty()

        val normalizedText = SensitiveFilter.normalizeText(rawBody)
        if (!SensitiveFilter.shouldProcess(title, normalizedText)) {
            return
        }

        val eventId = computeEventId(bank, normalizedText)
        val postedAt = sbn.postTime

        serviceScope.launch {
            try {
                val outbox = TransactionOutbox(applicationContext)
                val item = PendingTransaction(
                    eventId = eventId,
                    bank = bank,
                    packageName = pkg,
                    title = title,
                    text = normalizedText,
                    postedAt = postedAt,
                    state = TransactionOutbox.STATE_PENDING,
                )

                if (outbox.enqueueIfNewOrPending(item)) {
                    enqueueUpload(eventId)
                }
                applicationContext.getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                    .edit().remove(KEY_QUEUE_ERROR).apply()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                applicationContext.getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                    .edit().putString(KEY_QUEUE_ERROR, "QUEUE_STORAGE_ERROR").apply()
            }
        }
    }

    private fun enqueueUpload(eventId: String) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(UploadWorker.INPUT_EVENT_ID to eventId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "bank-$eventId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

}

