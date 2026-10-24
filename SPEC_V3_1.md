# Finance Bridge V3.1 — Đặc tả triển khai và nghiệm thu

> Bổ sung readiness PR #2: mã nghiệp vụ tại `vercel/lib/transaction-service.ts`; `api` chỉ chứa transaction và health. Mọi lần ghi đều kiểm tra header hai tab; schema sai trả SHEET_SCHEMA_MISMATCH, quyền/ID sai trả mã cấu hình có thể khôi phục. Health chỉ xác minh cấu hình cục bộ, không gọi Sheets. Bootstrap qua `npm run sheets:bootstrap` mặc định read-only; `-- --apply` chỉ tạo tab/header trên bảng trống sau khi preflight toàn bộ target. Quy trình triển khai và nghiệm thu thực tế ở [VCB_PRODUCTION_E2E.md](docs/VCB_PRODUCTION_E2E.md).

**Hệ thống Quản lý Thu Chi Cá nhân Tự động: Android APK Forwarder ➔ Vercel Serverless ➔ Google Sheets ➔ ChatGPT / Gemini**

## 1. Mục tiêu & Phạm vi

Xây dựng hệ thống tự động ghi nhận biến động số dư cho **01 người dùng cá nhân** từ thông báo của **03 ngân hàng** (`com.VCB`, `com.vietinbank.ipay`, `com.vnpay.bidv`) với kiến trúc tối giản và chi phí thấp. Phí thực tế phụ thuộc tài khoản, hạn mức và dịch vụ bên ngoài; không cam kết chi phí bằng không.

1. **Không nhập tay:** Giao dịch tự động được bóc tách và ghi vào Google Sheets ngay khi có thông báo biến động số dư.

2. **Không mất giao dịch đã tiếp nhận khi mất mạng:** Lưu notification đã vượt `SensitiveFilter` vào SQLite riêng của ứng dụng. WorkManager giữ `eventId`, gửi khi có mạng và lấy payload từ SQLite; lỗi bị chặn giữ nguyên payload cho thao tác gửi lại.

3. **Không gửi thông tin nhạy cảm:** Bộ lọc `SensitiveFilter` chặn OTP, mã xác thực, cảnh báo đăng nhập, thông tin thẻ và thông báo không có dấu hiệu biến động trước khi lưu vào SQLite hoặc gửi đi.

4. **Không cần build lại APK khi ngân hàng đổi format:** Toàn bộ bộ phân tích cú pháp (Parser) và từ điển phân loại danh mục nằm trên Vercel Serverless.

**Không sử dụng trong V3.1:** Không dùng Supabase, không dùng Firebase, không dùng Redis/KV, không gọi AI API trên luồng ghi webhook, không dùng Google Apps Script, không thuê VPS.

## 2. Kiến trúc Luồng Xử lý (Pipeline Chốt)

```
3 Ngân hàng (VCB / VietinBank / BIDV)
     │
     ▼
Android NotificationListenerService
     │
     ▼
Package Whitelist (Chỉ nhận com.VCB, com.vietinbank.ipay, com.vnpay.bidv)
     │
     ▼
System Flag Filter (Bỏ qua FLAG_GROUP_SUMMARY & FLAG_ONGOING_EVENT)
     │
     ▼
SensitiveFilter (Chặn OTP + Bảo mật/Thẻ/Đăng nhập + Kiểm tra dấu hiệu giao dịch)
     │
     ▼
Chuẩn hóa normalizedText & Tính event_id = SHA256(bank + ":" + postedAt + ":" + normalizedText)
     │
     ▼
SQLite Outbox cục bộ: INSERT OR IGNORE theo eventId
     │
     ▼
Unique WorkManager: enqueueUniqueWork("bank-$eventId", KEEP, input chỉ gồm eventId)
     │
     ▼
Vercel Serverless (POST /api/transaction)
 ├─ 1. Kiểm tra secret, schema input, bank/package và event_id
 ├─ 2. Lọc OTP và xác nhận dấu hiệu giao dịch
 ├─ 3. Chống trùng theo toàn bộ cột event_id của hai tab
 ├─ 4. Chạy parser và phân loại giao dịch
 └─ 5. Ghi đúng 9/4 cột bằng RAW, trả status nghiệp vụ
     │
     ▼
Google Sheets (Chỉ gồm 2 Tab)
 ├─ Transactions (9 cột chuẩn — Lưu giao dịch đã parse thành công)
 └─ Raw_Capture (4 cột — Lưu thông báo có dấu hiệu giao dịch nhưng parser chưa hiểu)
     │
     ▼
Trợ lý AI (ChatGPT / Gemini đọc Sheet) & công cụ báo cáo chỉ đọc

```

