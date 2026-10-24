# VCB Production E2E acceptance status

Assessment date: 2026-10-25 (Asia/Saigon)

## Verdict

`VCB_PRODUCTION_E2E_BLOCKED_PRODUCTION_CREDENTIALS`

PR #3 remains **Draft**. No production deployment or real-device acceptance is claimed. CI cannot prove the notification → Android → SQLite → WorkManager → Vercel production → Sheets flow.

## Vercel project

Created `finance` in team `vi-phuong-158s-projects`, ID `prj_jSMQ6jeW8GKEVOPDeeerI9ZKOiVj`. Dashboard verification confirms Git repository `vi-phuong-158/finance`, production branch `main`, Root Directory `vercel`, Framework Other, Node `24.x`, Install Command `npm ci`, empty Build Command and no frontend Output Directory override. Vercel assigned domain `finance-rust-nine-16.vercel.app`; this is not evidence of a deployed/healthy production API. No ready production deployment exists.

Git integration automatically started first deployment `dpl_BF2hwiVARyosCj4vwdC3Nc5dzprK` from acceptance commit `e182b22bdfea346ba7d38a3f94555e0e74669fee`, with target production despite `main` branch tracking. It was canceled while BUILDING; the API confirmed **CANCELED**. No successful deployment/health result is claimed. **Ignored Build Step is temporarily `exit 0`** to skip further Git builds until credentials/Sheet are ready. Restore its default/Automatic setting only at that point, then explicitly deploy the reviewed `main` commit. Do not promote the canceled acceptance deployment.

## Production environment and Google Sheets

Vercel has **zero environment variables**. All four production values are missing:

| Name | Supply through a secure environment |
| --- | --- |
| `SPREADSHEET_ID` | ID of the owner's private production Sheet |
| `WEBHOOK_SECRET` | Private random URL-safe value, 32–128 characters; same value in APK |
| `GOOGLE_CLIENT_EMAIL` | Service account email shared onto that Sheet |
| `GOOGLE_PRIVATE_KEY` | Service account RSA key; store as Secret/Sensitive |

`WEBHOOK_URL` is for local/deployment-check tooling, not a fifth backend requirement. Never paste keys/JSON into chat, issues, PRs, logs or tracked files.

Owner setup (details in [VCB_PRODUCTION_E2E.md](VCB_PRODUCTION_E2E.md)):

1. Create a private Google Sheet **Finance Bridge Pilot**.
2. Create a Google Cloud service account and enable **Google Sheets API**.
3. Share only that Sheet with the service account email as **Editor**.
4. Enter the four values directly in Vercel Production and a separate private local `.env.local` for bootstrap/check tooling. Never commit the JSON key.

No production Sheet was selected or changed. Credentials are unavailable, so production bootstrap and `--apply` were not run. Existing scripts were inspected: default is read-only; apply uses preflight and rejects incompatible existing schemas. When private configuration exists:

```bash
cd vercel
npm run sheets:bootstrap
# Only if CHANGES_REQUIRED, with other schema edits paused:
npm run sheets:bootstrap -- --apply
npm run sheets:bootstrap
```

Final result must be `ALREADY_READY`; preserve these exact headers:

| Tab | Header from column A |
| --- | --- |
| Transactions | time, bank, type, amount, balance, counterparty, description, category, event_id |
| Raw_Capture | time, bank, raw_text, event_id |

Then deploy the reviewed `main` commit. Require `/api/health` HTTP 200 and `{"alive":true,"version":"3.1.0","environmentReady":true}`. Run `npm run check:deployment` with private local URL/secret: require `health: READY`, `dryRun: VALIDATED_TRANSACTION`, `writes: 0`. Independently confirm dry-run did not add Sheet rows. Health alone does not verify Google permissions. Do not start real transactions before these checks succeed. Verify the intended production endpoint is callable by the phone; preserve preview protection.

## Pilot APK workflow and security blockers

Added [.github/workflows/build-pilot-apk.yml](../.github/workflows/build-pilot-apk.yml), triggered only by `workflow_dispatch`. It checks out the exact SHA, sets up JDK 17/SDK 34/build-tools 34.0.0, runs `testDebugUnitTest verifyWebhookConfigurationPolicy assembleDebug`, and uploads only `app-debug.apk` as **finance-bridge-vcb-pilot-apk**, retained for **one day**.

Created GitHub Environment **vcb-pilot**, restricted to `codex/vcb-production-e2e-acceptance` and `main`. No secrets or approval SHA were populated. After reviewing the full commit, including workflow and Gradle scripts, an environment administrator sets variable `PILOT_REVIEWED_SHA` to that full SHA. Dispatch input `reviewed_sha`, `github.sha`, and the environment variable must match. Each new commit needs review; do not blindly copy HEAD into approval.

Configure only these environment secrets through GitHub Settings:

- `FINANCE_WEBHOOK_URL`: the verified production HTTPS `/api/transaction` URL.
- `FINANCE_WEBHOOK_SECRET`: the same private value as Vercel `WEBHOOK_SECRET`.

A configured build is blocked by two independent conditions:

