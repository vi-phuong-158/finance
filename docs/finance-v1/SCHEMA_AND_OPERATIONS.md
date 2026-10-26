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
