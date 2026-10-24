# Finance Bridge — V3.1 đang hoàn thiện theo gate nghiệm thu

Gói mã nguồn gồm:
1. **`vercel/`**: Endpoint TypeScript trên Vercel, parser, bộ lọc, danh mục và test với Sheets giả lập.
2. **`android/`**: Ứng dụng single-module dùng Kotlin, Jetpack Compose, `NotificationListenerService`, `SensitiveFilter`, WorkManager và SQLite cục bộ.
3. **`SPEC_V3_1.md`**: Bản đặc tả kỹ thuật chốt thi công V3.1.

Mã nguồn chưa được nghiệm thu trên điện thoại hay Google Sheets thật. MVP ưu tiên VCB; BIDV/VietinBank cần mẫu đã che dữ liệu để viết parser. Tiến độ triển khai và các gate còn chờ được ghi tại [docs/DE_XUAT_TRIEN_KHAI.md](docs/DE_XUAT_TRIEN_KHAI.md).

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
2. Đẩy thư mục `vercel/` lên GitHub và import vào Vercel (hoặc chạy `npx vercel --prod`).
3. Cài đặt bốn biến môi trường trong **Vercel Settings -> Environment Variables**. Dùng `.env.example` làm danh sách tên, rồi tự điền ID Sheet và thông tin service account. Không commit `.env`, `android/local.properties` hay khóa service account.
   - `SPREADSHEET_ID` = ID Sheet riêng tư
   - `WEBHOOK_SECRET` = chuỗi bí mật ngẫu nhiên được tạo riêng
   - `GOOGLE_CLIENT_EMAIL` = `<email-service-account-của-bạn>` (Nhớ bấm Share quyền **Editor** cho email này vào file Google Sheet trên)
   - `GOOGLE_PRIVATE_KEY` = `<private-key-trong-file-json-service-account>`

### Bước 2: Build APK trong thư mục `android/`
1. Mở Android Studio -> chọn **Open** -> trỏ vào thư mục `android/`.
2. Mở file `android/local.properties` (hoặc tạo mới cạnh `settings.gradle.kts`) và thêm 2 dòng:
   ```properties
   WEBHOOK_URL=https://ten-project-cua-ban.vercel.app/api/transaction
   WEBHOOK_SECRET=mat-khau-bi-mat-tu-chon
   ```
3. Checkout có Gradle Wrapper 8.7, pin checksum bản phân phối và wrapper JAR đã được đối chiếu checksum chính thức. Chuẩn bị JDK 17, Android SDK platform 34 và build-tools 34.0.0; đặt `ANDROID_HOME` tới SDK, rồi chạy:
   ```bash
   cd android
   ./gradlew assembleDebug testDebugUnitTest
   ```
   Trên Windows dùng `gradlew.bat assembleDebug testDebugUnitTest`. Không cần cài Gradle toàn cục. Cấu hình giữ AGP 8.5.2, Kotlin 1.9.24 và Compose Compiler 1.5.14. Bản debug không cần webhook/secret và sẽ chuyển sự kiện sang `BLOCKED` nếu chưa cấu hình; bản release yêu cầu HTTPS và secret riêng. Không commit APK hoặc `local.properties`.

## Hợp đồng sự kiện và bảo vệ dữ liệu

`eventId` là đầy đủ 64 ký tự hex thường của SHA-256 trên UTF-8 `bank:postedAt:normalizedText`. `postedAt` là số nguyên epoch milliseconds gốc từ notification, giữ nguyên trong SQLite qua mọi lần retry. Android và backend chạy cùng bộ vector ở `shared/event-id-vectors.tsv`, gồm NFC/NFD và các khoảng trắng Unicode. Nội dung giống nhau ở thời điểm khác nhau tạo hai ID khác nhau. Server từ chối ID không khớp.

Đây là thay đổi giao thức trước rollout: APK và backend phải được nâng đồng bộ. Không tự viết lại ID của outbox cũ hoặc Sheet cũ; các bản ghi pending dùng ID 16 ký tự sẽ bị chặn `EVENT_ID_MISMATCH`, vẫn giữ payload cục bộ. Nếu đã thử bản nền cũ, cần xử lý/đối soát hết hàng đợi với backend cũ trước khi nâng. Không phát lại dữ liệu đã ghi dưới thuật toán cũ sang thuật toán mới.

Backend che identifier có từ 8 chữ số, liền nhau hoặc ngăn bằng space/NBSP/dấu `-`, trước khi ghi `Raw_Capture.raw_text`, `Transactions.description` và `counterparty`. Các giá trị có đơn vị tiền, token ngày/giờ và cột amount/balance/event_id được giữ nguyên. Đây là quy tắc xác định cho format MVP, không phải bộ nhận diện mọi dữ liệu cá nhân.

Dedup đọc toàn bộ cột ID của cả hai tab; không còn cửa sổ 1.000 dòng. Đọc và append vẫn là hai thao tác: request đồng thời có thể ghi trùng. Mutex Android tuần tự hóa upload trong một tiến trình, không thay thế khóa nguyên tử phía Sheets.

## Kiểm tra tự động

Workflow `.github/workflows/ci.yml` chạy `npm ci`, `npm test`, `npm run typecheck` trên Node 24.11.0 và `assembleDebug testDebugUnitTest` trên JDK 17/Android SDK 34. Debug CI không dùng dữ liệu hoặc secret thật. Build và unit test không thay thế E2E điện thoại hoặc thử Google Sheets production.

### Bước 3: Cấp quyền một lần trên điện thoại
1. Nếu dùng Android 13/14/15+: Vào `Cài đặt -> Ứng dụng -> Đồng Bộ Thu Chi -> Menu 3 chấm góc phải -> Cho phép cài đặt bị hạn chế (Allow restricted settings)`.
2. Mở ứng dụng **Đồng Bộ Thu Chi**, chạm vào 2 dòng trạng thái trên màn hình để bật **Quyền đọc thông báo** và **Pin không hạn chế (Unrestricted)**.
3. Chọn **Kiểm tra kết nối** để xác nhận webhook và cấu trúc Sheet bằng dry-run. Thao tác này không ghi giao dịch mẫu vào sổ.

Ứng dụng lưu cục bộ các thông báo đã qua bộ lọc. Lỗi mạng được retry; giao dịch bị chặn do cấu hình được giữ để gửi lại sau khi sửa lỗi. Tải/cài dependencies và deploy chỉ thực hiện sau khi chủ dự án cho phép.

