# Theo dõi tiền lãi trong App Thu Chi

Mở **Tài khoản → Theo dõi tiền lãi**. Module sử dụng backend Finance Bridge và font/theme có sẵn; không cần tạo Web App Apps Script hay một ứng dụng riêng. Sổ Google Sheets hiện tại tiếp tục là nguồn dữ liệu. Màn hình có tổng gốc, lãi đã chốt, lãi đến hạn còn thu, lãi kỳ hiện tại, thu trước, từng đợt giải ngân và lịch sử thu tiền.

## Quy tắc MONTHLY_25_V1

- Kỳ cố định `[25 tháng này, 25 tháng sau)`. Lãi kỳ = gốc đầu kỳ × lãi suất năm / 12. Chỉ cộng vào lãi đã chốt khi đến ngày 25 cuối kỳ; không chia 365, không chia số ngày tháng.
- Giải ngân giữa kỳ bắt đầu ngày 25 kế tiếp. Giải ngân đúng ngày 25 tham gia ngay kỳ mới.
- Trả gốc giữa kỳ làm giảm gốc thực tế ngay ngày nhận, giảm lãi từ kỳ tiếp theo. Thu gốc đúng ngày 25 giảm ngay lãi kỳ mới. Không sửa lại lãi kỳ trước.
- Khoản gộp giữ các đợt có ngày giải ngân, lãi suất và đáo hạn riêng; không tính toàn bộ tổng gốc từ một ngày. Thu gốc phân bổ FIFO theo ngày giải ngân rồi mã đợt. Mã đợt phải ổn định, duy nhất.
- Chỉ tính kỳ đủ tháng kết thúc không sau ngày đáo hạn từng đợt. Không mặc định tính phần tháng lẻ đầu/cuối. Thỏa thuận khác cần một thay đổi nghiệp vụ riêng.
- Không cộng lãi vào gốc. Tổng lãi của các đợt được cộng trước, rồi làm tròn đến đồng theo từng khoản (lũy kế và kỳ hiện tại độc lập).
- Tiền lãi đã thu bù lãi đã chốt trước. Phần dư là **lãi thu trước**, được trừ khỏi lãi dự kiến phải thu; không làm mất tiền thu trước bằng `MAX(0)`.
- **Đã nhận trong kỳ** chỉ mô tả ngày tiền thực nhận. Không suy rằng giao dịch đó trả lãi của kỳ hiện tại. Lịch sử thiếu mã kỳ không bị viết lại để phân bổ giả.
- Khi xem quá khứ, bỏ các giao dịch xảy ra sau ngày xem. Gốc chỉ xuất hiện sau ngày giải ngân.

## Hợp đồng Sheet

| Tab | Dữ liệu |
| --- | --- |
| `Khoản vay` | A:F thông tin khoản gộp, O ghi chú được giữ; G:N công thức tháng; P thu trong kỳ, Q thu trước, R dự kiến còn thu, S:T kỳ hiện tại |
| `Giao dịch` | A:F giữ nguyên, chỉ đọc. Loại hợp lệ: `Thu lãi`, `Thu gốc`. Không xóa, hợp nhất hay đổi ngày/số tiền các dòng lịch sử |
| `Đợt giải ngân` | A:F mã đợt, mã khoản, gốc, lãi suất năm dạng số thập phân, ngày giải ngân, đáo hạn. G:L công thức hỗ trợ, không nhập đè |
| `Cấu hình` | B2 ngày xem, B3 ngày mô phỏng (trống dùng hôm nay), B7:B8 kỳ hiện tại, B12 = `MONTHLY_25_V1` |

`lib/interest-sheet-formulas.ts` lưu mẫu công thức đã áp dụng để kiểm tra/bảo trì; không có thao tác ghi Sheet tự động trong API. Công thức chuẩn bị đến dòng 300 tại Khoản vay/Đợt giải ngân. Muốn vượt giới hạn cần mở rộng cả công thức và giới hạn API, tránh đọc thiếu nguồn.

## Kết nối và API

1. Cấu hình `INTEREST_SPREADSHEET_ID` ở backend bằng ID sổ lãi hiện có. Không thay `SPREADSHEET_ID` của sổ ngân hàng. Không đưa ID thật vào Git.
2. Service account hiện có cần quyền đọc sổ lãi. Cấp **Viewer** là đủ; đây là bước cấp quyền của chủ tài khoản. Không cần công khai Sheet hay Editor cho API mới.
3. `GET /api/interest` sử dụng `X-Webhook-Secret` hiện có, scope Sheets `spreadsheets.readonly`, range allowlist cố định. Không nhận ID/range hoặc secret từ query. `?asOf=YYYY-MM-DD` cho xem lại riêng trong API, không ghi Cấu hình Sheet.
4. Không truyền `asOf`: API dùng B2 và đối soát chín chỉ số mỗi khoản với Sheet. Sai công thức/sai schema/thiếu đợt/thu gốc vượt giải ngân → 503; không trả 0 giả hoặc dữ liệu mẫu.
5. Mọi phản hồi đặt `no-store`; Android không follow redirect để tránh chuyển secret sang nơi khác. Thiếu cấu hình, 401, 404, 503 hoặc lỗi mạng có trạng thái lỗi và Thử lại.

Module lãi không trộn gốc giải ngân, gốc thu hồi hay lãi chưa thu vào báo cáo thu chi Money Lover/ngân hàng. Việc nhập thu tiền vẫn qua sổ nguồn hiện tại. API của module này chỉ đọc, chưa bổ sung biểu mẫu ghi khoản vay/thu tiền trên Android.

## Xác minh và giới hạn nghiệm thu

- Backend: `npm test`, `npm run typecheck`. Fixture giả bao phủ ngày 24/25/26, năm nhuận, chuyển năm, hai đợt gộp, trả gốc, tất toán, thu trước, xem quá khứ, schema lỗi, auth, giới hạn nguồn, lệch công thức và cache.
- Android: `gradlew.bat --offline --no-daemon assembleDebug testDebugUnitTest verifyWebhookConfigurationPolicy validateReleaseWebhookConfig` với toolchain/cache đã có.
- Kiểm tra sổ thật qua connector và phát lại snapshot qua API local có port chỉ đọc: kiểm tra công thức khớp engine, không lỗi công thức và lịch sử A:F không thay đổi. Snapshot thật chỉ ở thư mục công việc ngoài Git; không lưu số liệu cá nhân làm fixture.
- Build/unit test không thay thế nghiệm thu trên điện thoại. Chưa deploy API, chưa cấu hình quyền backend/Sheet và chưa nghiệm thu Android → chưa coi tích hợp online hoàn tất.
