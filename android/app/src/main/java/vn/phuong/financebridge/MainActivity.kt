package vn.phuong.financebridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                StatusScreen(context = this)
            }
        }
    }
}

@Composable
fun StatusScreen(context: Context) {
    var hasNotifPermission by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var isBatteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var connectionStatus by remember { mutableStateOf("Chưa kiểm tra") }
    var checkInProgress by remember { mutableStateOf(false) }
    var connectionWorkId by remember { mutableStateOf<UUID?>(null) }
    var queueError by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf(OutboxSummary(0, 0, emptyList())) }
    val appContext = context.applicationContext
    val workManager = remember(context) { WorkManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(appContext) {
        val pendingEventIds = withContext(Dispatchers.IO) {
            runCatching {
                TransactionOutbox(appContext).apply { purgeOldTerminalEntries() }.pendingEventIds()
            }.getOrDefault(emptyList())
        }
        pendingEventIds.forEach { schedulePendingUpload(workManager, it) }

        while (isActive) {
            hasNotifPermission = isNotificationServiceEnabled(appContext)
            isBatteryUnrestricted = isIgnoringBatteryOptimizations(appContext)
            queueError = appContext.getSharedPreferences("finance_bridge_status", Context.MODE_PRIVATE)
                .getString("queue_error_code", null)
            summary = withContext(Dispatchers.IO) {
                runCatching { TransactionOutbox(appContext).summary() }
                    .getOrElse { OutboxSummary(0, 0, emptyList()) }
            }
            delay(2_000)
        }
    }

    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(workManager, lifecycleOwner, connectionWorkId) {
        val currentWorkId = connectionWorkId
        if (currentWorkId == null || lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = Observer<WorkInfo?> { latest ->
                checkInProgress = latest?.state == WorkInfo.State.ENQUEUED
                    || latest?.state == WorkInfo.State.RUNNING
                    || latest?.state == WorkInfo.State.BLOCKED
                if (latest?.state == WorkInfo.State.SUCCEEDED) {
                    connectionStatus = latest.outputData.getString("message") ?: "Máy chủ đã kiểm tra kết nối"
                } else if (latest?.state == WorkInfo.State.FAILED) {
                    connectionStatus = latest.outputData.getString("message") ?: "Không kết nối được; kiểm tra cấu hình"
                } else if (latest?.state == WorkInfo.State.ENQUEUED || latest?.state == WorkInfo.State.BLOCKED) {
                    connectionStatus = "Đang chờ kết nối mạng..."
                } else if (latest?.state == WorkInfo.State.RUNNING) {
                    connectionStatus = "Đang kiểm tra server và Sheet..."
                }
            }
            val liveData = workManager.getWorkInfoByIdLiveData(currentWorkId)
            liveData.observe(lifecycleOwner, observer)
            onDispose { liveData.removeObserver(observer) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Đồng Bộ Thu Chi Tự Động", style = MaterialTheme.typography.headlineSmall)

        Text(text = if (hasNotifPermission) "✓ Quyền đọc thông báo: Đã bật" else "✗ Quyền đọc thông báo: Chưa bật")
        if (!hasNotifPermission) {
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                Text("Mở cài đặt quyền thông báo")
            }
        }

        Text(text = if (isBatteryUnrestricted) "✓ Pin không hạn chế: Đã bật" else "✗ Pin không hạn chế: Chưa bật")
        if (!isBatteryUnrestricted) {
            TextButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                },
            ) {
                Text("Mở cài đặt tối ưu pin")
            }
        }

        Text(text = if (checkInProgress) "◷ $connectionStatus" else "• Máy chủ: $connectionStatus")
        Button(
            onClick = {
                checkInProgress = true
                connectionStatus = "Đang xếp hàng kiểm tra kết nối..."
                val request = OneTimeWorkRequestBuilder<ConnectionCheckWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .setInputData(workDataOf("dryRun" to true))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                connectionWorkId = request.id
                workManager.enqueue(request)
            },
            enabled = !checkInProgress,
        ) {
            Text(if (checkInProgress) "Đang kiểm tra..." else "Kiểm tra kết nối")
        }

        Text(text = "Hàng đợi: ${summary.pendingCount} đang chờ · ${summary.blockedCount} cần xử lý")
        if (summary.blockedCount > 0) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        val outbox = TransactionOutbox(appContext)
                        val ready = withContext(Dispatchers.IO) {
                            outbox.blockedEventIds().filter(outbox::retryBlocked)
                        }
                        ready.forEach { eventId -> schedulePendingUpload(workManager, eventId) }
                    }
                },
            ) {
                Text("Gửi lại ${summary.blockedCount} giao dịch lỗi")
            }
        }

        if (queueError != null) {
            Text(text = "Lỗi: Không lưu được vào hàng đợi của điện thoại. Giải phóng dung lượng rồi thử lại.")
        }

        Text(text = "Giao dịch gần đây", style = MaterialTheme.typography.titleMedium)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (summary.recentEntries.isEmpty()) {
                Text("Chưa nhận giao dịch nào.", style = MaterialTheme.typography.bodySmall)
            }
            summary.recentEntries.forEach { entry ->
                val details = entry.errorCode?.let { " · $it" }.orEmpty()
                Text(
                    text = "${entry.bank} · ${entry.eventId.take(8)}… · ${stateLabel(entry.state)}$details · ${formatTime(entry.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Text("Giao dịch chưa gửi được giữ riêng trên điện thoại và không tự xóa.", style = MaterialTheme.typography.bodySmall)
        Text(text = "Phiên bản: 1.0", style = MaterialTheme.typography.bodySmall)
    }
}

private fun schedulePendingUpload(workManager: WorkManager, eventId: String) {
    val request = OneTimeWorkRequestBuilder<UploadWorker>()
        .setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        )
        .setInputData(workDataOf(UploadWorker.INPUT_EVENT_ID to eventId))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    workManager.enqueueUniqueWork("bank-$eventId", androidx.work.ExistingWorkPolicy.KEEP, request)
}

private fun stateLabel(state: String): String = when (state) {
    TransactionOutbox.STATE_PENDING -> "Đang chờ gửi"
    TransactionOutbox.STATE_BLOCKED -> "Cần sửa cấu hình / gửi lại"
    TransactionOutbox.STATE_SAVED_TRANSACTION -> "Đã ghi giao dịch"
    TransactionOutbox.STATE_SAVED_RAW_CAPTURE -> "Đã ghi mẫu chờ phân tích"
    TransactionOutbox.STATE_DUPLICATE -> "Đã có, bỏ qua"
    TransactionOutbox.STATE_DROPPED, TransactionOutbox.STATE_DROPPED_NON_TRANSACTION -> "Đã lọc bỏ"
    else -> "Trạng thái không xác định"
}

private fun formatTime(timestamp: Long): String = SimpleDateFormat("dd/MM HH:mm", Locale("vi", "VN")).apply {
    timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
}.format(Date(timestamp))

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    val cn = ComponentName(context, BankNotificationListener::class.java)
    return flat.contains(cn.flattenToString())
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

