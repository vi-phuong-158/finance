# Money Lover history import and unified read API

Import preserves the full `Transaction` sheet in a separate `MoneyLover_History` tab.
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
columns are rejected. A rejected row blocks the whole apply, with row number and
error code only. No partial import or guessed correction.

`MoneyLover_History!A:O` has these exact headers:

```text
legacy_id,date,type,amount,currency,amount_vnd,category,wallet,note,counterparty,event,excluded_from_report,source,original_no,member
```

Negative source amounts become `CHI` with absolute `amount`; positive amounts
become `THU`. Zero, unsafe amounts and unsupported precision are rejected.
Dates remain ISO `YYYY-MM-DD`; Excel 1900/1904 serial dates are supported, including
rejection of Excel's nonexistent 1900-02-29. Fractional serials/timestamps are rejected
rather than silently losing a transaction time. Source text is preserved.
`original_no` and `member` retain source provenance; neither appears in the API.

Money Lover historical transactions admitted into the system are strictly VND consumption
records. Any row with `currency != VND` (such as JPY foreign exchange) is excluded by
policy. Non-consumption financial categories (`Cho vay`, `Trả lãi`, and potential loan/debt
categories `Vay`, `Đi vay`, `Trả nợ`, `Thu nợ`) are filtered out from history.
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
npm run moneylover:import -- --file "C:/private/history.xlsx" --cleanup --apply --report "C:/private/cleanup-report.json"
npm run moneylover:import -- --file "C:/private/history.xlsx" --expectations "C:/private/expected.json" --apply --report "C:/private/applied.json"
```

`MONEYLOVER_SOURCE_FILE` is an alternative to `--file`.
The first command is offline and writes no Sheets data. It reports
`DRY_RUN_SOURCE_ONLY`; `skipped: null` means existing Sheets IDs were not checked.
`--check-sheets` performs a real read-only duplicate/schema check.
Only `--apply` writes. The latter two commands load the existing Google
configuration from environment or optional `vercel/.env.local`:
`SPREADSHEET_ID`, `GOOGLE_CLIENT_EMAIL`, `GOOGLE_PRIVATE_KEY`.
Never put credentials in command arguments, source or reports. The CLI does not
need the webhook secret to access Sheets.

Reports include financial aggregates and wallet/category names. Keep them private;
do not paste them into public GitHub logs or PRs. Report paths cannot overwrite
existing files or the source. Errors emitted by the CLI are allowlisted codes.

## Reconciliation

`--expectations` accepts a private JSON object, for example **synthetic** values:

```json
{"rows":3,"incomeCount":1,"expenseCount":2,"expenseVnd":42500,"incomeVnd":50000,"categoryExpenseVnd":{"Cafe":42500}}
```

Provide the actual acceptance values from the owner's request privately, including
all required category totals. The repository deliberately contains no personal
acceptance totals. A mismatch exits nonzero and blocks apply; numbers are never
modified to force a match. Without expectations, reconciliation is explicitly
`EXPECTATIONS_NOT_PROVIDED`, not a successful acceptance check.

Compare source row count, date bounds, THU/CHI, currency, wallet and category counts;
category VND expense totals; and eligible report VND totals. Non-VND and non-consumption
rows are excluded by policy during source parsing and cleanup. Preserve the
source SHA-256 and private report as the import evidence.

## Duplicate protection and recovery

`ml1_` IDs hash canonical date/type/amount/currency/amount_vnd/category/wallet/note/
counterparty/event/exclusion/member plus an occurrence number among *identical*
records. Unicode NFC and newline normalization stabilize identity; export ordinal
and worksheet row position do not determine identity. Reordering/renumbering a full
export preserves its ID set. Identical source rows remain separate transactions.

Repeated full exports are supported. Partial exports and edited records cannot
prove whether an identical record is new: a changed record receives a new ID and
does not amend the old row. Review source changes privately before apply.

Apply validates the whole existing history, checks IDs/content, re-reads the
snapshot immediately before writing, then uses one Sheets batch to add the tab
(if absent) and append literal typed cells. Strings beginning with `=` stay strings,
equivalent to RAW input. It never writes `formulaValue`, updates existing history,
or touches the bank/raw tabs. Empty malformed history tabs fail closed.

Run one importer on one machine at a time; stop manual history edits during apply.
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

Cross-source rows matching date/type/VND-equivalent amount are flagged
`possibleDuplicate` with candidate count. They are not merged/deleted/excluded by
this heuristic; totals may therefore contain both sources until owner review.
Bank names alone never imply an internal transfer. Explicit `CHUYEN TIEN NOI BO`
or a manually assigned `Chuyển nội bộ` category excludes that transaction.

The registry contains the 34 original user category names plus system transfer and
uncategorized entries. Grab/Taxi/Xanh SM → Taxi; VETC/EPASS → Di chuyển; PVOIL → Xăng;
income rules do not assign expense categories. Old aliases are read without
rewriting bank cells; Money Lover category text stays exactly as exported.

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
