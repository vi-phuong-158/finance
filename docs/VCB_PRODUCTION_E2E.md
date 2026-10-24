# Cài đặt và kiểm thử VCB thực tế

Hướng dẫn dành cho pilot **một điện thoại**. CI xanh chứng minh build/test tự động; chỉ chủ dự án có thể xác nhận E2E thực tế sau khi tự đối soát trên điện thoại và Sheet riêng. Ghi kết quả vào [VCB_DEVICE_CHECKLIST.md](VCB_DEVICE_CHECKLIST.md).

Chuẩn bị tài khoản Google/Vercel/GitHub, điện thoại Android 8 trở lên có VCB Digibank và thông báo biến động số dư, máy tính có Node 24.11 trở lên, JDK 17, Android SDK 34/build-tools 34.0.0. Có thể chuẩn bị JDK/SDK qua Android Studio. Không nhập mật khẩu ngân hàng vào Finance Bridge.

## Bước 1 — Tạo Google Sheet riêng

Tạo bảng tính mới, ví dụ “Finance Bridge Pilot”, giữ chia sẻ **Restricted**. Phần giữa `/d/` và `/edit` trong địa chỉ Sheet là `SPREADSHEET_ID`, chỉ lưu vào cấu hình riêng. Không cần tự tạo cột: bước 3 có công cụ bootstrap. Tab trống mặc định có thể giữ lại, công cụ không xóa tab khác.

Nếu từng thử bản eventId 16 ký tự cũ, xử lý/đối soát hết outbox với backend cũ trước khi nâng. Không viết lại ID hoặc phát lại lịch sử qua thuật toán mới.

## Bước 2 — Tạo service account và cấp quyền

