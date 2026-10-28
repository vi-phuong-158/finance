# Money Lover history import and unified read API

Import keeps eligible `Transaction` rows in a separate `MoneyLover_History` tab.
The existing `Transactions` (9 columns) and `Raw_Capture` (4 columns), webhook
authentication, parser, event identity and Android upload pipeline keep their contracts.
Bank categorization uses the Money Lover registry; existing bank category aliases
are mapped at read time without rewriting existing cells.

## Source and schema

The importer reads `.xlsx` only, never saves it, and ignores other worksheets.
It requires headers `Ngày`, `Nhóm`, `Số tiền`, `Đơn vị tiền tệ`, `Ví` (or the
documented English aliases in `parser.ts`). It preserves optional `Ghi chú`, `Với`,
`Sự kiện`, `Không tính vào báo cáo`, `No.`, `Thành viên`. Unknown or duplicate
headers, formulas, invalid dates/amounts/currencies/flags and unmapped populated
columns are rejected. A rejected row blocks the whole apply; the CLI reports only
aggregate rejected counts. No partial import or guessed correction.

`MoneyLover_History!A:O` has these exact headers:

```text
legacy_id,date,type,amount,currency,amount_vnd,category,wallet,note,counterparty,event,excluded_from_report,source,original_no,member
```

Negative source amounts become `CHI` with absolute `amount`; positive amounts
become `THU`. Zero, unsafe amounts and unsupported precision are rejected.
Dates remain ISO `YYYY-MM-DD`; Excel 1900/1904 serial dates are supported, including
rejection of Excel's nonexistent 1900-02-29. Fractional serials/timestamps are rejected
rather than silently losing a transaction time. Free-text fields are passed through
`maskSensitiveIdentifiers` before import and when read by the API; the source file is unchanged.
`original_no` and `member` retain source provenance; neither appears in the API.

Money Lover historical transactions admitted into the system are strictly VND consumption
records. Any row with `currency != VND` (such as JPY foreign exchange) is excluded by
policy. Non-consumption financial categories (`Cho vay`, `Trả lãi`, and potential loan/debt
categories `Vay`, `Đi vay`, `Trả nợ`, `Thu nợ`) are filtered out from history.
Rows from the `Tiết Kiệm` wallet are also excluded from import and finance reads;
matching ignores accents and case. Existing saved rows are not deleted by this filter.
Legitimate income categories (`Lương`, `Thu nhập khác`, `Tiền chuyển đến`) that are not
loans or debts remain admitted.

Exclusion uses an explicit allowlist: blank/false/0/no/không → false;
true/1/yes/có/x/✅ → true (text is case insensitive). Unknown flags are rejected.

## Dry-run and apply

Requirements: existing Node 24 dependencies and Python 3 standard library.
No new npm/Python packages are needed. `MONEYLOVER_PYTHON` can select a Python
executable. Python is CLI-only; Vercel read APIs do not launch it.

From `vercel/`, use a private source path and a new report path outside tracked files:

```powershell
npm run moneylover:import -- --file "C:/private/history.xlsx" --report "C:/private/source-report.json"
npm run moneylover:import -- --file "C:/private/history.xlsx" --check-sheets --report "C:/private/sheets-plan.json"
npm run moneylover:import -- --file "C:/private/history.xlsx" --check-sheets --overlap-decisions "C:/private/overlap-decisions.json" --report "C:/private/reviewed-plan.json"
npm run moneylover:import -- --file "C:/private/history.xlsx" --expectations "C:/private/expected.json" --apply --report "C:/private/applied.json"
```

`MONEYLOVER_SOURCE_FILE` is an alternative to `--file`.
The first command is offline and writes no Sheets data. It reports
`DRY_RUN_SOURCE_ONLY`; `skipped: null` means existing Sheets IDs were not checked.
`--check-sheets` performs a real read-only duplicate/schema check against history and
Transactions. An unreviewed overlap returns `OVERLAP_REVIEW_REQUIRED` and exit 2.
Pass the same `--overlap-decisions` file to both check and apply when overlaps exist.
Only `--apply` writes. All Sheets modes load the existing Google
configuration from environment or optional `vercel/.env.local`:
`SPREADSHEET_ID`, `GOOGLE_CLIENT_EMAIL`, `GOOGLE_PRIVATE_KEY`.
Never put credentials in command arguments, source or reports. The CLI does not
need the webhook secret to access Sheets.