1. **Repo is public.** Other GitHub users with read access can download its Actions artifacts. APK contains the webhook secret, so admission fails before the secret-bearing job on public repos and privacy is checked again before upload. Retention/Environment protection does not make artifacts private. Owner authorization is needed for an appropriate private repository/distribution arrangement; verify the GitHub plan supports private Environment secrets. No repository visibility change or new repository was made.
2. **New workflow is only on Draft PR #3.** GitHub requires a dispatch workflow on the default branch before manual runs. Dispatch was attempted after push and returned **HTTP 404: workflow not found on the default branch**. No merge/default-branch change was made to bypass that requirement. A successful configured workflow run remains an open gate.

Configured Gradle output, including diagnostics that may quote generated BuildConfig, goes to a temporary runner file and is never printed/uploaded. No private Gradle cache is saved. Only the APK is uploaded; generated output/logs are cleaned. No release, PR attachment or APK commit. Actions are pinned to inspected upstream SHAs and checkout does not retain a write credential.

**Signing/update limitation:** fresh hosted runners generate different debug signing keys. Treat the first APK as an initial pilot install. Never uninstall/Clear data to install a differently signed update with pending outbox events. Drain/reconcile before changing installations. Repeatable in-place updates need a separately secured stable signing key, not provisioned by this workflow.

## CI / regression evidence

- Starting HEAD `b1afbb2d53c16cf6204c718747607ad00494671d`: [CI 40114378259](https://github.com/vi-phuong-158/finance/actions/runs/40114378259) succeeded before these changes.
- Local backend: `npm ci` completed, **58/58 tests**, typecheck completed successfully. Four existing moderate audit findings remain; dependencies/lockfile unchanged.
- Local Android `gradlew.bat --no-daemon assembleDebug testDebugUnitTest verifyWebhookConfigurationPolicy` cannot start: no JDK/`JAVA_HOME`. No JDK/SDK installed locally; required tasks must be checked on exact-head CI.
- Workflow YAML parsed; all six Bash blocks passed syntax validation. Local synthetic checks covered seven admission cases (public/internal visibility, wrong branch/SHA, both allowed branches), three environment approval cases and five configuration cases. A failing Gradle stub deliberately emitted a synthetic value: diagnostics stayed in the temporary log and did not reach stdout/stderr. These are workflow policy checks, not an Android build or device test.
- No configured APK or successful new manual workflow run exists yet. See PR #3 for final exact-head CI evidence; a CI run cannot substitute for the configured workflow gate.

## MANUAL_DEVICE_ACCEPTANCE

Start only after bootstrap, production health/dry-run and configured APK build succeed. Download from the successful private Actions run → **Artifacts → finance-bridge-vcb-pilot-apk** within one day; unzip, transfer `app-debug.apk` by USB/private transfer, open it and permit installation from that chosen source if Android asks. **ADB is optional.** Keep run ID/SHA privately.

- [ ] Tải APK pilot
- [ ] Cài APK
- [ ] Mở app Đồng Bộ Thu Chi
- [ ] Cấp Notification Access (nếu Android chặn: thông tin app → menu ba chấm → Allow restricted settings)
- [ ] Đặt pin của app ở chế độ Không hạn chế
- [ ] Cho phép VCB hiển thị notification
- [ ] Nhấn Kiểm tra kết nối; xác nhận thành công và không tăng dòng Sheet
- [ ] Thực hiện 01 giao dịch CHI nhỏ do chủ tài khoản tự chọn
- [ ] Kiểm tra Transactions: CHI, tiền, số dư, thời gian đúng
- [ ] Thực hiện 01 giao dịch THU nhỏ
- [ ] Kiểm tra Transactions: THU, tiền, số dư, thời gian đúng
- [ ] Kiểm tra không duplicate trên tổng hai tab
- [ ] Thử mất kết nối backend có kiểm soát, vẫn để VCB nhận notification
- [ ] Xác nhận outbox pending sau khi notification thực sự đến
- [ ] Khôi phục kết nối
- [ ] Xác nhận tự gửi lại đúng 1 dòng; retry cùng sự kiện không thêm dòng
- [ ] Reboot khi còn pending nếu có thể
- [ ] Xác nhận recovery; nếu chưa thể thử thì giữ gate chưa nghiệm thu
- [ ] Đối soát dữ liệu với VCB, gồm Raw_Capture và bản ghi BLOCKED

Hoàn tất [checklist chi tiết](VCB_DEVICE_CHECKLIST.md) trong bản riêng. Ghi model/Android, SHA APK/backend và metadata đã che dữ liệu. Không gửi ảnh chứa số tài khoản đầy đủ, raw notification, khóa hoặc DB lên repo/chat công khai.

## Next action and verdict boundary

Owner supplies the four production values securely and resolves private APK distribution/default-branch workflow availability. Then bootstrap, restore the temporary Ignored Build Step to Automatic, deploy `main`, verify health/dry-run, build/download/install APK, and perform real CHI/THU/offline/reboot checks. Do not change installation with pending events.

Only use `VCB_PRODUCTION_E2E_PASS` after real evidence covers every mandatory pipeline/device gate. Credentials and separate APK blockers remain; this run is not ready for device acceptance.
