# Schema, ghi tay và chống trùng v1

## Transactions V1 → V2

V1 giữ nguyên A:I: `time,bank,type,amount,balance,counterparty,description,category,event_id`.
V2 chỉ bổ sung J:K: `source,account`. Không tạo sổ tài chính thứ hai cho giao dịch thủ công.

- Dòng ngân hàng cũ: J:K trống, đọc thành `BANK_NOTIFICATION`; không viết lại lịch sử.
- Dòng ghi tay: J=`MANUAL`; B là VCB/VIETINBANK/BIDV/CASH; K là tài khoản hiển thị. Không suy diễn số dư.
- Money Lover giữ tab lịch sử riêng đã có; read model hợp nhất với Transactions.
- GET hiện hữu nhận cả header V1 và V2. POST notification vẫn ghi 9 trường. POST ghi tay yêu cầu đúng V2; chưa migrate trả `MANUAL_SCHEMA_MIGRATION_REQUIRED` và Android giữ dữ liệu chờ gửi lại.
- Nguồn lãi và gốc công nợ tiếp tục độc lập với báo cáo thu chi, theo MONTHLY_25_V1.

## Migration có backup

Không chạy các lệnh ghi lên production chỉ vì đọc tài liệu này. Trước apply: chủ dự án duyệt, dừng các editor khác, kiểm quyền riêng tư và dành một khoảng bảo trì.

```powershell
# vercel/, cấu hình riêng ở .env.local; mặc định chỉ đọc
npm run transactions:migrate

# Chỉ sau khi được cho phép ghi rõ ràng. Backup nằm ngoài repository, file phải chưa tồn tại.
npm run transactions:migrate -- --apply --backup D:\finance-private\transactions-before-v2.json
```

Script tạo backup bằng exclusive create trước, đọc lại fingerprint để chặn nguồn đổi, rồi thêm cột nếu cần và cập nhật hai header trong một `batchUpdate`. Không sửa A:I, không xóa dòng. Đọc lại đối soát A:I trước khi báo MIGRATED. Nếu đối soát lỗi phải dừng nhận ghi mới và kiểm tra backup; không tự phục hồi đè dữ liệu.

Rollback khi chưa có dữ liệu V2:

```powershell
npm run transactions:migrate -- --rollback
npm run transactions:migrate -- --rollback --apply --backup D:\finance-private\transactions-before-rollback.json
```

Rollback chỉ xóa giá trị header J:K, không xóa cột hoặc dữ liệu A:I. Nếu đã có dữ liệu source/account thì từ chối rollback để bảo toàn giao dịch ghi tay. Re-import/backup tài chính không được đưa vào Git.

## API và outbox ghi tay

`POST /api/manual-transaction`, JSON: `requestId` UUID v4, `dateTime` dạng `YYYY-MM-DDTHH:mm` theo giờ Việt Nam, `type` THU/CHI, `amount` nguyên dương tối đa 10^12, `category` trong registry phù hợp loại, `account` VCB/VIETINBANK/BIDV/CASH, `note` tối đa 1.024 byte. Auth `X-Webhook-Secret`; response không chứa nội dung tài chính. Ghi giá trị literal, che identifier trong note, không cho công thức đầu vào thực thi.

ID chuẩn = SHA256(`MANUAL:` + requestId). Lưu lại cùng ID và payload khi retry. Cùng ID/nội dung trả DUPLICATE_IGNORED; nội dung thay đổi trả 409, giữ payload để người dùng xử lý.

Android `ManualOutbox` lưu trước khi xếp WorkManager, không chờ mạng mới lưu. Trạng thái PENDING/SYNCING/SYNCED/BLOCKED. Chỉ xóa payload sau status thành công đã nhận diện. 429/5xx/network/response không hợp lệ giữ pending; credential/schema/conflict giữ blocked. App launch lên lịch drain; thao tác gửi lại đổi blocked → pending. Work chain APPEND_OR_REPLACE tránh bỏ sót lần lưu xảy ra khi drain trước đang kết thúc. Mã kết nối chỉ lấy từ Keystore.

## Dedup ở Google Sheets

Luồng mới giữ kiểm tra ID cũ để tương thích lịch sử. Mỗi lần ghi mới tạo named range có ID/tên từ đầy đủ eventId và append các cell literal trong **cùng một batchUpdate**. Marker tồn tại lâu dài; không dùng khóa RAM và không xóa marker theo TTL.

