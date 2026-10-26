# Finance v1.0 — audit và kế hoạch nghiệm thu

Ngày audit: 26/10/2026. Checkout: `pr1-checkout`; remote: `vi-phuong-158/finance`.
Base local: `f16c2ebda95b64985744153861981f0aebefd045`; origin/main: `0e10819bbc2c6b1d4e9823895140179540c342d0`.
Commit local bổ sung module lãi tháng đã có trước nhiệm vụ này, phải bảo toàn khi push nhánh.
Nhánh triển khai: `codex/finance-v1-completion`. Không merge PR hoàn thiện hoặc build release khi thiếu gate.

## Quyết định nghiệp vụ của chủ dự án trong phiên này

- Giữ **MONTHLY_25_V1**, kỳ 25–25 hiện hành. Yêu cầu tính lãi ngày trong bản giao việc được thay bằng lựa chọn này.
- Sổ Google Sheets chủ dự án cung cấp là nguồn nghiệp vụ. Đã đọc metadata, header và Cấu hình A1:B12 qua connector; không sửa nguồn, không sao chép số liệu cá nhân vào Git.
- Gốc thực tế giảm ngày nhận; gốc tính lãi giảm kỳ kế tiếp, trừ thu đúng 25. Không chia 365/366; không giả lịch sử hoặc gắn kỳ cho thu lãi cũ.

## Audit ban đầu (trước sửa code)

| Phần | Phân loại | Bằng chứng / giới hạn |
|---|---|---|
| UI foundation PR #6 | ALREADY_WORKING | GitHub xác nhận merged tại `0e10819`; head `1bdf37d`; CI run 40265249042 và main run 40287506385 success. Không merge lại |
| PR #1/#2/#4/#5 | ALREADY_WORKING | Đã merge: nền webhook/outbox, readiness, Keystore, Money Lover/read API. PR #3 vẫn open, không tự merge |
| Android | PARTIALLY_WORKING | Compose/Keystore/SQLite/Worker có thật; unit tests JVM không thực thi SQLite/WorkManager trên máy thật |
| Vercel | PARTIALLY_WORKING | HTTP adapter mỏng, config validation, auth timingSafeEqual, RAW writes. Hosted Preview của nhánh mới chưa có |
| Sheets ngân hàng | ALREADY_WORKING | Transactions 9 cột, Raw_Capture 4, MoneyLover_History 15; kiểm header fail-closed; scope đọc riêng |
| VCB parser | PARTIALLY_WORKING | Fixture tổng hợp + normalization/masking, từ chối envelope không chắc; cần nghiệm thu notification/thiết bị hiện hành |
| VietinBank / BIDV | NOT_IMPLEMENTED / RELEASE_BLOCKER | Parser trả null để capture đã mask. Chưa có fixture notification thực đã che dữ liệu; không đoán regex |
| eventId | ALREADY_WORKING | SHA256 đầy đủ bank:postedAt:normalizedText; vector Unicode dùng chung Kotlin/TS; giữ postedAt qua retry |
| Dedup | PARTIALLY_WORKING / RELEASE_BLOCKER | Replay tuần tự/timeout sau append được test. Read-ID rồi append còn race giữa request/serverless; Mutex Android không chứng minh exactly-once |
| Masking / secret | PARTIALLY_WORKING | Lọc 2 lớp, mask số dài, runtime Keystore, no-store, RAW. Chưa hoàn tất scan diff/APK mới hoặc kiểm log thực |
| Money Lover | ALREADY_WORKING | Import dry-run/idempotent/expectations/cleanup atomic, tests; không đọc .private-data hoặc import lại dữ liệu thật |
| Dashboard | BUG / RELEASE_BLOCKER | DashboardRepository trả preview khi thiếu secret/lỗi HTTP/lỗi mạng; còn thay danh sách rỗng bằng mẫu. UiDashboard.preview chứa số liệu hardcode. Net flow bị gắn nhãn tài chính/số dư |
| Xu hướng | UI_ONLY / BUG | SpendingChart tự tạo số mẫu theo chip, không đọc nguồn |
| Giao dịch | PARTIALLY_WORKING | Backend filter/cursor có thật. Android chỉ nhận recentTransactions của dashboard, chưa tải toàn sổ; detail/edit chưa có |
| Ghi chép | UI_ONLY | Nút lưu disabled, callback rỗng; chưa có canonical mutation/outbox manual |
| Danh mục | PARTIALLY_WORKING | Registry server có taxonomy; Android chưa dùng chung để sửa giao dịch |
| Báo cáo | UI_ONLY / PARTIALLY_WORKING | Summary dùng dashboard nhưng chip không đổi truy vấn, report tiles placeholder, biểu đồ mẫu |
| Tài khoản | BUG | Không có balance snapshot đáng tin; hero đang dùng net như tài sản. Cần ghi rõ chưa có dữ liệu số dư |
| Tiền lãi | PARTIALLY_WORKING | GET /api/interest readonly auth, engine tháng/từng đợt/FIFO/thu trước, UI đọc danh sách và timeline; chưa có tạo/sửa/thu tiền từ app |
| Migration sổ lãi | NOT_IMPLEMENTED / RELEASE_BLOCKER | Nguồn native đã có Khoản vay/Đợt giải ngân/Giao dịch/Cấu hình. Giao dịch A:F không có eventId; không được xóa/trộn receipt giống nhau. Chưa có importer canonical an toàn/backup mới |
| CI/build | PARTIALLY_WORKING | Baseline backend 92/92 + typecheck local; Android đang kiểm lại. CI cũ không bao phủ f16c2eb hay thay đổi mới |
| Production E2E/release | RELEASE_BLOCKER | Chưa có full 3 ngân hàng/manual/loans trên điện thoại; signing, backup, migration, Preview còn gate riêng |

