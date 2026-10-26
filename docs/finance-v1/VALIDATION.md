# Finance v1 — bằng chứng kiểm tra ngày 26/10/2026

## Kết quả local

| Kiểm tra | Kết quả | Phạm vi |
|---|---|---|
| Backend `npm test` | 107/107, 0 fail/skip | parser/pipeline, manual/idempotency, migration, reports, monthly interest, auth/masking |
| `npm run typecheck` | exit 0 | TypeScript backend/scripts/tests |
| Android `assembleDebug` | build thành công | Debug; không build release |
| Android `testDebugUnitTest` | 41/41, 0 fail/error/skip | JVM; không có SQLite/WorkManager thật |
| Webhook policy | 7/7 | URL/configuration policy, validateReleaseWebhookConfig; không tương đương build release |
| Source scan | 0 match | Added source: PEM private-key header và literal webhook secret; không phải full-history/log security audit |
| APK / BuildConfig | Không có symbol WEBHOOK_SECRET | Không nhúng build-time secret; secret nhập runtime qua Keystore |
| Diff whitespace | sạch | `git diff --check` |

## Kiểm tra Google Sheets

- Bảng test riêng chỉ chứa dữ liệu tổng hợp; không clone dữ liệu tài chính thật. Cùng event ID mới, 4 batch gửi đồng thời: 1 thành công, 3 bị Google từ chối named range trùng; đọc lại đúng 1 dòng cho ID đó. Header và dòng trước được giữ. Giá trị bắt đầu `=` giữ stringValue, không có formulaValue.
- Request native được tạo trực tiếp từ `atomicAppendRequests` trong source; thử qua connector Sheets, không qua Vercel hoặc thiết bị. Test giả lập bổ sung 20 caller và lost-response.
- Sổ lãi nguồn chủ dự án cung cấp được đọc qua connector ở chế độ readonly. Engine tháng đã đối chiếu 3 khoản, 4 đợt, 3 receipt, 27 chỉ số khớp. Snapshot xử lý tạm trong bộ nhớ; không lưu dữ liệu tài chính/notification thật vào repo, fixture, log hoặc báo cáo này. Golden fixture là dữ liệu tổng hợp thay thế, không chứng minh import dữ liệu thật.

## Gate chưa thực hiện

- Thiết bị: `adb devices` không có máy kết nối. Chưa kiểm navigation/form/SQLite/Worker, restart/reboot, offline/retry hoặc screenshot 360/412dp trên Android thật.
- Chưa chạy migration `--apply`, chưa backup/reconcile production, chưa ghi transaction test lên sổ thật, chưa deploy production.
- CI/Preview: xem checks của đúng HEAD PR, không dùng run của PR #6 cho code mới. Build READY chỉ là deploy state, chưa phải online acceptance.
- Loan mutation và transaction correction chưa có; không báo hoàn thành các phase đó. Loan import/cutover/audit migration còn chờ triển khai và đối soát.
- Chưa có ba luồng full E2E v1 (notification, ghi tay, loan event). Chưa đóng security review/logs/quota dài hạn hoặc signing gate.

**Quyết định:** chưa phát hành APK v1.0. Giữ Draft PR, không merge thay chủ dự án.
