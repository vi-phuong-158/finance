package vn.phuong.financebridge.ui.screens.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.phuong.financebridge.*
import vn.phuong.financebridge.ui.components.*
import vn.phuong.financebridge.ui.theme.FinanceTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(
    context: Context,
    modifier: Modifier = Modifier,
) {
    val appContext = context.applicationContext
    val credentialStore = remember(appContext) { WebhookCredentialStore(appContext) }
    var hasConfiguredSecret by remember { mutableStateOf(credentialStore.hasSecret()) }
    var isEditingSecret by remember { mutableStateOf(!hasConfiguredSecret) }
    var secretInput by remember { mutableStateOf("") }
    var secretFormatError by remember { mutableStateOf<String?>(null) }
    var isConnectionVerified by remember { mutableStateOf(false) }

    var hasNotifPermission by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var isBatteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var connectionStatus by remember { mutableStateOf(if (hasConfiguredSecret) "Đã lưu cấu hình; chưa kiểm tra" else "Chưa cấu hình") }
    var checkInProgress by remember { mutableStateOf(false) }
    var connectionWorkId by remember { mutableStateOf<UUID?>(null) }
    var queueError by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf(OutboxSummary(0, 0, emptyList())) }
    val workManager = remember(context) { WorkManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    fun triggerConnectionCheck() {
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
    }

    LaunchedEffect(appContext) {
        while (isActive) {
            hasNotifPermission = isNotificationServiceEnabled(appContext)
            isBatteryUnrestricted = isIgnoringBatteryOptimizations(appContext)
            hasConfiguredSecret = credentialStore.hasSecret()
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
                    isConnectionVerified = true
                    connectionStatus = latest.outputData.getString("message") ?: "Kết nối thành công"
                } else if (latest?.state == WorkInfo.State.FAILED) {
                    isConnectionVerified = false
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
        modifier = modifier
            .fillMaxSize()
            .background(FinanceTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Cài đặt & Kết nối",
            style = FinanceTheme.typography.headlineLarge,
            color = FinanceTheme.colors.textPrimary,
        )

        // Webhook Configuration GlassCard
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 20.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Cấu hình Webhook Vercel",
                    style = FinanceTheme.typography.titleMedium,
                    color = FinanceTheme.colors.textPrimary,
                )
                Text(
                    text = "Máy chủ: ${BuildConfig.WEBHOOK_URL}",
                    style = FinanceTheme.typography.bodyMedium,
                    color = FinanceTheme.colors.textSecondary,
                )
                Text(
                    text = "Trạng thái: $connectionStatus",
                    style = FinanceTheme.typography.caption,
                    color = if (isConnectionVerified) FinanceTheme.colors.income else FinanceTheme.colors.textMuted,
                )

                if (hasConfiguredSecret && !isEditingSecret) {
                    Text(
                        text = "Mã kết nối: •••••••••••• (Đã lưu an toàn bằng Keystore)",
                        style = FinanceTheme.typography.bodyMedium,
                        color = FinanceTheme.colors.textSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SecondaryButton(
                            text = "Đổi mã kết nối",
                            onClick = {
                                isEditingSecret = true
                                secretInput = ""
                                secretFormatError = null
                            },
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            text = if (checkInProgress) "Đang kiểm tra..." else "Kiểm tra",
                            onClick = { triggerConnectionCheck() },
                            enabled = !checkInProgress,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = secretInput,
                        onValueChange = {
                            secretInput = it
                            secretFormatError = null
                        },
                        label = { Text("Mã kết nối (Webhook Secret)") },
                        placeholder = { Text("Nhập secret từ backend Vercel") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = secretFormatError != null,
                        supportingText = secretFormatError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton(
                        text = "Lưu và kiểm tra",
                        onClick = {
                            val trimmed = secretInput.trim()
                            if (!WebhookConfiguration.isValidSecret(trimmed)) {
                                secretFormatError = "Mã kết nối cần 32-128 ký tự an toàn"
                                return@PrimaryButton
                            }
                            if (credentialStore.saveSecret(trimmed)) {
                                hasConfiguredSecret = true
                                isEditingSecret = false
                                secretInput = ""
                                secretFormatError = null
                                triggerConnectionCheck()
                            } else {
                                secretFormatError = "Không thể lưu mã kết nối vào Keystore"
                            }
                        },
                        enabled = !checkInProgress,
                    )
                }
            }
        }

        // Permissions Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 20.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Quyền hệ thống",
                    style = FinanceTheme.typography.titleMedium,
                    color = FinanceTheme.colors.textPrimary,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Quyền đọc thông báo",
                            style = FinanceTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = FinanceTheme.colors.textPrimary,
                        )
                        Text(
                            text = if (hasNotifPermission) "Đã cấp quyền đọc biến động số dư" else "Chưa cấp quyền",
                            style = FinanceTheme.typography.caption,
                            color = if (hasNotifPermission) FinanceTheme.colors.income else FinanceTheme.colors.expense,
                        )
                    }
                    if (!hasNotifPermission) {
                        SecondaryButton(
                            text = "Cấp quyền",
                            onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                            modifier = Modifier.widthIn(max = 120.dp),
                        )
                    }
                }

                HorizontalDivider(color = FinanceTheme.colors.borderGlass.copy(alpha = 0.3f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tối ưu hóa Pin",
                            style = FinanceTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = FinanceTheme.colors.textPrimary,
                        )
                        Text(
                            text = if (isBatteryUnrestricted) "Chạy ngầm không hạn chế" else "Đang bị hệ thống hạn chế",
                            style = FinanceTheme.typography.caption,
                            color = if (isBatteryUnrestricted) FinanceTheme.colors.income else FinanceTheme.colors.warning,
                        )
                    }
                    if (!isBatteryUnrestricted) {
                        SecondaryButton(
                            text = "Mở cài đặt",
                            onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.widthIn(max = 120.dp),
                        )
                    }
                }
            }
        }

        // Outbox Status Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 20.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Hàng đợi đồng bộ Outbox",
                    style = FinanceTheme.typography.titleMedium,
                    color = FinanceTheme.colors.textPrimary,
                )
                Text(
                    text = "${summary.pendingCount} đang chờ · ${summary.blockedCount} cần xử lý",
                    style = FinanceTheme.typography.bodyMedium,
                    color = FinanceTheme.colors.textSecondary,
                )

                if (summary.blockedCount > 0) {
                    PrimaryButton(
                        text = "Gửi lại ${summary.blockedCount} giao dịch lỗi",
                        onClick = {
                            coroutineScope.launch {
                                val outbox = TransactionOutbox(appContext)
                                val ready = withContext(Dispatchers.IO) {
                                    outbox.blockedEventIds().filter(outbox::retryBlocked)
                                }
                                ready.forEach { eventId ->
                                    val request = OneTimeWorkRequestBuilder<UploadWorker>()
                                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                                        .setInputData(workDataOf(UploadWorker.INPUT_EVENT_ID to eventId))
                                        .build()
                                    workManager.enqueueUniqueWork("bank-$eventId", ExistingWorkPolicy.KEEP, request)
                                }
                            }
                        },
                    )
                }

                if (summary.recentEntries.isNotEmpty()) {
                    Text(
                        text = "Nhật ký outbox gần nhất:",
                        style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                        color = FinanceTheme.colors.textMuted,
                    )
                    summary.recentEntries.take(5).forEach { entry ->
                        Text(
                            text = "${entry.bank} • ${entry.eventId.take(8)}… • ${entry.state}",
                            style = FinanceTheme.typography.caption,
                            color = FinanceTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    val cn = ComponentName(context, BankNotificationListener::class.java)
    return flat.contains(cn.flattenToString())
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
