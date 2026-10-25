package vn.phuong.financebridge.ui.screens.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Warning
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
import vn.phuong.financebridge.ui.components.FinanceCard
import vn.phuong.financebridge.ui.components.PrimaryButton
import vn.phuong.financebridge.ui.components.SecondaryButton
import vn.phuong.financebridge.ui.theme.FinanceTheme
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
    var connectionStatus by remember { mutableStateOf(if (hasConfiguredSecret) "Đã lưu mã kết nối; chưa kiểm tra" else "Chưa cấu hình") }
    var checkInProgress by remember { mutableStateOf(false) }
    var connectionWorkId by remember { mutableStateOf<UUID?>(null) }
    var queueError by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf(OutboxSummary(0, 0, emptyList())) }
    var showTechnicalDetails by remember { mutableStateOf(false) }

    val workManager = remember(context) { WorkManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    fun triggerConnectionCheck() {
        checkInProgress = true
        connectionStatus = "Đang kiểm tra kết nối với máy chủ..."
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
                    connectionStatus = "Kết nối máy chủ thành công"
                } else if (latest?.state == WorkInfo.State.FAILED) {
                    isConnectionVerified = false
                    connectionStatus = latest.outputData.getString("message") ?: "Không kết nối được; vui lòng kiểm tra mã kết nối"
                } else if (latest?.state == WorkInfo.State.ENQUEUED || latest?.state == WorkInfo.State.BLOCKED) {
                    connectionStatus = "Đang chờ kết nối mạng..."
                } else if (latest?.state == WorkInfo.State.RUNNING) {
                    connectionStatus = "Đang xác thực với máy chủ..."
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Kết nối & Hệ thống",
            style = FinanceTheme.typography.headlineLarge,
            color = FinanceTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 8.dp),
        )

        // Card 1: Máy chủ & Mã kết nối
        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 20.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Kết nối máy chủ",
                    style = FinanceTheme.typography.titleMedium,
                    color = FinanceTheme.colors.textPrimary,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Trạng thái",
                        style = FinanceTheme.typography.bodyMedium,
                        color = FinanceTheme.colors.textSecondary,
                    )
                    Text(
                        text = connectionStatus,
                        style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isConnectionVerified) FinanceTheme.colors.income else FinanceTheme.colors.textMuted,
                    )
                }

                if (hasConfiguredSecret && !isEditingSecret) {
                    Text(
                        text = "Mã kết nối: •••••••••••• (Đã lưu an toàn)",
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
                            text = if (checkInProgress) "Đang kiểm tra..." else "Kiểm tra kết nối",
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
                        label = { Text("Mã kết nối") },
                        placeholder = { Text("Nhập mã kết nối bảo mật") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = secretFormatError != null,
                        supportingText = secretFormatError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = FinanceTheme.shapes.medium,
                    )
                    PrimaryButton(
                        text = "Lưu và kiểm tra kết nối",
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
                                secretFormatError = "Không thể lưu mã kết nối"
                            }
                        },
                        enabled = !checkInProgress,
                    )
                }
            }
        }

        // Card 2: Quyền hệ thống
        FinanceCard(
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

                // Quyền đọc thông báo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Đọc biến động số dư",
                            style = FinanceTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = FinanceTheme.colors.textPrimary,
                        )
                        Text(
                            text = if (hasNotifPermission) "Đã cấp quyền đọc thông báo" else "Chưa bật quyền",
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

                HorizontalDivider(color = FinanceTheme.colors.borderGlass.copy(alpha = 0.5f))

                // Tối ưu hóa Pin
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Hoạt động nền không hạn chế",
                            style = FinanceTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = FinanceTheme.colors.textPrimary,
                        )
                        Text(
                            text = if (isBatteryUnrestricted) "Đang chạy nền bình thường" else "Đang bị hệ thống hạn chế",
                            style = FinanceTheme.typography.caption,
                            color = if (isBatteryUnrestricted) FinanceTheme.colors.income else FinanceTheme.colors.warning,
                        )
                    }
                    if (!isBatteryUnrestricted) {
                        SecondaryButton(
                            text = "Cài đặt pin",
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

        // Card 3: Trạng thái đồng bộ hàng đợi
        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 20.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Trạng thái đồng bộ",
                    style = FinanceTheme.typography.titleMedium,
                    color = FinanceTheme.colors.textPrimary,
                )
                Text(
                    text = "${summary.pendingCount} giao dịch đang chờ · ${summary.blockedCount} giao dịch cần gửi lại",
                    style = FinanceTheme.typography.bodyMedium,
                    color = FinanceTheme.colors.textSecondary,
                )

                if (summary.blockedCount > 0) {
                    PrimaryButton(
                        text = "Gửi lại ${summary.blockedCount} giao dịch cần xử lý",
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
                        text = "Nhật ký đồng bộ gần nhất:",
                        style = FinanceTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                        color = FinanceTheme.colors.textMuted,
                    )
                    summary.recentEntries.take(5).forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${entry.bank} • ${formatOutboxState(entry.state)}",
                                style = FinanceTheme.typography.caption,
                                color = FinanceTheme.colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        // Card 4: Expandable Chi tiết kỹ thuật (dành cho bảo trì / hỗ trợ)
        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FinanceTheme.shapes.xLarge,
            contentPadding = 16.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTechnicalDetails = !showTechnicalDetails },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Chi tiết kỹ thuật",
                        style = FinanceTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = FinanceTheme.colors.textMuted,
                    )
                    Icon(
                        imageVector = if (showTechnicalDetails) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = FinanceTheme.colors.textMuted,
                    )
                }

                AnimatedVisibility(visible = showTechnicalDetails) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Máy chủ: ${BuildConfig.WEBHOOK_URL}",
                            style = FinanceTheme.typography.caption,
                            color = FinanceTheme.colors.textMuted,
                        )
                        Text(
                            text = "Phiên bản ứng dụng: 1.0 (Build 1)",
                            style = FinanceTheme.typography.caption,
                            color = FinanceTheme.colors.textMuted,
                        )
                        Text(
                            text = "Hàng đợi: SQLite Outbox với Keystore AES/GCM",
                            style = FinanceTheme.typography.caption,
                            color = FinanceTheme.colors.textMuted,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(56.dp))
    }
}

private fun formatOutboxState(state: String): String = when (state) {
    TransactionOutbox.STATE_SAVED_TRANSACTION -> "Đã đồng bộ"
    TransactionOutbox.STATE_SAVED_RAW_CAPTURE -> "Đã lưu mẫu chờ phân tích"
    TransactionOutbox.STATE_DUPLICATE -> "Đã ghi nhận trước đó"
    TransactionOutbox.STATE_PENDING -> "Đang chờ gửi"
    TransactionOutbox.STATE_BLOCKED -> "Cần gửi lại"
    TransactionOutbox.STATE_DROPPED, TransactionOutbox.STATE_DROPPED_NON_TRANSACTION -> "Không phải giao dịch"
    else -> state
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
