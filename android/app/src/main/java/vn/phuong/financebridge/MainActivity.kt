package vn.phuong.financebridge

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.phuong.financebridge.ui.MainAppScreen
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = this
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val appContext = context.applicationContext
                    val credentialStore = WebhookCredentialStore(appContext)
                    val outbox = TransactionOutbox(appContext)
                    outbox.purgeOldTerminalEntries()
                    if (credentialStore.hasSecret()) {
                        val pending = outbox.pendingEventIds()
                        val workManager = WorkManager.getInstance(appContext)
                        pending.forEach { eventId ->
                            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                                .setInputData(workDataOf(UploadWorker.INPUT_EVENT_ID to eventId))
                                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                                .build()
                            workManager.enqueueUniqueWork("bank-$eventId", ExistingWorkPolicy.KEEP, request)
                        }
                    }
                }
            }

            MainAppScreen(context = this)
        }
    }
}