Google quy định batch atomic và AddNamedRange từ chối ID đã có:
- https://developers.google.com/workspace/sheets/api/guides/batch
- https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets/request#AddNamedRangeRequest

Nếu batch lỗi/timeout, chỉ xác nhận duplicate khi đọc lại thấy dòng tài chính. Với ghi tay còn so khớp nội dung đầy đủ. Marker không có dòng tương ứng không được coi là thành công. Không tự xóa marker hoặc xóa dữ liệu để chữa lỗi. Phải theo dõi quota/hiệu năng khi lịch sử và số marker lớn; thử nghiệm 4 request không chứng minh tải dài hạn.

Kiểm thử native 26/10/2026 trên **bảng tổng hợp riêng**: gửi 4 batch cùng eventId mới; 1 thành công, 3 bị Google từ chối duplicate; đọc lại đúng 1 dòng cho ID đó. Chuỗi bắt đầu `=` vẫn là stringValue, không thành formula. Đây là kiểm tra API Sheets qua connector, chưa phải E2E Android → Vercel → Sheets.

## Báo cáo và dữ liệu rỗng

Dashboard API thêm `dailySummary`, `bankSummary`, `sourceSummary`; mọi chiều dùng cùng eligible totals, loại chuyển nội bộ và excluded history. Android không dùng mẫu khi lỗi hoặc rỗng. Các chu kỳ hôm nay/7 ngày/tháng này/tháng trước/năm nay/tùy chọn gửi `from,to` thật. Net được ghi rõ là dòng tiền ròng. Accounts ghi chưa có dữ liệu số dư.

Ngân hàng hiện parse envelope VCB và hai envelope BIDV/VietinBank được chủ dự án cung cấp ngày 26/10. Fixture đổi toàn bộ thông tin cá nhân, tham chiếu, tiền và thời điểm. Trường không chắc như counterparty từ nội dung chuyển khoản để trống. Envelope lạ vẫn chuyển Raw_Capture đã che dữ liệu; không đoán và không backfill nguồn thật tự động.

## Ghi khoản vay và sửa giao dịch có audit

Hai capability mặc định tắt: `LOAN_WRITES_ENABLED=0`, `TRANSACTION_EDITS_ENABLED=0`. Quyền được giao ở giai đoạn này là mã nguồn/test/Draft PR; chưa bật các cờ ở production.

- `POST /api/loan-event`: CREATE_LOAN, DISBURSEMENT, PRINCIPAL_PAYMENT, INTEREST_PAYMENT, UPDATE_INFO. UUID v4 giữ nguyên qua retry, auth hiện hữu, tiền/ngày/rate được validate. Không nhận giao dịch tương lai. UPDATE_INFO chỉ sửa tên/ghi chú; không sửa lãi suất hoặc lịch sử tài chính cũ.
- Loan source giữ `Khoản vay`, `Đợt giải ngân`, `Giao dịch`, `Cấu hình`; không tạo sổ tài chính song song. Writer yêu cầu hai tab Khoản vay/Đợt giải ngân có 300 dòng, cùng giới hạn template đang dùng. Thu gốc kiểm toàn bộ chronology, từ chối trả vượt gốc. Audit `Finance_App_Audit` giữ request/loan/operation/hash/time/payload/thông tin trước sửa.
- Mỗi batch loan dùng marker command, revision khoản vay và slot dòng nếu tạo mới. Tất cả audit, dữ liệu, công thức nằm trong cùng batch. Hai khoản thu kiểm trên cùng số dư chỉ có một batch tiêu thụ revision. Sau retry phải đọc lại; không lấy mutex trong RAM làm bằng chứng.
- Source/audit được đọc với kiểm audit trước/sau để tránh snapshot lệch khi app ghi đồng thời. Khi bật ghi, không sửa source trực tiếp song song bằng tay hoặc qua công cụ khác; marker không khóa các editor ngoài ứng dụng.
- Formula adapter đọc locale thật: hỗ trợ English `en_*` và `vi_VN`; dấu phân cách được đổi bên ngoài quoted strings. Locale khác bị chặn, không tự đoán. Native test đã phát hiện lỗi locale và được sửa trước acceptance.
- `POST /api/transaction-edit`: category, note, type THU/CHI, excluded, transactionId, baseRevision, requestId. Không sửa số tiền hoặc hàng nguồn. Append `Transaction_Edits` với trạng thái mới và base revision; read model phát lại chain, dữ liệu/chain sai thì 503. Marker revision chặn sửa đè; revision cũ trả 409 và giữ yêu cầu để xử lý.
- GET transactions thêm categories/editingEnabled và revision trên từng record; GET interest thêm writesEnabled và ghi chú đã mask. Android dùng capability để mở biểu mẫu; backend cũ vẫn chỉ đọc, không giả đã lưu.
- SQLite queue nâng version 1→2 bằng ADD COLUMN endpoint, giữ hàng đợi cũ. Cùng queue/Worker xử lý manual-transaction, loan-event, transaction-edit; không đổi tên Worker đã lưu trong WorkManager. Chỉ status xác nhận mới xóa payload. Xung đột/schema/cờ tắt/capacity giữ BLOCKED; retry sau timeout giữ cùng UUID.