1. Trong [Google Cloud Console](https://console.cloud.google.com/), chọn/tạo project riêng.
2. Vào **APIs & Services → Library**, bật **Google Sheets API**.
3. Vào **IAM & Admin → Service Accounts**, tạo service account cho Finance Bridge. Không cần vai trò Editor/Owner trên toàn project.
4. Mở service account → **Keys → Add key → Create new key → JSON**. Lưu file tải về **ngoài repo**, không gửi lên chat/GitHub. Nếu tổ chức chặn tạo key, dừng và hỏi quản trị viên; không tắt chính sách bảo mật.
5. Trong Sheet, bấm **Share**, thêm email service account với quyền **Editor**. Chỉ cấp quyền trên Sheet cần dùng, không chia sẻ công khai.

Trong JSON, dùng `client_email` cho `GOOGLE_CLIENT_EMAIL`, `private_key` cho `GOOGLE_PRIVATE_KEY`. Không đưa cả file JSON vào Vercel/repo.

## Bước 3 — Cấu hình riêng và bootstrap Sheet

Mở terminal trong thư mục `vercel`, chạy `npm ci`. Sao chép `.env.example` thành `.env.local`; Git bỏ qua file này. Điền bốn giá trị riêng:

```dotenv
SPREADSHEET_ID=ID_RIENG_CUA_BAN
WEBHOOK_SECRET=CHUOI_NGAU_NHIEN_RIENG
GOOGLE_CLIENT_EMAIL=EMAIL_SERVICE_ACCOUNT
GOOGLE_PRIVATE_KEY="NOI_DUNG_private_key_TU_JSON"
```

Tạo secret trên máy cá nhân bằng:

```bash
node -e "console.log(require('node:crypto').randomBytes(32).toString('hex'))"
```

Chỉ chép kết quả vào cấu hình riêng. Hệ thống yêu cầu secret 32–128 ký tự `A–Z`, `a–z`, `0–9`, `_`, `-`, không dùng giá trị mặc định/placeholder.

Giữ nguyên `BEGIN/END` và nội dung trường `private_key`. Trong `.env.local`, có thể đặt key trong nháy kép với `\n` như JSON; công cụ chấp nhận cả newline thật và dạng escape. Không thêm các trường JSON khác vào giá trị.

Chạy trước lệnh **chỉ xem kế hoạch**, chưa ghi:

```bash
npm run sheets:bootstrap
```

Nếu báo `CHANGES_REQUIRED`, tạm dừng người/ứng dụng khác sửa Sheet rồi chạy:

```bash
npm run sheets:bootstrap -- --apply
npm run sheets:bootstrap
```

Lần cuối phải báo `ALREADY_READY`. Lệnh kiểm tra truy cập bảng tính, tạo hai tab/header còn thiếu bằng một batch và giữ dữ liệu hợp lệ. Nó không sửa bảng có dữ liệu nhưng header sai/thiếu/đảo cột, công thức ở header hoặc dữ liệu ngoài cột chuẩn: gặp các trường hợp đó sẽ dừng `SHEET_SCHEMA_MISMATCH` trước khi ghi. Không xóa bảng để vượt lỗi nếu chưa sao lưu/đối soát riêng.

| Tab | Header từ cột A |
| --- | --- |
| Transactions | time, bank, type, amount, balance, counterparty, description, category, event_id |
| Raw_Capture | time, bank, raw_text, event_id |

Bootstrap cần khoảng thời gian không có cộng tác viên sửa schema. Batch ghi là một thao tác, nhưng bước kiểm tra trước ghi không khóa được các ứng dụng khác.

## Bước 4 — Cấu hình và deploy Vercel

1. Import repo `vi-phuong-158/finance`, dùng branch đã được người duyệt chấp thuận. Agent không tự merge PR này.
2. Chọn **Root Directory = `vercel`**, **Framework Preset = Other**, Node **24.x**, Install Command **`npm ci`**. Không đặt build/output của frontend; dự án chỉ có hai Node API.
3. Trong **Settings → Environment Variables**, thêm bốn tên ở bước 3 cho Production. Chép giá trị, không chép dấu nháy bao ngoài của `.env.local`. Chọn Secret/Sensitive cho secret/private key nếu giao diện hỗ trợ.
4. Preview dùng Sheet/secret thử riêng. Sau mỗi lần sửa env, redeploy để cấu hình có hiệu lực.
5. Deploy và lấy domain HTTPS ổn định. URL webhook phải là `https://DOMAIN_CUA_BAN/api/transaction`, không có user/password, query hay fragment.

Điện thoại phải gọi được endpoint trực tiếp. Trang đăng nhập Vercel/Deployment Protection không phải phản hồi API. Chỉ cho phép truy cập production endpoint chủ định, vẫn giữ `X-Webhook-Secret`; không tắt bảo vệ các preview không liên quan. Không cần cài CLI Vercel để làm các bước này.

## Bước 5 — Kiểm tra health và dry-run

Mở `https://DOMAIN_CUA_BAN/api/health` trong trình duyệt. Mong đợi HTTP 200:

```json
{"alive":true,"version":"3.1.0","environmentReady":true}
```

HTTP 503/`environmentReady:false` là cấu hình thiếu hoặc không hợp lệ. Health **không gọi Google Sheets**, chưa chứng minh quyền Sheet/kết nối Google.

Thêm `WEBHOOK_URL=https://DOMAIN_CUA_BAN/api/transaction` vào `.env.local`, rồi chạy:

```bash
npm run check:deployment
```

Mong đợi `health: READY`, `dryRun: VALIDATED_TRANSACTION`, `writes: 0`. Công cụ gửi mẫu giả có `dryRun:true`, không ghi dòng giao dịch. Không tự nhập eventId hoặc sửa source. Nếu lỗi, kiểm tra env, domain, Deployment Protection, header và quyền Editor. Chưa dry-run đạt thì chưa thử giao dịch thật.

## Bước 6 — Build APK có cấu hình

Pilot đầu có thể dùng **debug APK đã cấu hình**. Debug CI để trống URL/secret và không thể tự gửi webhook.

Sao chép `android/local.properties.example` thành `android/local.properties`, điền URL và **cùng secret** phía Vercel. Giữ dòng `sdk.dir` nếu Android Studio đã tạo. Đây là file cấu hình, không sửa Kotlin. Cũng có thể dùng hai biến môi trường cùng tên; env ưu tiên hơn file. Không truyền secret qua tham số dòng lệnh/log CI.

Trong thư mục `android`:

```bash
./gradlew --no-daemon assembleDebug testDebugUnitTest
```

Windows dùng `gradlew.bat --no-daemon assembleDebug testDebugUnitTest`. APK ở `android/app/build/outputs/apk/debug/app-debug.apk`. Không commit/chia sẻ công khai: APK cá nhân có secret có thể trích xuất.

Để tạo **release**, dùng Android Studio → **Build → Generate Signed App Bundle / APK → APK**, chọn app/release và keystore riêng ngoài repo. Giữ keystore/mật khẩu an toàn. Build release bị chặn nếu cấu hình thiếu, URL dùng HTTP hoặc secret mặc định/placeholder. `assembleRelease` riêng có thể tạo APK **unsigned**, phải ký trước khi cài. Không thêm cấu hình ký/keystore vào Git.

## Bước 7 — Cài APK

Chuyển APK qua USB, cho phép cài từ nguồn đã chọn, mở **Đồng Bộ Thu Chi**. Nếu có ADB, dùng `adb install -r <duong-dan-APK>`.

Khi sửa URL/secret nhúng trong APK, build/cài **bản cập nhật cùng applicationId và chữ ký**. Không gỡ app/“Clear data”: sẽ xóa outbox chưa gửi. Debug APK thường cần cùng debug keystore trên máy đã build; giữ keystore đó cho các lần pilot. Không gỡ app để vượt lỗi chữ ký khi còn giao dịch chờ.

## Bước 8 — Cấp quyền

Trong app, bật **Quyền đọc thông báo** và **Pin không hạn chế**. Với một số APK cài ngoài, vào thông tin app → menu ba chấm → **Allow restricted settings**, rồi cấp Notification Access. Tên mục khác nhau theo Android/hãng máy. Kiểm tra VCB cũng được phép hiển thị notification.

Nhấn **Kiểm tra kết nối** trong app. Phải báo kiểm tra giao dịch thành công, chưa ghi dữ liệu. Số dòng Sheet không tăng do dry-run.

## Bước 9 — Tạo giao dịch VCB nhỏ

Chọn số tiền nhỏ mà bạn chủ động chấp nhận để thử, tạo một giao dịch chuyển đi và, nếu có thể, một giao dịch nhận về. Không dùng OTP/mật khẩu làm dữ liệu thử. Finance Bridge chỉ đọc thông báo từ package được cho phép sau bộ lọc nhạy cảm.

Không gửi notification thật lên repo/chat. QR/POS chỉ được kiểm thử với nội dung nằm trong envelope VCB đã hỗ trợ; chưa có bằng chứng cho mọi format thực tế. Format không chắc chắn phải vào Raw_Capture, không đoán giao dịch.

## Bước 10 — Kiểm tra Sheet

Kiểm tra từng dòng: VCB; CHI/THU đúng; amount dương, đúng giá trị; balance đúng kể cả 0; time theo notification; event_id 64 ký tự; description/counterparty không còn full identifier dài. Đối soát category, có thể chỉnh category thủ công, không sửa amount để làm khớp tổng.

Không thấy Transactions thì xem Raw_Capture và mã lỗi trong app. Khi cần hỗ trợ chỉ gửi metadata/mã lỗi đã bỏ dữ liệu riêng, không gửi raw notification, key hoặc số tài khoản.

## Bước 11 — Thử mất mạng và BLOCKED recovery

**Notification phải thực sự đến và được tiếp nhận trước khi thử độ bền hàng đợi.** Bật Airplane Mode trước giao dịch có thể làm VCB chưa nhận push; trường hợp đó chưa chứng minh outbox làm mất dữ liệu.

Trong cửa sổ thử có kiểm soát, tạm chặn riêng kết nối đến domain webhook trên mạng thử nếu bạn có công cụ làm việc đó, vẫn để VCB nhận push. Tạo giao dịch nhỏ, chờ VCB nhận notification và xác nhận bản ghi đang chờ trong app. Dùng lỗi kết nối/timeout để kiểm tra nhánh retry; không dùng 401/404 hoặc lỗi cấu hình cố định, vì chúng đi nhánh BLOCKED bên dưới. Ngắt mạng điện thoại, bỏ chặn endpoint, mở lại app để kiểm tra hàng đợi vẫn còn; sau đó bật mạng. Chờ WorkManager gửi và chuyển terminal; thời điểm phụ thuộc Android/OEM. Nếu không kiểm soát được thời điểm/mạng thử, giữ mục này chưa nghiệm thu, không suy ra kết quả từ test mô phỏng. Khi terminal, raw/title cục bộ được clear, giữ metadata. Không xuất DB chứa dữ liệu thật vào repo để chứng minh.

Lỗi cố định phải chuyển BLOCKED và giữ payload, không retry vô hạn:

| Lỗi | Khôi phục |
| --- | --- |
| URL/secret trong APK sai | Sửa cấu hình riêng, build/cài cập nhật cùng chữ ký, không gỡ app |
| Secret server sai / server thiếu env | Sửa env Vercel, redeploy, bảo đảm khớp APK |
| SHEET_SCHEMA_MISMATCH | Dừng ghi, đối chiếu schema/backup riêng; bootstrap không tự sửa bảng có dữ liệu |
| SHEETS_PERMISSION_DENIED / SHEET_NOT_FOUND | Kiểm tra ID và quyền Editor; redeploy nếu đổi env |

Sau khi sửa: health/dry-run lại, nhấn **Gửi lại giao dịch lỗi**. Không xóa hàng đợi để làm hết lỗi.

## Bước 12 — Đối soát và nghiệm thu

Đối chiếu từng giao dịch với nguồn VCB riêng: số lượng, chiều, tiền, số dư, giờ và category. Kiểm tra cả Raw_Capture; không bỏ qua mẫu chưa parse khi tính đủ/thiếu.

Theo dõi cùng eventId qua lỗi/retry: chỉ có một dòng trên tổng hai tab. Giao dịch thứ hai có text giống nhưng postedAt khác là sự kiện khác, không phải phép thử duplicate. Retry thật giữ timestamp/payload gốc, app tự xử lý; không sửa eventId bằng tay.

Hoàn tất checklist khi có bằng chứng từng mục. Chỉ chạy một điện thoại trong pilot. Sheets đọc rồi append chưa chống trùng nguyên tử khi nhiều request đồng thời. Chưa có kết quả thực tế thì vẫn là **readiness**, chưa phải **Production E2E PASS**.

## Phạm vi bằng chứng tự động

Backend test đi qua normalization/hash/parser/category/masking/dedup đến Sheets giả lập, gồm timeout sau append. Android unit test gọi `UploadProcessor` thật với outbox/HTTP giả để kiểm tra giữ payload, BLOCKED recovery và clear sau terminal. Chúng **không chạy SQLite Android, scheduler WorkManager/OEM hay điện thoại thật**. Cấu trúc SQLite, enqueue sau lưu và WorkManager input chỉ chứa eventId được giữ nguyên.

Tài liệu chính thức: [Sheets batch](https://developers.google.com/workspace/sheets/api/guides/batch), [đọc giá trị/formula](https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets.values/get), [Vercel Node Functions](https://vercel.com/docs/functions/runtimes/node-js), [Vercel env](https://vercel.com/docs/environment-variables).