## Thứ tự thực hiện

1. A: audit, kiểm baseline, ghi rõ blocker và giữ dữ liệu nguồn.
2. B: xác nhận PR #6 đã merge; branch mới từ main local có module lãi, không bỏ commit f16c2eb.
3. G/H: gỡ toàn bộ fallback/sample production và số dư suy diễn; truy vấn báo cáo thật theo chu kỳ, trend từ eligible transactions; lỗi/empty/retry rõ.
4. E: đọc endpoint phân trang cho toàn bộ lịch sử, lọc/tìm/chi tiết; bổ sung sửa có dấu vết khi có hợp đồng mutation.
5. C/D: giữ engine 25–25; chuẩn hóa stable loan/event IDs, kiểm dữ liệu/provenance và forward migration có backup/dry-run/rollback trước mutation. Không tự đổi sổ nguồn sang engine ngày.
6. F: canonical manual transaction + persistence/outbox/retry phải đi cùng auth/idempotency và migration tương thích. Không bật nút lưu khi chỉ có UI.
7. I: nhận fixture sanitized thực của CTG/BIDV rồi viết parser riêng; hiện chưa có nguồn để chứng minh đúng.
8. J: isolated Preview/Sheets test, Android thực, offline/restart/timeout/concurrency, backup/reconciliation; ghi SHA đã kiểm trước release.

## Release checklist

Chưa tick gate chỉ dựa vào thiết kế, compilation hay CI cũ.

- [ ] Android build/tests/policies của HEAD mới
- [ ] Backend tests/typecheck của HEAD mới
- [ ] CI jobs và Vercel Preview của đúng SHA
- [ ] VCB/VietinBank/BIDV fixture và thiết bị thật
- [ ] Dedup timeout/restart/concurrency trên Sheets thật của môi trường kiểm thử
- [ ] Manual persistence/offline, transaction edit/correction
- [ ] Dashboard/reports không mẫu, số liệu đối soát
- [ ] Loan engine/UI/mutations theo MONTHLY_25_V1
- [ ] Loan migration + provenance + backup/rollback/reconciliation
- [ ] Security review/scan APK/diff/logs, không secret hoặc dữ liệu cá nhân mới trong Git
- [ ] E2E ngân hàng, thủ công, khoản vay trên Android
- [ ] Owner review; signing guide; release APK từ đúng accepted SHA

Không có `FINANCE_V1_RELEASE_CANDIDATE_PASS` cho tới khi toàn bộ gate bắt buộc có bằng chứng.

## Tiến độ thực hiện sau audit

- Chủ dự án cung cấp hai envelope Thu/Chi BIDV và VietinBank; đã viết parser riêng và fixture thay thế toàn bộ dữ liệu. Không lưu notification nguồn vào repository. Unit/pipeline tests không thay thế thử trên thiết bị.
- Đã bỏ toàn bộ preview và dữ liệu biểu đồ mẫu trong production Android, lỗi/empty tách riêng. Hero dùng nhãn dòng tiền ròng; Accounts không suy diễn tài sản từ net.
- Lịch sử dùng API/cursor riêng, có type/bank/category/date/search/detail. Báo cáo dùng truy vấn kỳ thật và summary theo ngày/ngân hàng/nguồn, không tính từ trang giao dịch đang tải.
- Ghi tay có API validate/auth/idempotency, canonical Transactions V2, SQLite outbox và Worker. Script migration backup/dry-run/readback/rollback đã có; **chưa chạy apply** trên nguồn thật.
- Dedup đã thay bằng marker duy nhất + append literal trong cùng batch. Thử nghiệm **native Sheets tổng hợp riêng** 4 request cùng ID cho 1 dòng; 3 batch bị Google từ chối duplicate. Fixture 20 lời gọi song song và timeout cũng có test. Chưa đóng toàn bộ gate Android/Vercel/restart/offline/quota.
- Sổ lãi hiện có đã đối soát readonly 3 khoản, 4 đợt, 3 receipt; 27/27 chỉ số khớp engine MONTHLY_25_V1. Không re-import, không thay dữ liệu hoặc lưu số tiền nguồn vào Git. Bổ sung golden flow ẩn danh và chặn tổng tiền vượt safe integer.
- Chi tiết công nợ bổ sung tổng đã giải ngân, lãi lũy kế/đã thu, đáo hạn và timeline gộp theo ngày, format rate Việt Nam. Chưa có mutation khoản vay.
- ADB kiểm tra không có thiết bị kết nối. Thiết bị thật/offline/notification replay và UI 360/412dp chưa chạy.
- Mutation khoản vay và correction giao dịch chưa được triển khai. Chờ xác nhận phạm vi ghi có audit trước khi bổ sung endpoint, rồi mới kiểm tra migration/backup/rollback và runtime riêng. Không thay bằng thao tác ghi trực tiếp vào nguồn.

Vì còn mutation/audit migration, quyền môi trường và nghiệm thu thiết bị, chưa có phase nào được gắn PASS thay cho toàn bộ gate bắt buộc của phase đó.
