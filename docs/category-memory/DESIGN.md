# Gợi ý danh mục từ lịch sử (Smart Category Memory) — DESIGN v1

> Trạng thái: thiết kế + mã/test trên nhánh `codex/category-memory-v1` (xếp chồng trên PR #7).
> Mặc định TẮT (`CATEGORY_MEMORY_ENABLED=0`, `CATEGORY_MEMORY_WRITES_ENABLED=0`). Chưa deploy, chưa migration Sheet thật.
> Không AI/LLM/embedding/ML, không DB mới, không dependency mới. Toàn bộ là luật xác định, giải thích được.

## 1. Kết quả review kiến trúc hiện có (Phase A)

| Thành phần | Hiện trạng ảnh hưởng thiết kế |
|---|---|
| Parser VCB/BIDV/VietinBank | VCB lấy `counterparty` từ đoạn `Ref` (sau dấu `.` thứ hai). BIDV/VietinBank luôn `counterparty=''` (comment: tên trong nội dung chuyển khoản không định danh đáng tin). **Không envelope nào có trường số tài khoản đối tác có cấu trúc**; số tài khoản trong envelope là tài khoản của chính chủ. |
| Che dữ liệu | `maskSensitiveIdentifiers` thay mọi dãy ≥ 8 chữ số bằng `***` ở mọi trường ghi. Sheet **không bao giờ** chứa số tài khoản đầy đủ. |
| Danh mục tự động | `matchCategory` theo từ khóa lúc ingest — không phải lựa chọn của người dùng. |
| Lựa chọn của người dùng | (a) giao dịch ghi tay `source=MANUAL` (người dùng chọn danh mục); (b) `Transaction_Edits` (audit append-only, `requestId` UUID, chuỗi `base_revision`, marker nguyên tử, replay so khớp). |
| Android | `ManualOutbox` (SQLite) + `MutationQueue` (NonCancellable insert + schedule) + `ManualSyncWorker` (allowlist endpoint, giữ UUID/payload khi retry, BLOCKED khi lỗi cấu hình/409). |
| Feature flag | Biến môi trường `*_ENABLED='1'`, kiểm tra **sau** xác thực; mặc định tắt. |
| Migration tab | `lib/audit-migration.ts` + `scripts/prepare-finance-audit.ts`: dry-run mặc định, backup đầy đủ ngoài repo, kiểm nguồn đổi, readback, rollback chỉ khi tab rỗng/không marker. |

**Hệ quả quan trọng:** sổ đã có sẵn một *nhật ký sự kiện học* đúng nghĩa — lựa chọn ghi tay và audit sửa giao dịch — đã idempotent, có hàng đợi offline và retry. Bộ nhớ phân loại vì vậy được **suy ra (derive)** từ nhật ký đó thay vì duy trì bộ đếm riêng.

## 2. Kiến trúc

```
Android (chọn/sửa danh mục)                 Vercel                                  Google Sheets (nguồn sự thật)
 RecordScreen ──manual-transaction──▶ ManualOutbox ─▶ /api/manual-transaction ─▶ Transactions (source=MANUAL)
 TransactionEditForm ──transaction-edit─▶            /api/transaction-edit   ─▶ Transaction_Edits (audit)
 CategoryMemoryScreen ──category-memory─▶            /api/category-memory POST ▶ Category_Memory (lệnh SET/DELETE/RESET_ALL)

 GET /api/category-memory  ◀── đọc Transactions + MoneyLover_History + Transaction_Edits + Category_Memory
   → memoryObservations()  (trạng thái mới nhất cho mỗi giao dịch)
   → buildMemoryIndex()    (cửa sổ 10 lần gần nhất / identity / chiều THU|CHI)
   → JSON index gọn ──▶ Android CategoryMemoryStore (file cache) ──▶ gợi ý tức thì, kể cả offline
 GET /api/transactions (flag bật) gắn thêm memoryKey cho mỗi bản ghi để Android tra cache không cần tính lại.
```

- **Không có đường ghi "learn" riêng.** Học xảy ra qua hai đường ghi sẵn có. Không thể xảy ra tình huống "giao dịch lưu được nhưng memory lỗi": memory không phải bản ghi riêng mà là phép chiếu của sổ.
- Ghi mới duy nhất là lệnh quản lý ghi nhớ (sửa/xóa/đặt lại) vào tab `Category_Memory`, append-only.

## 3. Hệ thống nhận dạng (identity)

Mỗi giao dịch có **tối đa một** identity — tầng cao nhất có dữ liệu thắng; không rơi xuống tầng dưới để "mượn" lịch sử (Case B).

| Tầng | Điều kiện | Khóa | Hệ số tin cậy |
|---|---|---|---|
| 1 `ACCOUNT` | Có số tài khoản đối tác có cấu trúc (6–20 chữ số sau khi bỏ khoảng trắng/`.`/`-`) và bank ∈ VCB/BIDV/VIETINBANK | `ACCOUNT:<BANK>:<HMAC-SHA256 32 hex>` | 1.00 |
| 2 `COUNTERPARTY` | Bên kia của chuyển khoản tách được chắc chắn, merchant VCB `Ref` không phải chuyển khoản, Money Lover "Với" | `COUNTERPARTY:<chuẩn hóa>` | 1.00 |
| 3 `DESCRIPTION` | Nội dung BIDV/VietinBank không phải chuyển khoản; ghi chú ghi tay/Money Lover | `DESCRIPTION:<chuẩn hóa>` | 0.85 |

- Tầng 1 **khóa theo ngân hàng** (Case A: `VCB:123456` ≠ `BIDV:123456`). Số tài khoản không bao giờ lưu/trả về: khóa là HMAC với khóa con `HMAC(WEBHOOK_SECRET, "category-memory-identity-v1")` (số tài khoản ít entropy nên không dùng SHA thuần). Hiển thị chỉ `••1234`.
- **Giới hạn trung thực:** hiện chưa parser nào cung cấp số tài khoản đối tác có cấu trúc và Sheet chỉ chứa bản đã che, nên tầng 1 có đầy đủ hợp đồng + test nhưng **chưa có nguồn dữ liệu**; giao dịch thật hiện rơi vào tầng 2/3. Thêm nguồn tầng 1 cần envelope ngân hàng được chủ dự án xác nhận (không đoán format).
- Tầng 2/3 **không khóa theo ngân hàng**: cùng merchant trả bằng VCB hay BIDV là một merchant.
- Rule = `(identity, THU|CHI)`: cùng người nhưng chi (Gia đình) và thu (Tiền chuyển đến) là hai rule riêng.
- Identity luôn tính từ **trường gốc** của giao dịch (trước khi sửa ghi chú), server tự suy ra; client không gửi identity cho đường học.

### Chuẩn hóa (twin TS ↔ Kotlin, vector chung `shared/category-memory-vectors.tsv`)

1. NFD, bỏ dấu kết hợp U+0300–U+036F, `đ/Đ → D`.
2. Mọi ký tự ngoài `[A-Za-z0-9]` → khoảng trắng; uppercase ASCII; tách token.
3. Bỏ token **chứa chữ số** (reference, mã GD, giờ, số tiền, `REF123`, phần đã che `***`).
4. Bỏ token kỹ thuật cố định: `MBVCB IBFT REF TRACE FT QR POS ECOM NAPAS CT CK GD TK ND VND DEN`.
5. **Không cắt bớt.** Identity chỉ hợp lệ khi kết quả 2 ký tự trở lên và **tối đa 8 token**; dài hơn → không có identity (không gợi ý) thay vì lấy 8 token đầu — tiền tố chung (tên người chuyển, "chuyen tien"…) không bao giờ gộp hai bên khác nhau.

Ví dụ: `GRAB REF111`/`GRAB REF999` → `GRAB` (Case C); `CT TNHH TM VA DV SIEU THI AN LAC` → `TNHH TM VA DV SIEU THI AN LAC`.

### Tách bên giao dịch từ nội dung ngân hàng (`extractBankParty`, hardening sau review PR #8)

Nguồn văn bản: VCB dùng `counterparty` lấy từ `Ref` (không dùng description vì đó là cùng `Ref` hoặc cả template khi thiếu `Ref`); BIDV dùng nội dung đã bỏ hậu tố `· Mã giao dịch: …`; VietinBank dùng nội dung.

1. Có **đúng một** cụm `CT tu <tài khoản/***> <NGƯỜI CHUYỂN> toi <tài khoản/***> <NGƯỜI NHẬN> [tai <NGÂN HÀNG>] [(...)]` → bên kia là **người nhận nếu CHI**, **người chuyển nếu THU** (hướng lấy từ giao dịch gốc). Bỏ đuôi `(...)` và đúng một token ` tai <NGÂN HÀNG>` ở cuối (tên có chữ "TAI" vẫn giữ). Kết quả là tầng `COUNTERPARTY`.
2. Có dấu hiệu chuyển khoản khác (`chuyen tien`, `chuyen khoan`, `CK`, `IBFT`, `NAPAS`, `CT den/di`, `tu … toi`, nhiều cụm `CT tu`, `CT tu` thiếu tài khoản, nhiều `toi`) → **không có identity**. Lý do: tên hiển thị trong memo thường là chính chủ (vd `NGUYEN VAN CHU chuyen tien`) nên mọi người nhận sẽ bị gộp vào một rule.
3. Không phải chuyển khoản (QR/POS/merchant) → toàn bộ văn bản chuẩn hóa (≤ 8 token): VCB → `COUNTERPARTY`, BIDV/VietinBank → `DESCRIPTION`.
4. VCB không có `Ref` (`VCB Transaction`) → không có identity.

Hệ quả có chủ đích: chuyển khoản không tách chắc được thì **không bao giờ** được gợi ý/chọn sẵn; người dùng vẫn chọn tay như cũ. Hai người trùng tên (không có số tài khoản) vẫn có thể chung rule — giới hạn còn lại cho tới khi có tầng 1.
Đánh đổi chấp nhận: hai chi nhánh chỉ khác số (`… AN LAC 2`) gộp làm một — cùng chuỗi cửa hàng, cùng mục đích phân loại. Tên khác chữ không bao giờ bị gộp; không bỏ tiền tố pháp lý, không fuzzy match. Không có luật merchant cứng (không "Grab = Đi lại").

## 4. Quan sát (observation) và ngữ nghĩa sửa giao dịch

Một giao dịch là *quan sát* khi người dùng đã thực sự chọn danh mục trong app:
- `source=MANUAL` (thời điểm = ngày giờ giao dịch, +07:00), hoặc
- có ít nhất một dòng `Transaction_Edits` (thời điểm = `created_at` của lần sửa **mới nhất**).

Danh mục quan sát = danh mục **hiện tại** sau toàn bộ chuỗi sửa (latest-state-per-transaction). Bỏ qua `Chưa phân loại`. Danh mục tự động lúc ingest và lịch sử Money Lover chưa sửa **không** được tính.

⇒ Sửa `Ăn uống → Gia đình` trên cùng giao dịch cho đúng **1** quan sát `Gia đình` (Scenario 8). WorkManager gửi lại cùng `requestId` 3 lần → `Transaction_Edits` có 1 dòng (idempotency sẵn có) và kể cả khi có dòng lặp, mỗi giao dịch vẫn chỉ 1 quan sát (Scenario 7).

## 5. Độ tin cậy và recency

- Cửa sổ: **10 quan sát gần nhất** của rule (sau mốc reset gần nhất), sắp theo thời điểm rồi id.
- `confidence = count(danh mục nhiều nhất trong cửa sổ) / số quan sát trong cửa sổ × hệ số tầng`, làm tròn 2 chữ số. Hòa số lần → ưu tiên danh mục dùng gần nhất để hiển thị, nhưng mức là `AMBIGUOUS`.

| Mức | Điều kiện | UI |
|---|---|---|
| `HINT` | 1 quan sát | "Gợi ý từ lịch sử: X · Đã dùng 1 lần" — không chọn sẵn |
| `AMBIGUOUS` | hòa, hoặc confidence < 0.60 | "Gợi ý gần đây: A / B" — không chọn sẵn |
| `PRESELECT` | ≥ 2 lần, đứng đầu duy nhất, confidence ≥ 0.60 | chọn sẵn, ghi rõ "Gợi ý từ lịch sử · 2/3 lần gần đây" |
| `STRONG` | ≥ 4 quan sát, confidence ≥ 0.80 | như trên, ghi "gợi ý mạnh" |

Ví dụ: 8/1/1 → 0.80 STRONG; 3/3 → AMBIGUOUS; 3× Gia đình + 1× Ăn uống → 0.75 PRESELECT Gia đình (Scenario 2: cập nhật nhưng không quên ngay). 10× Ăn uống rồi 5× Gia đình → cửa sổ 5/5 → AMBIGUOUS; thêm 2 Gia đình → 7/10 PRESELECT; 8/10 → STRONG. Cửa sổ trượt là cơ chế recency duy nhất: dễ giải thích ("6/7 lần gần đây"), dễ test, không decay ẩn.

**Không bao giờ auto-submit**: v1 chỉ chọn sẵn trong form; người dùng vẫn bấm lưu. Không phân loại ngầm lúc ingest.

## 6. Quản lý ghi nhớ (tab `Category_Memory`)

Header: `request_id, action, identity_key, type, category, created_at` — append-only, không lưu số tài khoản hay nội dung giao dịch.

| Lệnh | Ngữ nghĩa |
|---|---|
| `SET` (sửa) | Bỏ lịch sử của rule trước mốc này, gieo danh mục mới **như 2 lần chọn** ⇒ lần sau chọn sẵn; người dùng chọn khác 2 lần thì rule tự đổi theo hành vi mới (không khóa). |
| `DELETE` (xóa) | Bỏ toàn bộ quan sát của rule tới mốc này; rule biến mất tới khi có lựa chọn mới. |
| `RESET_ALL` (đặt lại) | Mốc cắt cho mọi rule. |

Lệnh không xóa dữ liệu giao dịch hay audit. Giới hạn đã biết: giao dịch ghi tay **ghi lùi ngày** về trước mốc reset sẽ không được tính (sổ không lưu thời điểm tạo của dòng ghi tay).

## 7. API

| Endpoint | Mô tả |
|---|---|
| `GET /api/category-memory` | Auth `X-Webhook-Secret` (timingSafeEqual), `no-store`, `Vary`. Không nhận query. Flag tắt → `200 {version:1, enabled:false}` (không dữ liệu) để app xóa cache. Bật → `{version, enabled, writesEnabled, generatedAt, window, rules[], categories[]}` (tối đa 2.000 rule dùng gần nhất). |
| `POST /api/category-memory` | Body `{requestId (UUIDv4), action, identityKey?, type?, category?}`, ≤ 2 KiB, allowlist khóa, danh mục phải thuộc registry và hợp Thu/Chi (không nhận `Chưa phân loại`). Cần cả hai flag. Idempotent: marker nguyên tử theo `requestId` + replay so khớp (`DUPLICATE_IGNORED` / `409 IDEMPOTENCY_CONFLICT`). Tab chưa có → `503 CATEGORY_MEMORY_MIGRATION_REQUIRED`. |
| `GET /api/transactions` | Khi `CATEGORY_MEMORY_ENABLED=1` mỗi bản ghi có thêm `memoryKey` (string|null). Tương thích ngược. |

Rule trả về: `key, identityType, label, type, category, count, total, confidence, level, recent[], lastUsedAt, pinned`. `label` là chuỗi đã chuẩn hóa (từ dữ liệu đã che).

## 8. Offline (Android)

- `CategoryMemoryStore`: file cache trong bộ nhớ riêng của app (ghi file tạm rồi rename), định dạng dòng có kiểm tra; hỏng → bỏ qua, không crash. Tồn tại qua restart.
- Làm mới khi mở Lịch sử giao dịch / Ghi chép / Ghi nhớ phân loại và khi bấm Tải lại. Lỗi mạng giữ cache cũ; `enabled:false` xóa cache.
- Tra cứu là phép tra map theo `memoryKey + type` (edit) hoặc `DESCRIPTION:` từ ghi chú đang gõ (ghi tay) — không đọc lịch sử giao dịch khi mở dropdown.
- Xác nhận khi offline: giao dịch/sửa vào `ManualOutbox` như cũ; khi đồng bộ xong server tự suy ra quan sát. Gợi ý cập nhật sau lần làm mới kế tiếp (v1 không overlay lạc quan trên máy).
- Lệnh quản lý đi qua cùng `MutationQueue` (endpoint `category-memory`), retry giữ nguyên UUID/payload.

## 9. Idempotency tóm tắt

| Tình huống | Cơ chế |
|---|---|
| Retry sửa giao dịch | `Transaction_Edits` idempotent theo `requestId` (sẵn có) + 1 quan sát/giao dịch |
| Retry ghi tay | `event_id = sha256(MANUAL:requestId)` + marker nguyên tử (sẵn có) |
| Retry lệnh ghi nhớ | marker `finance_<sha256(MEMORY:requestId)>` + replay so khớp |
| Sửa A → B | latest-state-per-transaction, không cộng dồn |

## 10. Threat model

| Rủi ro | Biện pháp |
|---|---|
| Lộ số tài khoản | Không đọc nguồn chưa che; tầng 1 dùng HMAC có khóa; label chỉ từ dữ liệu đã che; không log payload. |
| Client giả identity để "dạy" sai | Đường học không nhận identity từ client; server suy từ giao dịch đã lưu. Lệnh SET chỉ nhận khóa đúng định dạng và danh mục trong registry. |
| Formula injection | Ghi literal `stringValue` qua `appendCells` (không USER_ENTERED). |
| Payload lớn/sai | Giới hạn 2 KiB, allowlist khóa, regex khóa ≤ 200 ký tự → 400/413 trước khi tạo client Sheets. |
| Thiếu auth / flag | 401 trước mọi đọc; flag kiểm tra sau auth; mặc định tắt. |
| Dòng lệnh hỏng trong Sheet | Đọc fail-closed `503 CATEGORY_MEMORY_SCHEMA_MISMATCH`; giao dịch không bị ảnh hưởng. |
| Gợi ý sai ép người dùng | Không auto-submit; luôn sửa được; "Bỏ gợi ý"; rule tự đổi theo lựa chọn mới. |

## 11. Migration

`npm run audit:prepare -- --scope category-memory` (dry-run, chỉ đọc) → `CHANGES_REQUIRED|ALREADY_READY`.
Apply (chỉ chủ dự án): `-- --scope category-memory --apply --backup <file ngoài repo>`: backup đầy đủ, kiểm nguồn không đổi, thêm tab + header trong một batch, đọc lại. Không sửa/xóa tab khác, không rewrite lịch sử. Rollback `--rollback` chỉ khi tab rỗng và không có marker. Không có tab: GET vẫn chạy (không lệnh nào), POST trả `MIGRATION_REQUIRED`.

## 12. Rollout

1. Merge sau PR #7 (phụ thuộc `Transaction_Edits`, `MutationQueue`).
2. Preview: bật `CATEGORY_MEMORY_ENABLED=1` (chỉ đọc) trên Sheet thử nghiệm; kiểm tra index và màn 360/412dp trên thiết bị.
3. Chạy migration dry-run → apply trên Sheet thử, bật `CATEGORY_MEMORY_WRITES_ENABLED=1`, thử sửa/xóa/đặt lại.
4. Production chỉ khi chủ dự án nghiệm thu riêng. Tắt flag là rollback hoàn toàn (không có dữ liệu suy ra nào cần dọn).

Nâng cấp sau (không làm trong v1): nguồn tầng 1 khi có envelope xác nhận; overlay lạc quan khi offline; tùy chọn học từ lịch sử Money Lover chưa sửa.
