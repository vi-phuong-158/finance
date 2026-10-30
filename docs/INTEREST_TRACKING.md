# Theo dõi tiền lãi trong App Thu Chi

Mở **Tài khoản → Theo dõi tiền lãi**. Module sử dụng backend Finance Bridge và font/theme có sẵn; không cần tạo Web App Apps Script hay một ứng dụng riêng. Sổ Google Sheets hiện tại tiếp tục là nguồn dữ liệu. Màn hình có tổng gốc, lãi đã chốt, lãi đến hạn còn thu, lãi kỳ hiện tại, thu trước, từng đợt giải ngân và lịch sử thu tiền.

## Quy tắc DAILY_ACT365_25_V1 (thay MONTHLY_25_V1 từ 29/10/2026)

- Lãi thông thường = tổng (dư nợ cuối ngày × số ngày thực tế giữ dư nợ × lãi suất năm) / 365, kể cả năm nhuận. Giải ngân tính ngay ngày nhận nợ; thu gốc giảm lãi từ ngày thực nhận.
- Kỳ chốt vẫn là `[25 tháng này, 25 tháng sau)`. Lãi đã chốt tính đến hết ngày trước ngày 25 đầu kỳ hiện tại. Lãi kỳ hiện tại là dự kiến cho cả kỳ, gồm lịch sử đã biết đến ngày xem và giả định dư nợ/lãi suất không đổi trong phần còn lại; không đọc trước giao dịch hay giải ngân tương lai.
- Tính lại toàn bộ lịch sử từ ngày giải ngân bằng quy tắc mới. Không sửa ngày, loại, số tiền, ghi chú hoặc xóa dòng thu tiền; bản sao trước chuyển đổi giữ kết quả quy tắc cũ để đối chiếu.
- Khoản gộp giữ ngày giải ngân, lãi suất và đáo hạn riêng từng đợt. Thu gốc phân bổ FIFO theo ngày giải ngân rồi mã đợt, kể cả nhiều dòng cùng ngày và dòng nhập không theo thứ tự thời gian.
- Tính cả phần kỳ lẻ đầu/cuối. Lãi thông thường dừng trước ngày đáo hạn từng đợt; chưa có điều khoản lãi quá hạn/phí trả trước nên không tự áp hệ số phạt. Đây là mô hình Actual/365 tham khảo phương pháp BIDV, không mô phỏng toàn bộ sản phẩm ngân hàng.
- Không nhập lãi vào gốc. Cộng lãi các đợt trước khi làm tròn đến đồng theo từng khoản. Lãi đã thu bù lãi đã chốt; phần dư là thu trước. Đã nhận trong kỳ thể hiện ngày nhận tiền, không tự phân bổ lịch sử vào kỳ giả.
- H/`completedPeriods` giữ thông tin số mốc chốt ngày 25 tối đa của các đợt, không dùng để nhân tiền lãi.

Nguồn phương pháp: Điều 3, điều khoản cấp hạn mức thấu chi công bố trên bidv.com.vn: dư nợ cuối ngày × số ngày thực tế × lãi suất năm / 365. Ngày nhận nợ có tính lãi; ngày thanh toán hết gốc không còn dư nợ chịu lãi.

## Triển khai đồng bộ

Backend, APK và Sheet có mã quy tắc mới. API cũ hoặc APK cũ từ chối mã mới theo thiết kế fail-closed. Phải triển khai backend mới và APK mới cùng đợt chuyển công thức/rule Sheet; không đổi riêng sổ Production khi backend còn MONTHLY_25_V1. Chỉ deploy khi chủ dự án cho phép rõ ràng. Snapshot thật nằm ngoài Git.

## Hợp đồng Sheet

