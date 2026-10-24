# Kiến trúc Runtime Webhook Credential & Public Safe APK

## 1. Tổng quan kiến trúc

Kiến trúc **Public Safe APK + Runtime Private Config** giải quyết rủi ro lộ secret khi xây dựng và phát hành APK từ repository công khai:

```text
Build time:
  Repo public / CI runner
  → BuildConfig chỉ chứa WEBHOOK_URL (https://finance-rust-nine-16.vercel.app/api/transaction)
  → Không nhúng WEBHOOK_SECRET vào BuildConfig, Gradle args, hay DEX file
  → Tạo Generic Public-Safe APK

Runtime (Thiết bị):
  Người dùng nhập Mã kết nối (WEBHOOK_SECRET) trong giao diện app
  → Mã hóa bằng AES/GCM (256-bit) với key quản lý bởi Android Keystore
  → Chỉ lưu Ciphertext + IV trong SharedPreferences nội bộ (Private App Storage)
  → WorkManager giải mã tại thời điểm gửi request qua HTTPS tới Vercel
```

## 2. Loại bỏ Secret khỏi APK & Build Artifacts

1. `WEBHOOK_URL` được giữ trong `BuildConfig` với giá trị mặc định là URL production HTTPS ổn định. URL này không phải là secret.
2. `WEBHOOK_SECRET` bị loại bỏ hoàn toàn khỏi:
   - `BuildConfig.java` / `BuildConfig.class`
   - Gradle build arguments và build caches
   - `local.properties` và template `local.properties.example`
   - GitHub Actions workflow secrets dành cho APK build
   - Artifact APK và CI logs.

## 3. Quản lý Secret bằng Android Keystore

- **Khởi tạo Key**: Master AES key 256-bit được tạo và lưu trữ bên trong phần cứng/vùng bảo mật của hệ thống thông qua `KeyStore.getInstance("AndroidKeyStore")`.
- **Mã hóa**: Sử dụng cipher transformation `AES/GCM/NoPadding` với authentication tag length 128-bit và IV 12-byte ngẫu nhiên.
- **Lưu trữ**: Chỉ lưu `webhook_secret_iv` (Base64) và `webhook_secret_cipher` (Base64) trong `SharedPreferences` với chế độ `Context.MODE_PRIVATE`.
- **Bảo vệ mã nguồn**: `WebhookCredentialStore` cài đặt `toString()` che giấu hoàn toàn nội dung secret (`WebhookCredentialStore(hasSecret=...)`), không đưa secret vào exceptions hay log.

## 4. Luồng xử lý WorkManager tại Runtime

```text
VCB Notification
  → Bộ lọc nhạy cảm (SensitiveFilter)
  → Tạo Event ID bất biến (EventIdentity)
  → Lưu hàng đợi SQLite (TransactionOutbox, STATE_PENDING)
  → WorkManager (UploadWorker)
  → Đọc secret từ WebhookCredentialStore (Android Keystore giải mã)
      ├─ Chưa cấu hình secret: chuyển STATE_BLOCKED ("MISSING_CREDENTIAL"), giữ nguyên outbox payload
      └─ Đã có secret: gửi POST HTTPS kèm header X-Webhook-Secret
           ├─ Thành công (200): đánh dấu STATE_SAVED_TRANSACTION, xóa raw text/title
           ├─ Lỗi mạng / timeout: đánh dấu RETRY, WorkManager tự động thử lại với exponential backoff
           └─ Sai secret (401 UNAUTHORIZED) / lỗi cấu hình: đánh dấu STATE_BLOCKED, giữ nguyên payload
```

Khi người dùng sửa mã kết nối trong UI, họ có thể nhấn nút **Gửi lại X giao dịch lỗi** để chuyển các bản ghi `BLOCKED` về `PENDING` và WorkManager sẽ tiếp tục gửi mà không bị mất dữ liệu hay trùng lặp.

## 5. Trải nghiệm người dùng (UX)

Giao diện **Cấu hình kết nối** hỗ trợ 4 trạng thái rõ ràng:
1. **Chưa cấu hình**: Hiển thị ô nhập password cho "Mã kết nối" cùng các nút "Lưu cấu hình", "Lưu và kiểm tra".
2. **Đã cấu hình**: Mã kết nối được ẩn dạng `•••••••••••• (Đã lưu an toàn)`. Cung cấp nút **Đổi mã kết nối** và nút **Kiểm tra kết nối**.
3. **Kết nối thành công**: Hiển thị sau khi `ConnectionCheckWorker` gửi dry-run (`dryRun: true`) thành công tới backend Vercel (`VALIDATED_TRANSACTION`), chứng minh xác thực hợp lệ trước khi cấp quyền notification.
4. **Cấu hình không hợp lệ**: Hiển thị khi mã kết nối không đúng format (cần 32-128 ký tự URL-safe) hoặc server trả lỗi cấu hình (`UNAUTHORIZED`, `CLIENT_CONFIGURATION_ERROR`, v.v.).

## 6. Chính sách Gradle & Security Scan

- **Gradle Task `validateReleaseWebhookConfig`**: Kiểm tra endpoint HTTPS `/api/transaction` hợp lệ (không chứa credentials, query, hay fragment). Không yêu cầu secret tại build time.
- **Gradle Task `verifyWebhookConfigurationPolicy`**: Tự động xác thực 7 quy tắc chính sách endpoint và bảo đảm `BuildConfig` không chứa trường `WEBHOOK_SECRET`.
- **CI / Workflow Security Scan**: Sau khi assemble APK, script quét toàn bộ các file DEX bên trong APK và file `BuildConfig.java` để bảo đảm chuỗi `WEBHOOK_SECRET` không tồn tại dưới dạng hằng số trong artifact.

## 7. Giới hạn bảo mật (Security Boundaries & Limitations)

- **Thiết bị tiêu chuẩn (Non-compromised)**: Android Keystore cung cấp mức độ bảo vệ mạnh mẽ chống lại việc trích xuất key/secret từ các app khác hoặc qua backup thông thường.
- **Thiết bị bị Root hoặc can thiệp phần cứng (Compromised / Rooted)**: Trên các thiết bị đã bị root hoặc bị khai thác kernel, ứng dụng có đặc quyền root hoặc công cụ debug bộ nhớ động (như Frida) có thể trích xuất secret khi app đang chạy hoặc can thiệp bộ nhớ RAM. Kiến trúc này không tuyên bố bí mật tuyệt đối trong mô hình mối đe dọa thiết bị đã bị thỏa hiệp; mục tiêu là ngăn chặn hoàn toàn việc rò rỉ secret qua source code công khai và build artifact, đồng thời bảo vệ hợp lý credential ở trạng thái nghỉ (at-rest) trên thiết bị người dùng.
