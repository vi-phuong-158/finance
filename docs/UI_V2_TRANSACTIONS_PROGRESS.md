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

## Milestone 3: Round 4 review fixes (PROMPT 10, 30/10/2026)
- **Overlay queued outbox edits on server load & map blocked state (Commit 4a604df):**
  - Added `ManualOutbox.activeEdits()` querying `PENDING`, `SYNCING`, and `BLOCKED` mutations on `Dispatchers.IO` (read-only).
  - Added pure `TransactionOptimisticUpdate.overlayPendingEdits()` and `parseEditPayload()` to overlay active outbox edits onto fresh server responses.
  - Server reloads, searches, and tab switches preserve pending mutations (`pendingSync = true`), eliminating the rollback glitch.
  - Mapped `BLOCKED` mutations to "Cần xử lý" status pill without applying draft payload.
  - Added 4 JVM unit tests for overlay logic + 1 for outbox activeEdits query.
- **Reduce category badges per Brief §21 (Commit aadf760):**
  - In `TransactionSmartStatus`, `source == "MONEYLOVER"` is treated as `REMEMBERED` (confirmed by user in Money Lover history).
  - In `TransactionRow`, badges are suppressed when status is `REMEMBERED` or `null` (internal transfer), preventing the entire list from being covered in yellow dots.
  - Only "Cần kiểm tra" (warning amber dot) and "Chưa phân loại" (expense red dot) show badges.
  - Added test cases in `TransactionSmartStatusTest`.
- **Force excluded=true for internal transfers (Commit 6bdd8e0):**
  - In `TransactionOptimisticUpdate.apply`, `category == "Chuyển nội bộ"` automatically forces `excluded = true` to match server rules.
  - Added JVM test `applyForInternalTransferCategoryAlwaysForcesExcludedToTrue`.
- **Validation:** 101/101 JVM unit tests passing (0 failures, 0 ignored, exceeding target >= 92), 45 actionable tasks executed, 7 webhook configuration policy checks passed, clean `assembleDebug` APK build.
