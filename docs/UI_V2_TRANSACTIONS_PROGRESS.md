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

## Milestone 4: Redesign transaction detail sheet & edit form (PROMPT 11, 31/10/2026)
- **Sheet chi tiết (Commit 741827a):**
  - Phần đầu: Ô icon danh mục 48dp có nền tròn màu CategoryVisuals (alpha ~0.15). Bên phải là số tiền có dấu (+/−) dùng AmountText autoShrink (sàn 70%, \u00A0đ, không cắt "..."). Dưới số tiền là tên danh mục, chip Thu/Chi, cùng nhãn "Đang chờ đồng bộ" / "Cần xử lý" bảo toàn từ vòng 4.
  - Thẻ thông tin (Surface bo 16dp, border mảnh): Các dòng >=56dp gồm icon dẫn (Tài khoản: ví, Thời gian: lịch, Nguồn: icon theo nguồn, Báo cáo: biểu đồ), caption ở trên và giá trị ở dưới, ngăn cách bằng HorizontalDivider mảnh. Dòng mô tả/ghi chú riêng biệt kèm nút "Xem đầy đủ" / "Thu gọn".
  - Cảnh báo: Đổi cảnh báo "Có thể trùng…" và "Chưa mở chỉnh sửa…" thành banner tonal bo góc có icon cảnh báo/thông tin, không dùng chữ đỏ trơn.
  - Nút "Sửa" cố định ở đáy sheet (Surface elevation 2dp), thân sheet cuộn độc lập không trôi nút.
- **Form sửa TransactionEditForm (Commit 1e23b22):**
  - Thẻ đầu chỉ đọc: Hiển thị icon tròn 48dp, số tiền có dấu và tài khoản (không cho sửa số tiền).
  - SegmentedButton Thu/Chi đặt ngay dưới thẻ đầu.
  - Lưới CategoryTile: Ô đang chọn có viền 2dp và nền tonal theo màu danh mục (alpha 0.16), chữ đậm cùng màu tint. Giữ nguyên khối "Danh mục đề xuất" Phase C.
  - Thẻ ghi chú & báo cáo: Ô ghi chú có icon dẫn; công tắc "Không tính vào báo cáo" là dòng ListItem có Switch trong cùng thẻ.
  - Thanh đáy cố định: Hai nút "Hủy" (outlined) và "Lưu thay đổi" (filled) weight 1:1, hỗ trợ imePadding và navigationBarsPadding (bàn phím mở không che nút). Giữ nguyên khóa Lưu khi đang xác minh revision (P4) và hiển thị thông báo tiến trình. Không thêm nút Xóa.
- **Tách hàm thuần & Test JVM (Commit be25571):**
  - Tách `amountWithSign(type, amount)`, `sourceLabel(source)`, `reportStatusLabel(excluded)` sang `TransactionDetailPresentation.kt`.
  - Bổ sung 5 test case mới trong `TransactionDetailPresentationTest.kt`, kiểm tra toàn diện các nhánh kiểu enum/chuỗi, số âm/0, nguồn dữ liệu và cờ loại trừ.
- **Validation:** 106/106 JVM unit tests passing (0 failures, 0 ignored, tăng từ 101), 45/45 actionable tasks executed, 7 webhook configuration policy checks passed, APK `assembleDebug` build clean.