CLI stdout and reports include only counts and statuses: no transaction rows,
amounts, dates, wallet/category names or reconciliation values. Private totals are
computed in memory for expectations. Reports must be outside the repository and
cannot overwrite existing files or the source. Errors use allowlisted codes.
Dry-run ports use readonly scope and reject mutation calls even if called accidentally.

## Reconciliation

`--expectations` accepts a private JSON object, for example **synthetic** values:

```json
{"rows":3,"incomeCount":1,"expenseCount":2,"expenseVnd":42500,"incomeVnd":50000,"categoryExpenseVnd":{"Cafe":42500}}
```

Provide the actual acceptance values from the owner's request privately, including
all required category totals. The repository deliberately contains no personal
acceptance totals. A mismatch exits nonzero and blocks apply; numbers are never
modified to force a match. Without expectations, reconciliation is explicitly
`EXPECTATIONS_NOT_PROVIDED`, not a successful acceptance check. Apply without
`--expectations` is refused with `EXPECTATIONS_REQUIRED` before accessing Sheets.

Compare source row count, date bounds, THU/CHI, currency, wallet and category counts;
category VND expense totals privately; these are not printed by the CLI. Source expectations
refer to the eligible source before overlap decisions; the import report separately counts
history rows excluded by reviewed overlap decisions. Non-VND and non-consumption
rows are excluded by policy during source parsing and cleanup. Preserve the
source SHA-256 and private report as the import evidence.

## Duplicate protection and recovery

`ml1_` IDs hash canonical date/type/amount/currency/amount_vnd/category/wallet/note/
counterparty/event/exclusion/member plus an occurrence number among *identical*
records. Unicode NFC and newline normalization stabilize identity; export ordinal
and worksheet row position do not determine identity. Reordering/renumbering a full
export preserves its ID set. Identical source rows remain separate transactions.
Identity is calculated from the original source content before masking; redaction does
not re-key existing exports. Previously saved unmasked history is not rewritten;
content differences block with `HISTORY_CONTENT_CONFLICT` and need a separate owner review.

For each date/type/amount candidate in Transactions, a private `--overlap-decisions`
JSON file may explicitly retain both (`KEEP_BOTH`) or mark only the history row
`excluded_from_report=true` (`EXCLUDE_HISTORY`). History remains visible; its source ID
does not change. No bank row is modified. Decisions bind the exact history ID and
complete matching transaction ID set. New or stale candidates require review again;
missing decisions block apply. Do not infer a decision from a matching amount alone.
The decision-file format uses identifiers only (the following identifiers are synthetic):

```json
{"version":1,"decisions":[{"historyId":"ml1_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","transactionIds":["bank:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"],"action":"EXCLUDE_HISTORY"}]}
```

Keep this file outside the repository and retain it for reruns. Candidate detection
uses immutable Transactions rows; audit edits or future bank arrivals still require
normal report reconciliation. The API's heuristic continues to flag rather than
automatically merge other unreviewed rows.

Repeated full exports are supported. Partial exports and edited records cannot
prove whether an identical record is new: a changed record receives a new ID and
does not amend the old row. Review source changes privately before apply.

Apply validates the whole existing history, checks IDs/content, re-reads the
snapshot immediately before writing, then uses one Sheets batch to add the tab
(if absent) and append literal typed cells. Strings beginning with `=` stay strings,
equivalent to RAW input. It never writes `formulaValue`, updates existing history,
or touches the bank/raw tabs. Empty malformed history tabs fail closed. If all
source rows are filtered out and the tab is missing, apply still creates and verifies
the 15-column header-only tab. A no-op is reported only when the tab already exists.
Preflight blocks a projected history above 50,001 rows including header and bounds
the serialized atomic append to a conservative local ceiling of 2 MiB (with margin).
Oversized batches are refused rather than silently split into partial imports.
Cleanup uses a single atomic `spreadsheets.batchUpdate` (`updateCells`) to overwrite
retained rows and clear excess historical rows starting from row 2 in one operation,
preserving headers and eliminating any intermediate cleared state.

Run one importer on one machine at a time; stop manual history/Transactions edits
and competing transaction writers during apply. Both snapshots are re-read before
append; changed bank data returns `TRANSACTIONS_CHANGED_RETRY_DRY_RUN`.
A local lock blocks another CLI in the same checkout. This is **not** a distributed
unique constraint: the preflight and append are separate operations. Do not run
another checkout/host concurrently. Sheets atomic batch application does not make
the read-before-write dedup atomic.