Chuẩn bị audit, **chỉ xem kế hoạch**:

```powershell
npm run audit:prepare -- --scope transactions
npm run audit:prepare -- --scope loans
```

Chỉ sau owner approval cho đích ghi: thêm `--apply --backup D:\finance-private\unique-full-backup.json`. Script lưu full Sheets cell/formula/format/metadata JSON ngoài Git trước, kiểm lại trạng thái audit rồi thêm đúng tab/header cần thiết trong một batch. Scope loans dùng INTEREST_SPREADSHEET_ID, transactions dùng SPREADSHEET_ID. Không tự bật env flag. File backup phải mới và được bảo vệ quyền truy cập.

Rollback thêm `--rollback`, vẫn dry-run mặc định; apply vẫn cần backup mới. Chỉ xóa audit tab nếu không có event hoặc marker. Đã có audit thì từ chối rollback, giữ cờ ghi tắt và phục hồi/đối soát trên bản sao; không xóa lịch sử để hạ schema.

## Mapping/import nguồn công nợ hiện có

Nguồn chủ dự án cung cấp là **native Google Sheets đã có lịch sử**, được dùng trực tiếp. Không append lại cùng dữ liệu vào một Loans/LoanEvents vật lý khác.

`npm run loans:import` tạo dry-run canonical mapping và chỉ in counts/status; **không có --apply**. `--output <private-manifest.json>` và `--previous <private-manifest.json>` chỉ nhận file ngoài repository, tạo file mới bằng exclusive create.

| Canonical domain | Native source | Identity/provenance |
|---|---|---|
| Loans | Khoản vay + từng đợt | Giữ mã khoản; không bịa rate chung khi các đợt khác rate |
| LoanEvents DISBURSEMENT | Đợt giải ngân A:F | Stable mã đợt, source key, row và input hash |
| LoanEvents PRINCIPAL_PAYMENT/INTEREST_PAYMENT | Giao dịch A:F | Legacy row identity + source key + input hash; hai receipt giống nhau ở hai dòng vẫn là hai event |

Manifest nhận diện re-run, chặn dòng cũ đổi/xóa hoặc đổi nguồn; append hợp lệ giữ IDs cũ. Thiếu đợt hoặc dữ liệu không đủ tái dựng thì chặn toàn bộ, không đoán lịch sử/OPENING_BALANCE. Manifest không thay thế backup hay biên bản import thật. Không đưa manifest, borrower hoặc số tiền nguồn vào Git.

Gia hạn/đổi rate/xóa event cần event có hiệu lực theo ngày hoặc reversal và quy tắc nghiệp vụ riêng; không dùng sửa metadata để hồi tố lãi. ADJUSTMENT tài chính chưa có semantics được chốt nên bị từ chối thay vì âm thầm đổi gốc/lãi.

## Gợi ý danh mục từ lịch sử (Category Memory)

Thiết kế đầy đủ: `docs/category-memory/DESIGN.md`. Hai cờ mặc định tắt: `CATEGORY_MEMORY_ENABLED=0` (GET `/api/category-memory`, `memoryKey` trên GET transactions) và `CATEGORY_MEMORY_WRITES_ENABLED=0` (POST lệnh SET/DELETE/RESET_ALL). Ghi nhớ được suy ra từ giao dịch ghi tay và `Transaction_Edits`; không có bộ đếm riêng, không auto-submit. Tab `Category_Memory` (6 cột, append-only) chỉ cần cho lệnh quản lý: chuẩn bị bằng `npm run audit:prepare -- --scope category-memory` (dry-run), apply cần `--apply --backup <ngoài repo>` do chủ dự án chạy. Tắt cờ là rollback hoàn toàn.