## 3. Cấu trúc Logic Nghiệp vụ Chính

Logic listener/filter/upload gồm các file Kotlin bên dưới; SQLite outbox, kiểm tra kết nối và ứng dụng HTTP dùng file riêng. Backend giữ endpoint, parser và danh mục trên Vercel. Google Sheets vẫn có hai tab:

```
Android (Module :app)
├── MainActivity.kt               # Trạng thái quyền, hàng đợi, lỗi và kiểm tra kết nối không ghi
├── BankNotificationListener.kt   # Bắt thông báo, lọc package, gọi SensitiveFilter & WorkManager
├── SensitiveFilter.kt            # Chặn OTP/bảo mật & chỉ cho phép thông báo có dấu hiệu giao dịch
├── UploadWorker.kt               # Worker nhận eventId, retry và cập nhật kết quả
├── TransactionOutbox.kt           # SQLite giữ payload cho tới khi xử lý xong
├── ConnectionCheckWorker.kt       # Dry-run kiểm tra server và header Sheet
└── WebhookClient.kt                # HTTPS, ánh xạ lỗi và status nghiệp vụ

Vercel
├── api/transaction.ts            # Adapter Google Sheets
├── lib/transaction-service.ts    # Kiểm tra input, bảo mật, chống trùng và ghi giao dịch
├── parsers/
│   ├── vcb.ts                    # Bóc tách thông báo Vietcombank (Chạy chính thức ngay từ đầu)
│   ├── vietinbank.ts             # Bóc tách VietinBank (Triển khai sau khi có mẫu thật ở Raw_Capture)
│   └── bidv.ts                   # Bóc tách BIDV (Triển khai sau khi có mẫu thật ở Raw_Capture)
└── categories.ts                 # Từ điển ánh xạ từ khóa -> Danh mục (bao gồm "Chuyển nội bộ")

Google Sheets
├── Transactions                  # 9 cột lưu giao dịch chuẩn hóa
└── Raw_Capture                   # 4 cột lưu thông báo giao dịch chưa parse được (đã che số TK)

```

## 4. Đặc tả Chi tiết Tầng Android APK

### 4.1. Danh sách Package Whitelist

Chỉ xử lý thông báo có `StatusBarNotification.packageName` thuộc đúng 3 giá trị sau:

* `"com.VCB"` ( Ánh xạ mã `bank = "VCB"`)

* `"com.vietinbank.ipay"` (Ánh xạ mã `bank = "VIETINBANK"`)

* `"com.vnpay.bidv"` (Ánh xạ mã `bank = "BIDV"`)

Mọi thông báo từ ứng dụng khác (Zalo, Messenger, Gmail, Telegram, SMS...) phải bị bỏ qua ngay lập tức ở dòng đầu tiên của `onNotificationPosted`, tuyệt đối không đọc nội dung và không ghi log.

### 4.2. Trích xuất Văn bản & Chuẩn hóa `normalizedText`

1. **Bỏ qua thông báo hệ thống:** Bỏ qua ngay nếu `notification.flags` có bật cờ `Notification.FLAG_GROUP_SUMMARY` hoặc `Notification.FLAG_ONGOING_EVENT`.

2. **Đọc nội dung đầy đủ:** Lấy `extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()` trước; nếu `null` hoặc rỗng mới lấy `extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()`.