After append, readback checks all planned IDs/content and preservation of previous
rows. A lost append response is reconciled, never blindly retried. If the outcome
is unknown, retain source/report and run `--check-sheets` first. Restore or remove a
stale `.moneylover-import.lock` only after confirming no importer is running.

Rollback is an owner operation: retain a restricted pre-import backup and Sheets
version history. Disable deployment of the new read handlers if needed; the bank
pipeline continues to use only its original tabs. Do not delete rows/tabs or restore
the entire workbook automatically, since new bank data could be lost. Importer has
no delete/overwrite/undo mode.

## Authenticated read APIs

Both endpoints require `X-Webhook-Secret`, using the existing private runtime
credential and constant-time comparison. Never send it in query parameters or embed
it in the APK. Responses (including errors) are private and `no-store` for CDN and
browser caches. The server uses a read-only Sheets scope and fixed range allowlist;
`Raw_Capture`, credentials, Sheet IDs and member data are not exposed.

| Endpoint | Query | Result |
| --- | --- | --- |
| `GET /api/transactions` | `from,to,type,category,bank,wallet,source,q,limit,cursor` | bounded `transactions`, `nextCursor`, `limit`, `total`, read timestamp |
| `GET /api/dashboard` | same filters except pagination; `month=YYYY-MM` or `year=YYYY` (also `year=YYYY&month=M`) | VND income/expense/net, counts, category/account summaries, ten recent transactions, read timestamp |

Dates are inclusive. Dashboard defaults to the current calendar month in
`Asia/Ho_Chi_Minh`; custom dashboard ranges require both `from` and `to`.
Do not mix month/year with custom dates. Transactions default to all dates, page
size 50 (1–100). Filters accept `THU|CHI`, `VCB|BIDV|VIETINBANK`,
`BANK_NOTIFICATION|MONEYLOVER`; wallet applies to historical wallets. Search covers
description/party/category/wallet/event and ignores Vietnamese accents.
Unknown/repeated parameters and malformed values return 400.

Cursor signatures bind the filter, page size and data snapshot. Forgery or filter
changes return 400; source changes between pages return 409 `CURSOR_STALE`, so
restart from page one. Order is descending date, then known bank time, then ID;
date-only records have `time:null,datePrecision:DAY` and no inferred midnight.
Their within-day order is deterministic and is not a claim about transaction time.

Internal transfers and exclusion flags remain visible in history but are omitted
from report income/expense/net. Category/account summaries use the same eligibility
rules. `transactionCount` includes all filtered rows; `includedTransactionCount`
counts those used in the VND totals. `lastUpdatedAt` means API read time, not the
source's last modification time. No balance is invented for historical wallets.

Only report-eligible cross-source rows matching date/type/VND-equivalent amount are flagged
`possibleDuplicate` with candidate count. They are not merged/deleted/excluded by
this heuristic; totals may therefore contain both sources until owner review.
Rows with effective `excludedFromReport=true` (including replayed audit edits and
internal transfers) stay visible but neither contribute to duplicate groups nor
receive duplicate flags/counts. A reviewed `EXCLUDE_HISTORY` pair therefore no
longer raises a dashboard warning; unreviewed eligible pairs continue to do so.
Bank names alone never imply an internal transfer. Explicit `CHUYEN TIEN NOI BO`
or a manually assigned `Chuyển nội bộ` category excludes that transaction.

The registry contains the 34 original user category names plus system transfer and
uncategorized entries. Grab/Taxi/Xanh SM → Taxi; VETC/EPASS → Di chuyển; PVOIL → Xăng;
income rules do not assign expense categories. Old aliases are read without
rewriting bank cells; Money Lover category text is preserved apart from identifier masking.

## Validation and release gates

`npm test` includes sanitized XLSX/CLI fixtures and fake Sheets: parsing, identity,
rerun idempotency, excluded/foreign money, ambiguous append, readback, auth,
pagination, periods and existing webhook regressions. `npm run typecheck` is required.
CI sets up Python 3.13 for the offline XLSX test; it uses no real workbook or secret.

Read APIs validate grid metadata and read within actual row/column bounds. Grids
larger than 50,001 rows (including header and unused rows) fail closed, so later
populated rows cannot be silently omitted. There is no additional cache/DB.
Sheet imports and hosted API acceptance must be recorded separately from local
tests. A Draft PR and green tests do not prove a production import, deployment or
device acceptance. Follow the current owner's data-access rules before processing
a real workbook or invoking apply.
