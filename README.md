# Finance Bridge — V3.1 đang hoàn thiện theo gate nghiệm thu

Gói mã nguồn gồm:
1. **`vercel/`**: Endpoint TypeScript trên Vercel, parser, bộ lọc, danh mục và test với Sheets giả lập.
2. **`android/`**: Ứng dụng single-module dùng Kotlin, Jetpack Compose, `NotificationListenerService`, `SensitiveFilter`, WorkManager, Android Keystore và SQLite cục bộ.
3. **`SPEC_V3_1.md`**: Bản đặc tả kỹ thuật chốt thi công V3.1.

Mã nguồn chưa được nghiệm thu trên điện thoại hay Google Sheets thật. MVP ưu tiên VCB; BIDV/VietinBank cần mẫu đã che dữ liệu để viết parser. Tiến độ triển khai và các gate còn chờ được ghi tại [docs/DE_XUAT_TRIEN_KHAI.md](docs/DE_XUAT_TRIEN_KHAI.md).

Hướng dẫn cài đặt/pilot theo 12 bước: [VCB Production E2E](docs/VCB_PRODUCTION_E2E.md). Kiến trúc runtime secret: [docs/RUNTIME_WEBHOOK_CREDENTIAL.md](docs/RUNTIME_WEBHOOK_CREDENTIAL.md). Ghi kết quả điện thoại vào [checklist riêng](docs/VCB_DEVICE_CHECKLIST.md). Không cần sửa source để đổi cấu hình triển khai.

Backend có `GET /api/health` (chỉ kiểm tra cấu hình cục bộ), `npm run sheets:bootstrap` (xem kế hoạch; thêm `-- --apply` để tạo tab/header còn thiếu) và `npm run check:deployment` (health + dry-run không ghi giao dịch). Hai lệnh đọc cấu hình riêng từ `vercel/.env.local`.

Nhập lịch sử Money Lover và API đọc thu chi hợp nhất: [Money Lover History Import](docs/MONEYLOVER_HISTORY_IMPORT.md).

## Google Sheets

Tạo một bảng tính riêng cho môi trường của bạn và cấu hình `SPREADSHEET_ID` trong Vercel. Không đưa ID thật, secret hoặc khóa service account vào repository.

---

## Hướng dẫn Chạy Nhanh (3 Bước)

### Bước 1: Deploy thư mục `vercel/` lên Vercel
1. Vào thư mục `vercel/`:
   ```bash
   cd vercel
   npm ci
   npm test
   npm run typecheck
   ```
2. Import repo vào Vercel, đặt Root Directory = `vercel`, Framework = Other, Node 24.x, Install Command = `npm ci`; không đặt output frontend. Handler HTTP nằm trong `api`, mã nội bộ nằm trong `lib`.
3. Cài đặt bốn biến môi trường trong **Vercel Settings -> Environment Variables**. Dùng `.env.example` làm danh sách tên, rồi tự điền ID Sheet và thông tin service account. Không commit `.env`, `android/local.properties` hay khóa service account.
   - `SPREADSHEET_ID` = ID Sheet riêng tư
   - `WEBHOOK_SECRET` = chuỗi bí mật ngẫu nhiên được tạo riêng
   - `GOOGLE_CLIENT_EMAIL` = `<email-service-account-của-bạn>` (Nhớ bấm Share quyền **Editor** cho email này vào file Google Sheet trên)
   - `GOOGLE_PRIVATE_KEY` = `<private-key-trong-file-json-service-account>`

### Bước 2: Build Public Safe APK trong thư mục `android/`
1. Mở Android Studio -> chọn **Open** -> trỏ vào thư mục `android/`.
2. (Tùy chọn) `WEBHOOK_URL` mặc định trỏ tới production backend `https://finance-rust-nine-16.vercel.app/api/transaction`. Có thể đổi endpoint qua `local.properties` hoặc biến môi trường `WEBHOOK_URL`. Tuyệt đối **không nhúng `WEBHOOK_SECRET` vào APK**.
3. Chạy lệnh build và kiểm thử:
   ```bash
   cd android
   ./gradlew assembleDebug testDebugUnitTest verifyWebhookConfigurationPolicy
   ```
   Trên Windows dùng `gradlew.bat assembleDebug testDebugUnitTest verifyWebhookConfigurationPolicy`. APK được đóng gói an toàn tại `android/app/build/outputs/apk/debug/app-debug.apk` và có thể build/phân phối qua GitHub Actions workflow dispatch `build-pilot-apk.yml`.

### Bước 3: Cấu hình mã kết nối và cấp quyền trên điện thoại (UX 4 bước)
1. Cài đặt APK lên thiết bị và mở ứng dụng **Đồng Bộ Thu Chi**.
2. Tại phần **Cấu hình kết nối**, nhập **Mã kết nối** (giá trị bí mật khớp với `WEBHOOK_SECRET` trên Vercel).
3. Nhấn **Lưu và kiểm tra**. Ứng dụng sẽ thực hiện dry-run xác thực tới backend Vercel. Khi hiển thị **Kết nối thành công**, secret đã được mã hóa an toàn trong **Android Keystore (AES/GCM)**.
4. Bật **Quyền đọc thông báo** và **Pin không hạn chế (Unrestricted)** (với Android 13+, vào Cài đặt -> Ứng dụng -> Đồng Bộ Thu Chi -> Cho phép cài đặt bị hạn chế trước nếu cần).

## Hợp đồng sự kiện và bảo vệ dữ liệu

`eventId` là đầy đủ 64 ký tự hex thường của SHA-256 trên UTF-8 `bank:postedAt:normalizedText`. `postedAt` là số nguyên epoch milliseconds gốc từ notification, giữ nguyên trong SQLite qua mọi lần retry. Android và backend chạy cùng bộ vector ở `shared/event-id-vectors.tsv`, gồm NFC/NFD và các khoảng trắng Unicode. Nội dung giống nhau ở thời điểm khác nhau tạo hai ID khác nhau. Server từ chối ID không khớp.

Backend che identifier có từ 8 chữ số, liền nhau hoặc ngăn bằng space/NBSP hoặc dấu `-`, `.`, `,`, `/`, `_`, trước khi ghi `Raw_Capture.raw_text`, `Transactions.description` và `counterparty`. Các giá trị có đơn vị tiền, token ngày/giờ và cột amount/balance/event_id được giữ nguyên. Đây là quy tắc xác định cho format MVP, không phải bộ nhận diện mọi dữ liệu cá nhân.

Dedup đọc toàn bộ cột ID của cả hai tab; không còn cửa sổ 1.000 dòng. Đọc và append vẫn là hai thao tác: request đồng thời có thể ghi trùng. Mutex Android tuần tự hóa upload trong một tiến trình, không thay thế khóa nguyên tử phía Sheets.

## Kiểm tra tự động

Workflow `.github/workflows/ci.yml` chạy `npm ci`, `npm test`, `npm run typecheck` trên Node 24.11.0 và `assembleDebug testDebugUnitTest verifyWebhookConfigurationPolicy validateReleaseWebhookConfig` cùng security scan trên JDK 17/Android SDK 34.
Workflow `.github/workflows/build-pilot-apk.yml` build public-safe APK và upload artifact `finance-bridge-vcb-pilot-apk` khi được kích hoạt qua `workflow_dispatch`.


## Theo dõi tiền lãi
Tài khoản → Theo dõi tiền lãi dùng kỳ tháng 25–25 và sổ Google Sheets hiện tại. Cấu hình, quy tắc và giới hạn nghiệm thu: [INTEREST_TRACKING](docs/INTEREST_TRACKING.md).