3. **Quy tắc chuẩn hóa `normalizedText` (Bắt buộc tuân thủ chính xác):**

   * Chuẩn hóa bảng mã Unicode về dựng sẵn NFC: `Normalizer.normalize(rawText, Normalizer.Form.NFC)`.

   * Thay thế toàn bộ ký tự xuống dòng (`\r`, `\n`), tab (`\t`), và khoảng trắng không ngắt (`\u00A0`) thành khoảng trắng chuẩn `' '`.

   * Gom nhiều khoảng trắng liên tiếp thành 01 khoảng trắng duy nhất (`replace(Regex("\\s+"), " ")`) và cắt khoảng trắng hai đầu (`.trim()`).

   * **Tuyệt đối KHÔNG xóa hoặc làm biến đổi:** chữ số, dấu `+/-`, dấu chấm/phẩy phân cách tiền tệ, ngày giờ giao dịch, số dư và nội dung chuyển khoản.

### 4.3. Bộ lọc Bảo mật Hai Lớp (`SensitiveFilter.kt`)

Để ngăn chặn việc gửi nhầm mã OTP hoặc thông tin bảo mật/thẻ/đăng nhập, `SensitiveFilter.shouldProcess(title, normalizedText)` chặn trước SQLite outbox. Server chạy lại hai lớp kiểm tra trước mọi lần ghi Sheet.

1. **Điều kiện 1 — Chặn cứng Từ khóa Nhạy cảm & Phi giao dịch (Blacklist):**

   * Chuyển chuỗi `title + " " + normalizedText` về chữ thường không dấu tiếng Việt.

   * Nếu khớp bất kỳ cụm từ nào sau đây ➔ **Trả về `false` (không lưu hoặc gửi đi):**

     * Nhóm OTP & Xác thực: `otp`, `smart otp`, `soft otp`, `ma xac thuc`, `ma kich hoat`, `ma bao mat`, `verification code`, `activation code`, `passcode`, `khong cung cap ma`, `khong chia se ma`.

     * Nhóm Đăng nhập & Bảo mật tài khoản: `dang nhap`, `thiet bi moi`, `mat khau`, `doi mat khau`, `kich hoat`, `sinh trac hoc`, `khoa the`, `mo khoa the`, `cvv`, `so the`, `han muc tin dung`, `bao tri he thong`, `khuyen mai`, `uu dai`.

2. **Điều kiện 2 — Bắt buộc có Dấu hiệu Biến động Giao dịch (Positive Transaction Signal):**

   * Chỉ trả về `true` (cho phép đi tiếp vào `WorkManager`) khi văn bản đáp ứng **đồng thời**:

     * Có chứa đơn vị tiền tệ hoặc dấu tiền tệ: khớp regex `(?i)(VND|VNĐ|[+-]\s*\d[\d.,]{2,})`.

     * Có chứa ít nhất 01 từ khóa chỉ biến động tài khoản/số dư: khớp regex không dấu `(?i)\b(so du|sd|tk|tai khoan|gd|giao dich|ps|phat sinh|chuyen khoan|thanh toan)\b`.

### 4.4. Sinh khóa `event_id` & Lưu chờ trên thiết bị

1. **Công thức tính `event_id` trên Android:**

   * Tính băm `SHA-256` trên chuỗi UTF-8: `"$bank:$postedAt:$normalizedText"`. `postedAt` là epoch milliseconds nguyên từ `sbn.postTime`, lưu một lần trong SQLite và giữ nguyên khi retry.

   * Lấy **đầy đủ 64 ký tự hex thường** làm `eventId`. Android và backend kiểm tra cùng bộ vector cố định tại `shared/event-id-vectors.tsv`.

   * Cùng bank/text/postedAt tạo cùng ID; postedAt khác tạo ID khác dù nội dung giống nhau. Android cập nhật notification với postTime mới được xem là sự kiện khác; không suy đoán đây là cùng giao dịch. Đây là thay đổi giao thức trước rollout, cần nâng APK/backend đồng bộ và đối soát hàng đợi/Sheet cũ theo hướng dẫn README.

