# Nghiệm thu VCB trên điện thoại thật

Mọi ô để trống tới khi chủ dự án xác minh. Không commit notification thật, số tài khoản/số dư, domain riêng, secret hoặc DB. Lưu bằng chứng chi tiết trong bản riêng.

- Ngày / phiên bản Android / hãng máy: __________
- Commit APK và backend: __________
- Loại APK / chữ ký dùng cập nhật: __________
- Người kiểm thử: __________

## Chuẩn bị

- [ ] Sheets API bật, service account được chia sẻ đúng Sheet riêng
- [ ] Bootstrap ALREADY_READY, dữ liệu/tab cũ giữ nguyên
- [ ] APK cài thành công
- [ ] Notification Access ON
- [ ] Battery unrestricted
- [ ] VCB được phép hiển thị notification
- [ ] Health HTTP 200 / environmentReady true
- [ ] Dry-run VALIDATED_TRANSACTION từ máy tính và trong app
- [ ] Dry-run không tăng số dòng Sheet

## Giao dịch

- [ ] VCB CHI ghi đúng
- [ ] VCB THU ghi đúng
- [ ] amount đúng, là số dương
- [ ] balance đúng, kể cả 0 nếu có
- [ ] time đúng
- [ ] category hợp lý, chuyển nội bộ đã đối soát
- [ ] counterparty/description có dấu đúng
- [ ] Không lộ full account/card identifier dài
- [ ] Format chưa hiểu vào Raw_Capture, không đoán giao dịch
- [ ] OTP/bảo mật/quảng cáo không ghi vào hai tab

## Mất mạng và retry

- [ ] Xác nhận notification đã đến và có trong hàng đợi trước khi kiểm tra mất mạng
- [ ] Network failure giữ bản ghi chờ
- [ ] Mở lại app, metadata sự kiện vẫn còn
- [ ] Có mạng/endpoint trở lại tự gửi và chuyển terminal
- [ ] Sau terminal raw/title cục bộ đã clear, kiểm tra riêng không xuất DB vào repo
- [ ] Retry cùng eventId không tạo duplicate trên tổng hai tab
- [ ] Hai sự kiện khác postedAt vẫn là hai giao dịch

## BLOCKED recovery

- [ ] URL sai: BLOCKED, giữ payload
- [ ] Secret sai: BLOCKED, không retry vô hạn
- [ ] Server config lỗi: BLOCKED, giữ payload
- [ ] Sheet schema sai: BLOCKED, không ghi lệch cột
- [ ] Quyền Sheet/ID sai: mã lỗi rõ ràng
- [ ] Sửa cấu hình/cài cập nhật cùng chữ ký nếu cần, dry-run đạt, bấm Gửi lại thành công
- [ ] Không gỡ app/Clear data khi còn hàng đợi

## Đối soát cuối

- [ ] Số lượng/tiền/chiều/giờ khớp nguồn VCB trong cửa sổ thử
- [ ] Không có giao dịch đã tiếp nhận bị thiếu không giải thích được
- [ ] Raw_Capture và mọi bản ghi BLOCKED được đối soát
- [ ] Đã ghi nhận giới hạn đồng thời; pilot chỉ một thiết bị

Kết quả: **CHƯA NGHIỆM THU / ĐẠT / CHƯA ĐẠT** (chủ dự án tự chọn sau khi kiểm chứng).

Ghi chú/mã lỗi đã bỏ dữ liệu nhạy cảm: __________
