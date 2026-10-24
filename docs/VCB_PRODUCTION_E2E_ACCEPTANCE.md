# VCB Production E2E acceptance status

Assessment date: 2026-10-25 (Asia/Saigon)

## Verdict

`VCB_PRODUCTION_E2E_BLOCKED_PRODUCTION_CREDENTIALS`

Intermediate milestone verdicts:
- `VCB_RUNTIME_CREDENTIAL_PROVISIONING_PASS`: Runtime credential storage abstraction (`WebhookCredentialStore`) implemented with Android Keystore AES/GCM encryption, Compose configuration UI (4 states), and WorkManager runtime retrieval.
- `VCB_PUBLIC_SAFE_APK_PASS`: APK build policy updated, `WEBHOOK_SECRET` completely removed from `BuildConfig` and APK DEX files, generic public-safe APK workflow dispatched and verified on `main`.

PR #3 remains **Draft**. Production E2E acceptance is blocked on physical device testing and real VCB transactions with owner production credentials.

## Architecture Milestone: Public Safe APK + Runtime Private Config (PR #4 Merged)

PR #4 (`codex/runtime-webhook-credential`) merged into `main` at commit `af16cdb6a608cafaa003240cb0308fd0cd450e86`:
- **Secret Removal**: `WEBHOOK_SECRET` removed from `BuildConfig`, Gradle build args, `local.properties`, CI logs, and workflow build steps. `WEBHOOK_URL` defaults to production HTTPS endpoint.
- **Android Keystore**: `WebhookCredentialStore` generates a 256-bit AES master key in `AndroidKeyStore`, encrypts secret using `AES/GCM/NoPadding` (128-bit tag, 12-byte IV), and persists only ciphertext and IV in private app storage.
- **Configuration UI**: Added `Cấu hình kết nối` section in Compose with server URL display, masked password field, and 4 states: *Chưa cấu hình*, *Đã cấu hình*, *Kết nối thành công*, *Cấu hình không hợp lệ*.
- **WorkManager**: `UploadWorker` and `ConnectionCheckWorker` retrieve secret at runtime. If unconfigured or unauthorized, outbox entries transition to `BLOCKED` without payload loss.
- **Workflow Dispatch**: Workflow `.github/workflows/build-pilot-apk.yml` deployed to default branch `main`. Dispatch run `40118396622` completed successfully; artifact `finance-bridge-vcb-pilot-apk` generated.
- **Artifact Security Scan**: Scanned 4 DEX files in `app-debug.apk`; verified 0 occurrences of `WEBHOOK_SECRET`.

## Vercel Project & Deployment Status

- Created `finance` in team `vi-phuong-158s-projects`, ID `prj_jSMQ6jeW8GKEVOPDeeerI9ZKOiVj`.
- Assigned domain `finance-rust-nine-16.vercel.app`.
- Automatic git deployments paused via Ignored Build Step `exit 0` until production credentials and Sheet bootstrap are completed by the owner.

## Production Environment and Google Sheets (Remaining Gates)

The 4 private production credentials must be entered directly by the owner into Vercel and private `.env.local`:

| Name | Supply through a secure environment |
| --- | --- |
| `SPREADSHEET_ID` | ID of the owner's private production Sheet |
| `WEBHOOK_SECRET` | Private random URL-safe value (32–128 chars); matched at runtime in app |
| `GOOGLE_CLIENT_EMAIL` | Service account email shared onto that Sheet |
| `GOOGLE_PRIVATE_KEY` | Service account RSA key; store as Secret/Sensitive |

### Next Operational Steps for Owner:
1. Create private Google Sheet **Finance Bridge Pilot**.
2. Create Google Cloud service account, enable Google Sheets API, grant service account **Editor** permission on the Sheet.
3. Configure the 4 variables in Vercel Production Environment and local `vercel/.env.local`.
4. Run bootstrap:
   ```bash
   cd vercel
   npm run sheets:bootstrap
   npm run sheets:bootstrap -- --apply
   npm run sheets:bootstrap # Must report ALREADY_READY
   ```
5. Deploy `main` to Vercel, verify `/api/health` HTTP 200, and run `npm run check:deployment` (dry-run, 0 writes).
6. Download artifact `finance-bridge-vcb-pilot-apk` from Actions Run 40118396622 or build locally.
7. Install APK on Android phone, enter `WEBHOOK_SECRET` in app, tap **Lưu và kiểm tra**, confirm **Kết nối thành công**, grant Notification Access, and perform small test transaction.

## CI & Regression Evidence

- Main commit `af16cdb`: All CI checks green ([CI 40118192775](https://github.com/vi-phuong-158/finance/actions/runs/40118192775)).
- Vercel: 58/58 unit tests passing, `tsc --noEmit` passing.
- Android: 30/30 unit tests passing (covering credential store, keystore round-trip, outbox recovery, policy checks).
- Policy verification: `verifyWebhookConfigurationPolicy` (7/7 checks) and `validateReleaseWebhookConfig` passing.
- Workflow dispatch: Run [40118396622](https://github.com/vi-phuong-158/finance/actions/runs/40118396622) succeeded on `main`, artifact `finance-bridge-vcb-pilot-apk` verified clean.

## MANUAL_DEVICE_ACCEPTANCE (Remaining Checklist)

- [ ] Tải APK pilot từ Actions run 40118396622 (artifact: `finance-bridge-vcb-pilot-apk`)
- [ ] Cài APK lên điện thoại Android
- [ ] Mở app Đồng Bộ Thu Chi
- [ ] Nhập Mã kết nối (khớp với Vercel `WEBHOOK_SECRET`)
- [ ] Bấm **Lưu và kiểm tra**, xác nhận trạng thái: **Kết nối thành công**
- [ ] Cấp Quyền đọc thông báo (Notification Access)
- [ ] Đặt Pin không hạn chế (Unrestricted)
- [ ] Thực hiện 01 giao dịch CHI nhỏ do chủ tài khoản tự chọn
- [ ] Kiểm tra Transactions: CHI, tiền, số dư, thời gian đúng
- [ ] Thực hiện 01 giao dịch THU nhỏ
- [ ] Kiểm tra Transactions: THU, tiền, số dư, thời gian đúng
- [ ] Kiểm tra không duplicate trên tổng hai tab
- [ ] Thử mất mạng và gửi lại giao dịch lỗi thành công