2. **Ghi payload vào SQLite trước khi lập lịch:** `event_id` là khóa duy nhất; `INSERT OR IGNORE` bảo vệ sự kiện đã tồn tại. Chỉ sau khi lưu thành công mới enqueue WorkManager với input gồm eventId. Khi app mở hoặc notification listener khởi động lại, quét payload đang chờ để lập lịch tác vụ còn thiếu.

3. **Đưa vào `WorkManager` với cơ chế unique work:**

   ```
   val workName = "bank-$eventId"
   WorkManager.getInstance(applicationContext).enqueueUniqueWork(
       workName,
       ExistingWorkPolicy.KEEP,
       uploadWorkRequest
   )
   
   ```

   * `ExistingWorkPolicy.KEEP` ngăn thông báo phát lại khi cùng worker còn chờ hoặc đang chạy tạo thêm lượt gửi.

   * Retry dùng chính payload cục bộ gắn với eventId. Lỗi mạng/server tạm thời được retry; lỗi cấu hình chuyển sang `BLOCKED` và có thể gửi lại từ app.

   * Một `Mutex` chỉ cho phép một upload worker hoạt động trong tiến trình ứng dụng. Chống trùng phía Sheets đọc toàn bộ lịch sử hai tab; không khẳng định request đồng thời trên server ghi nguyên tử.

   * Khi có kết quả kết thúc, xóa title/text khỏi SQLite và giữ eventId cùng mã trạng thái 30 ngày.

### 4.5. Giao diện APK Tối giản (`MainActivity.kt`)

`WEBHOOK_URL` và `WEBHOOK_SECRET` được truyền trực tiếp qua `BuildConfig` khi biên dịch APK cá nhân. Màn hình chính chỉ hiển thị:

* `✓ Quyền đọc thông báo` (Nhấn vào để mở màn hình cấp quyền Notification Access).