| Tab | Dữ liệu |
| --- | --- |
| `Khoản vay` | A:F thông tin khoản gộp, O ghi chú được giữ; G:N công thức ngày; P thu trong kỳ, Q thu trước, R dự kiến còn thu, S:T kỳ hiện tại |
| `Giao dịch` | A:F giữ nguyên, chỉ đọc. Loại hợp lệ: `Thu lãi`, `Thu gốc`. Không xóa, hợp nhất hay đổi ngày/số tiền các dòng lịch sử |
| `Đợt giải ngân` | A:F mã đợt, mã khoản, gốc, lãi suất năm dạng số thập phân, ngày giải ngân, đáo hạn. G:L công thức hỗ trợ, không nhập đè |
| `Cấu hình` | B2 ngày xem, B3 ngày mô phỏng (trống dùng hôm nay), B7:B8 kỳ hiện tại, B12 = `DAILY_ACT365_25_V1` |

`lib/interest-sheet-formulas.ts` lưu mẫu công thức đã áp dụng để kiểm tra/bảo trì; không có thao tác ghi Sheet tự động trong API. Công thức chuẩn bị đến dòng 300 tại Khoản vay/Đợt giải ngân. Muốn vượt giới hạn cần mở rộng cả công thức và giới hạn API, tránh đọc thiếu nguồn.

## Kết nối và API

1. Cấu hình `INTEREST_SPREADSHEET_ID` ở backend bằng ID sổ lãi hiện có. Không thay `SPREADSHEET_ID` của sổ ngân hàng. Không đưa ID thật vào Git.
2. Service account hiện có cần quyền đọc sổ lãi. Cấp **Viewer** là đủ; đây là bước cấp quyền của chủ tài khoản. Không cần công khai Sheet hay Editor cho API mới.
3. `GET /api/interest` sử dụng `X-Webhook-Secret` hiện có, scope Sheets `spreadsheets.readonly`, range allowlist cố định. Không nhận ID/range hoặc secret từ query. `?asOf=YYYY-MM-DD` cho xem lại riêng trong API, không ghi Cấu hình Sheet.
4. Không truyền `asOf`: API dùng B2 và đối soát chín chỉ số mỗi khoản với Sheet. Sai công thức/sai schema/thiếu đợt/thu gốc vượt giải ngân → 503; không trả 0 giả hoặc dữ liệu mẫu.
5. Mọi phản hồi đặt `no-store`; Android không follow redirect để tránh chuyển secret sang nơi khác. Thiếu cấu hình, 401, 404, 503 hoặc lỗi mạng có trạng thái lỗi và Thử lại.

Module lãi không trộn gốc giải ngân, gốc thu hồi hay lãi chưa thu vào báo cáo thu chi Money Lover/ngân hàng. Biểu mẫu khoản vay/giải ngân/thu gốc/thu lãi trên Android chỉ mở khi LOAN_WRITES_ENABLED được owner bật; thay đổi này không bật cờ ghi. Nhập trực tiếp sổ nguồn vẫn được hỗ trợ.

## Xác minh và giới hạn nghiệm thu

- Backend: `npm test`, `npm run typecheck`. Fixture giả bao phủ ngày 24/25/26, năm nhuận, chuyển năm, hai đợt gộp, trả gốc, tất toán, thu trước, xem quá khứ, schema lỗi, auth, giới hạn nguồn, lệch công thức và cache.
- Android: `gradlew.bat --offline --no-daemon assembleDebug testDebugUnitTest verifyWebhookConfigurationPolicy validateReleaseWebhookConfig` với toolchain/cache đã có.
- Kiểm tra sổ thật qua connector và phát lại snapshot qua API local có port chỉ đọc: kiểm tra công thức khớp engine, không lỗi công thức và lịch sử A:F không thay đổi. Snapshot thật chỉ ở thư mục công việc ngoài Git; không lưu số liệu cá nhân làm fixture.
- Build/unit test không thay thế nghiệm thu trên điện thoại. Bản Actual/365 cần được deploy và chuyển rule Sheet đồng bộ, sau đó cập nhật APK và nghiệm thu trên điện thoại; build không thay thế gate đó.
