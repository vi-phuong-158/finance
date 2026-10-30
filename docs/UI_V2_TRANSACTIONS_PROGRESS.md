# PR #19 — PROMPT 5c / PROMPT 8

## Milestone 1: review fixes (30/10/2026)
- `opening` is cleared in `finally`, including effect cancellation when the sheet is dismissed; a later transaction cannot inherit the stuck loading flag.
- Category visuals use a separate ten-hue light/dark palette, never income/expense. All 36 registry categories are tested; icon against its 12% tint has at least 3:1 contrast (normal and selected surfaces).
- Category tiles use available BoxWithConstraints width (3/4/5/6 columns), two-line labels, equal content sizing and >=48dp targets.
- Detail/edit transaction snapshots survive saved instance state; form fields keep their existing rememberSaveable values. Runtime fold/unfold remains unverified.
- Sheet confirmValueChange refuses Hidden while saving; import cleanup included.
- Validation: offline --rerun-tasks testDebugUnitTest assembleDebug verifyWebhookConfigurationPolicy validateReleaseWebhookConfig succeeded, 45 tasks executed; 81 tests, 0 failures/errors/skips; seven policy checks; APK/BuildConfig scan clean.
- Runtime BLOCKED: no adb device, emulator executable/system image or AVD found in the installed SDK. No runtime screenshots or resize/keyboard/device acceptance; no tools installed.

## Milestone 2: P4, P5 and Phase C handover completion (30/10/2026)
- **P4 (Commit 6b7f19b):**
  - Form opens immediately gated only by local outbox (`WAIT_FOR_SYNC` / `RESOLVE_CONFLICT`).
  - Save button is locked (`enabled = !saving && canSave && !verifying`) with status message while revision is verified in background.
  - User edits typed while awaiting revision read are strictly preserved; server updates untouched fields.
  - `prepareEdit` signature streamlined; `TransactionEditFlowTest` covers 3 resolution branches.
- **P5 (Commit 70cd5bc):**
  - Optimistic in-place update: modifying a transaction reflects immediately in `rows` with "Đang chờ đồng bộ" badge on `TransactionRow`.
  - Non-blanking reloads: list is never wiped (`rows = emptyList()` removed); thin `LinearProgressIndicator` displays while reloading.
  - Category memory only refreshes upon screen open and explicit reload.
  - Pure logic isolated into `TransactionOptimisticUpdate` with unit tests.
- **Phase C (Commit 64d18a1):**
  - Dedicated "Danh mục đề xuất" block in `TransactionEditForm` per brief §19 with "Xác nhận" and "Đổi danh mục" controls.
  - "Đã ghi nhớ" (green dot) / "Cần kiểm tra" (amber dot) / "Chưa phân loại" status on `TransactionRow` per brief §21.
  - Pure status derivation in `TransactionSmartStatus` with unit tests.
  - Strict manual confirmation maintained; no auto-submit.
- Validation: 92/92 JVM unit tests passing, clean debug APK build, 7 webhook policy checks pass.