* `✓ Pin không hạn chế (Unrestricted)` (Nhấn vào để mở hộp thoại `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, ngăn Samsung/OEM ngắt dịch vụ ngầm).

* `✓ Kết nối máy chủ` (Kết quả dry-run kiểm tra webhook và schema hai tab).

* Nút `[ Kiểm tra kết nối ]` (Gửi payload giả với `dryRun: true`, không ghi dữ liệu).

* Số giao dịch chờ/lỗi và nút gửi lại các bản ghi bị chặn.

* Dòng thông tin cuối màn hình: `Phiên bản: 1.0`.

## 5. Đặc tả Chi tiết Tầng Vercel Serverless

### 5.1. Endpoint `POST /api/transaction`

Payload JSON nhận từ Android APK:

```
{
  "eventId": "f951297f84a41a6910d1a872eacf8d760051820fca47bd1c087bc66d7c52ff6f",
  "bank": "VCB",
  "packageName": "com.VCB",
  "title": "Thông báo VCB",
  "text": "Số dư TK VCB ACCOUNT-TEST -10,000 VND lúc 01-01-2026 12:00:00. Số dư 50,000 VND. Ref MBVCB.TESTREF.TEST SUPERMARKET",
  "postedAt": 1792815333000
}

```

Quy trình xử lý tại `api/transaction.ts` và `lib/transaction-service.ts`:

1. **Kiểm tra cấu hình và Secret:** Thiếu cấu hình server trả 503; secret sai trả về `401 Unauthorized`.

2. **Lớp lọc dự phòng:** Chạy lại blacklist và positive transaction signal trước khi khởi tạo Google Sheets client. Status bị lọc trả về 200 để worker kết thúc mà không ghi vào Sheet.

3. **Kiểm tra schema input:** Chỉ nhận POST Content-Type JSON, bank/package khớp whitelist, timestamp hợp lệ, eventId khớp SHA-256, title tối đa 1 KiB và text tối đa 8 KiB UTF-8.

4. **Dry-run kiểm tra Sheet:** So sánh header của hai tab; trả `VALIDATED_TRANSACTION` hoặc `VALIDATED_RAW_CAPTURE`; không append.

5. **Kiểm tra chống trùng thực tế trên Google Sheets (toàn bộ lịch sử):**

   * Đọc toàn bộ cột `I` (`event_id`) của tab `Transactions` và cột `D` của tab `Raw_Capture`. Kiểm tra tất cả ID trả về, không cắt cửa sổ.

   * Nếu `eventId` đã tồn tại ➔ trả về ngay `200 OK` (`status: "DUPLICATE_IGNORED"`).

   * *Ghi chú phạm vi:* Đây là chống phát lại tuần tự trên toàn lịch sử, không phải cơ chế append nguyên tử; hai request đồng thời vẫn cần đối soát.

6. **Chạy Parser ngân hàng tương ứng:**

   * Gọi `parseVcb(text)`, `parseVietinbank(text)`, hoặc `parseBidv(text)`.

   * **Trường hợp 1 — Parse thành công:** Gán danh mục qua `categories.ts`, dùng `RAW` để append 9 cột và trả `SAVED_TRANSACTION`.

   * **Trường hợp 2 — Parser chưa hiểu format:** `maskSensitiveIdentifiers` che chuỗi từ 8 chữ số, liền nhau hoặc có space/NBSP hoặc dấu `-`, `.`, `,`, `/`, `_`; bảo toàn token tiền có đơn vị và ngày/giờ. Dùng `RAW` append 4 cột và trả `SAVED_RAW_CAPTURE`. Cùng hàm che được áp dụng cho description/counterparty trước mọi lần ghi Transactions; amount/balance và event_id không qua masking.

### 5.2. Đặc tả Parser Đầu tiên: `parsers/vcb.ts`

Mẫu giả lập đã ẩn danh:
`"Số dư TK VCB ACCOUNT-TEST -10,000 VND lúc 01-01-2026 12:00:00. Số dư 50,000 VND. Ref MBVCB.TESTREF.TEST SUPERMARKET"`

Kết quả bóc tách chuẩn:

* `time`: `"01/01/2026 12:00"` (hoặc `"2026-01-01 12:00:00"`)

* `bank`: `"VCB"`

* `type`: Dấu `-` trước số tiền ➔ `"CHI"`; dấu `+` ➔ `"THU"`

* `amount`: `10000` (Kiểu số nguyên `number`, bỏ dấu phẩy/chấm)

* `balance`: `50000` (Kiểu số nguyên `number`, hoặc `null` nếu thông báo ẩn số dư)

* `counterparty`: `"TEST SUPERMARKET"` (hoặc rút gọn `"TEST SUPERMARKET"`)

* `description`: Phần thông tin sau chữ `Ref`

* `category`: Kết quả từ hàm `matchCategory(counterparty + " " + description, type)`

### 5.3. Phân loại Danh mục & Xử lý Chuyển khoản Nội bộ (`categories.ts`)

Để giải quyết bài toán chuyển tiền qua lại giữa các tài khoản của chính mình (ví dụ chuyển 5 triệu từ VCB sang BIDV làm phát sinh 1 lệnh `CHI` và 1 lệnh `THU`) mà **không cần thêm cột thứ 10 (`is_excluded`)**, hệ thống sử dụng danh mục đặc biệt **`Chuyển nội bộ`**:

```
// File: categories.ts
export const SELF_TRANSFER_KEYWORDS: string[] = [
  'CHUYEN TIEN NOI BO',
  'TEST ACCOUNT', // Tên chủ tài khoản (cấu hình theo tên người dùng để tự nhận diện khi chuyển giữa VCB <-> BIDV <-> VietinBank)
];

export const CATEGORY_RULES: Record<string, string[]> = {
  'Chuyển nội bộ': SELF_TRANSFER_KEYWORDS,
  'Siêu thị': ['SUPERMARKET', 'WINMART', 'GO!', 'LOTTE', 'COOPMART', 'BACH HOA XANH', 'AEON'],
  'Đi lại': ['GRAB', 'XANH SM', 'GSM', 'BE ', 'TAXI', 'VETC', 'EPASS'],
  'Cafe': ['HIGHLANDS', 'PHUC LONG', 'KATINAT', 'STARBUCKS', 'THE COFFEE HOUSE', 'CONG CAPHE'],
  'Xăng xe': ['PETROLIMEX', 'PVOIL', 'XANG'],
  'Ăn uống': ['KFC', 'LOTTERIA', 'PIZZA', 'GOLDEN GATE', 'HADILAO', 'MANWAH', 'GOGI'],
  'Điện nước & Net': ['EVN', 'DIEN LUC', 'CAP NUOC', 'VIETTEL', 'VNPT', 'FPT'],
  'Y tế': ['LONG CHAU', 'PHARMACITY', 'AN KHANG', 'BENH VIEN', 'PHONG KHAM'],
  'Mua sắm': ['SHOPEE', 'LAZADA', 'TIKTOK', 'TIKI', 'UNIQLO'],
};

export function matchCategory(text: string, type: 'THU' | 'CHI'): string {
  const upper = text.toUpperCase();
  for (const [category, keywords] of Object.entries(CATEGORY_RULES)) {
    if (keywords.some((kw) => upper.includes(kw))) {
      return category;
    }
  }
  return type === 'THU' ? 'Thu nhập / Nhận CK' : 'Chưa phân loại';
}

```

*(Trong giai đoạn MVP, người dùng cũng có thể đổi thủ công cột `category` trên Sheet thành `Chuyển nội bộ`. Khi tính tổng Thu/Chi thực tế hoặc khi hỏi AI, chỉ cần loại trừ các dòng có `category == "Chuyển nội bộ"`).*

## 6. Thiết kế Google Sheets (2 Tab)

### 6.1. Tab 1: `Transactions` (Đúng 9 cột: A ➔ I)

| Cột | Tên cột | Ví dụ hiển thị | Mô tả | 
| ----- | ----- | ----- | ----- | 
| **A** | `time` | `01/01/2026 12:00` | Thời gian giao dịch từ thông báo ngân hàng | 
| **B** | `bank` | `VCB` | `VCB` / `VIETINBANK` / `BIDV` | 
| **C** | `type` | `CHI` | `THU` hoặc `CHI` | 
| **D** | `amount` | `10000` | Số tiền giao dịch (VND) | 
| **E** | `balance` | `50000` | Số dư sau giao dịch (VND) | 
| **F** | `counterparty` | `TEST SUPERMARKET` | Đối tác / Đơn vị nhận hoặc gửi | 
| **G** | `description` | `GoVietTri124...` | Nội dung giao dịch / Mã tham chiếu | 
| **H** | `category` | `Siêu thị` | Danh mục (`Siêu thị`, `Cafe`, `Chuyển nội bộ`, `Chưa phân loại`...) | 
| **I** | `event_id` | SHA-256 hex | Mã băm `SHA256(bank + ":" + postedAt + ":" + normalizedText)` (64 ký tự hex) |

### 6.2. Tab 2: `Raw_Capture` (4 cột: A ➔ D)

Chỉ lưu những thông báo từ 3 ngân hàng đã vượt qua `SensitiveFilter` (có dấu hiệu giao dịch tiền tệ) nhưng Parser chưa bóc tách được:

* **Cột A (`time`):** Thời điểm nhận thông báo

* **Cột B (`bank`):** `VCB` / `VIETINBANK` / `BIDV`

* **Cột C (`raw_text`):** Nội dung thông báo đã che số tài khoản dài (`***`)

* **Cột D (`event_id`):** Mã băm chống trùng

### 6.3. Lưu ý khi Truy vấn bằng ChatGPT / Gemini

* Ở giai đoạn MVP (vài trăm dòng giao dịch), ChatGPT và Gemini có thể đọc trực tiếp tab `Transactions` để tổng hợp và trả lời câu hỏi.

* **Giới hạn kỹ thuật cần lưu ý:** LLM không phải là công cụ tính toán số học tuyệt đối trên tập dữ liệu lớn. Khi tab `Transactions` tăng lên vài nghìn dòng sau nhiều tháng sử dụng, không nên kỳ vọng LLM tự cộng nhầm/trúng 100% bằng mắt đọc token; lúc đó sẽ bổ sung một vùng công thức `SUMIFS` / `QUERY` nhỏ ngay trên Sheet hoặc thêm endpoint `GET /api/summary` trên Vercel sau.

## 7. Trình tự Triển khai & Nghiệm thu Từng Bước (Gate-based Rollout)

Không quy định cứng thời gian theo phút; chỉ chuyển sang bước tiếp theo khi toàn bộ điều kiện kiểm thử của bước hiện tại đã đạt (Test xanh):

### Bước 1: Xây dựng & Kiểm thử Tầng Vercel + Google Sheets

* **Công việc:**

  1. Chuẩn bị Sheet thử riêng với header theo schema hiện có và cấp quyền tối thiểu cho Service Account.

  2. Viết `categories.ts`, `parsers/vcb.ts`, và `api/transaction.ts` (tích hợp kiểm tra `WEBHOOK_SECRET` và kiểm tra trùng `event_id` trên toàn bộ hai cột ID).

* **Điều kiện nghiệm thu Bước 1 (Gate 1):**

  * Unit test parser và API với Sheet giả lập đạt; kiểm tra ghi đúng cột/kiểu, lọc OTP và eventId.

  * Trên Sheet thử, gửi hai request giống nhau ➔ request đầu append đúng 9 cột, request sau trả `DUPLICATE_IGNORED`.

  * Dùng `curl` bắn 1 thông báo giả lập của VietinBank chưa có parser ➔ Tự động che số tài khoản và ghi đúng vào tab `Raw_Capture`.

### Bước 2: Xây dựng & Kiểm thử APK Cầu nối trên Điện thoại

* **Công việc:**

  1. Khởi tạo project Android Single-module với `MainActivity.kt`, `BankNotificationListener.kt`, `SensitiveFilter.kt`, `UploadWorker.kt` cùng `AndroidManifest.xml` và `build.gradle.kts`.

  2. Cài APK lên điện thoại, bật quyền Notification Access và tắt tối ưu hóa pin (Unrestricted).

* **Điều kiện nghiệm thu Bước 2 (Gate 2):**

  * Nút `[ Kiểm tra kết nối ]` hoàn tất dry-run mà không tạo dòng giao dịch trong Sheet.

  * Kiểm thử `SensitiveFilter`: Các thông báo chứa chữ `OTP`, `Mã xác thực`, `Đăng nhập thiết bị mới`, hoặc thông báo quảng cáo không có số tiền đều bị chặn hoàn toàn, không gửi request nào lên Vercel.

  * Bật Airplane Mode, phát notification thử, xác nhận đã vào SQLite; tắt chế độ máy bay ➔ worker gửi và cập nhật trạng thái. Phát lại cùng event không append dòng thứ hai dù ID nằm ngoài 1.000 dòng cuối.

### Bước 3: Vận hành Thực tế VCB & Mở rộng Parser VietinBank / BIDV

* **Công việc:**

  1. Sử dụng thực tế hàng ngày với Vietcombank (`com.VCB`).

  2. Khi phát sinh giao dịch thật trên VietinBank (`com.vietinbank.ipay`) và BIDV (`com.vnpay.bidv`), lấy các mẫu câu thực tế đã tự động hứng được trong tab `Raw_Capture` để viết tiếp `parsers/vietinbank.ts` và `parsers/bidv.ts`.

* **Điều kiện nghiệm thu Bước 3 (Gate 3):**

  * Sau khi có mẫu notification đã che và gate VCB đạt, kiểm tra parser BIDV/VietinBank trên Sheet thử. Chỉ promote môi trường đã nghiệm thu theo quyền deploy riêng; cập nhật parser không cần cài lại APK.

