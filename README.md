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
   npm install
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
3. Checkout hiện chưa có Gradle Wrapper (`gradlew`/`gradlew.bat`), nên chưa thể build APK trực tiếp từ hướng dẫn này. Cần bổ sung wrapper tương thích và Android SDK/JDK trước khi build. Bản release yêu cầu URL HTTPS và secret riêng đã được cấu hình; bản debug để trống sẽ không gửi webhook. Phần Android hiện đã có mã outbox SQLite và retry nhưng chưa được biên dịch hoặc nghiệm thu trên thiết bị.

### Bước 3: Cấp quyền một lần trên điện thoại
1. Nếu dùng Android 13/14/15+: Vào `Cài đặt -> Ứng dụng -> Đồng Bộ Thu Chi -> Menu 3 chấm góc phải -> Cho phép cài đặt bị hạn chế (Allow restricted settings)`.
2. Mở ứng dụng **Đồng Bộ Thu Chi**, chạm vào 2 dòng trạng thái trên màn hình để bật **Quyền đọc thông báo** và **Pin không hạn chế (Unrestricted)**.
3. Chọn **Kiểm tra kết nối** để xác nhận webhook và cấu trúc Sheet bằng dry-run. Thao tác này không ghi giao dịch mẫu vào sổ.

Ứng dụng lưu cục bộ các thông báo đã qua bộ lọc. Lỗi mạng được retry; giao dịch bị chặn do cấu hình được giữ để gửi lại sau khi sửa lỗi. Tải/cài dependencies và deploy chỉ thực hiện sau khi chủ dự án cho phép.

