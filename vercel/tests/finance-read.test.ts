import assert from 'node:assert/strict';
import test from 'node:test';
import { CATEGORY_REGISTRY, matchCategory } from '../lib/categories.js';
import { fromFinanceSnapshot } from '../lib/unified-transactions.js';
import { buildDashboard } from '../lib/dashboard-service.js';
import { filterTransactions, paginateTransactions, parseFinanceQuery } from '../lib/finance-query.js';
import { handleFinanceRead, readFinance } from '../lib/finance-read-service.js';
import { testEnvironment, SYNTHETIC_SECRET } from './support.js';
import { financeFixture } from './moneylover-fixtures.js';
import { HISTORY_HEADERS } from '../lib/moneylover/schema.js';
import { financeReadRanges } from '../lib/google-finance-read.js';
import { TRANSACTION_HEADERS } from '../lib/sheet-schema.js';
import { EDIT_HEADERS } from '../lib/edit-schema.js';

test('historical savings-wallet records stay in Sheets but are omitted from finance reads', () => {
  const snapshot = financeFixture();
  snapshot.history[1][7] = 'Tiết Kiệm';
  const original = structuredClone(snapshot);
  const rows = fromFinanceSnapshot(snapshot);
  assert.equal(rows.some((r) => r.source === 'MONEYLOVER' && r.accountOrWallet === 'Tiết Kiệm'), false);
  assert.deepEqual(snapshot, original);
  const id = `moneylover:${snapshot.history[1][0]}`;
  const edits = [EDIT_HEADERS, ['11111111-1111-4111-8111-111111111111', id, 'Cafe', '', false, 'CHI', '0', '2026-01-01T00:00:00Z']];
  assert.equal(fromFinanceSnapshot({ ...snapshot, edits }).some((r) => r.id === id), false);
});

test('Sheets read ranges fit actual grids, enforce the allowlist and fail before truncating oversized sources', () => {
  const tabs = [{ title: 'Transactions', rows: 1000, columns: 26, type: 'GRID' },
    { title: 'MoneyLover_History', rows: 2000, columns: 15, type: 'GRID' }];
  assert.deepEqual(financeReadRanges(tabs), ["'Transactions'!A1:L1000", "'MoneyLover_History'!A1:O2000"]);
  assert.throws(() => financeReadRanges(tabs.slice(0, 1)), /FINANCE_SCHEMA_MISMATCH/);
  assert.throws(() => financeReadRanges([{ ...tabs[0], rows: 50002 }, tabs[1]]), /FINANCE_SOURCE_LIMIT_EXCEEDED/);
  assert.throws(() => financeReadRanges([{ ...tabs[0], columns: 8 }, tabs[1]]), /FINANCE_SCHEMA_MISMATCH/);
});

test('registry preserves 34 user categories and scopes keyword rules to income/expense', () => {
  assert.equal(CATEGORY_REGISTRY.filter((c) => !c.system).length, 34);
  assert.equal(new Set(CATEGORY_REGISTRY.map((c) => c.id)).size, CATEGORY_REGISTRY.length);
  for (const phrase of ['Grab', 'Taxi', 'Xanh SM']) assert.equal(matchCategory(phrase, 'CHI'), 'Taxi');
  assert.equal(matchCategory('VETC', 'CHI'), 'Di chuyển'); assert.equal(matchCategory('PVOIL', 'CHI'), 'Xăng');
  assert.equal(matchCategory('nhận lương', 'THU'), 'Lương'); assert.equal(matchCategory('HIGHLANDS', 'THU'), 'Tiền chuyển đến');
  assert.equal(matchCategory('CHUYEN TIEN NOI BO HIGHLANDS', 'CHI'), 'Chuyển nội bộ');
  assert.equal(matchCategory('BIDV VCB', 'CHI'), 'Chưa phân loại');
  assert.equal(matchCategory('LOTTE CINEMA', 'CHI'), 'Giải trí');
});
test('unified model preserves date-only VND transactions, masks details and detects candidate duplicates', () => {
  const snapshot = financeFixture(), before = structuredClone(snapshot), rows = fromFinanceSnapshot(snapshot);
  assert.equal(rows.length, 6); assert.deepEqual(snapshot, before);
  const historical = rows.find((r) => r.source === 'MONEYLOVER' && r.category === 'Cafe')!;
  assert.equal(historical.time, null); assert.equal(historical.datePrecision, 'DAY');
  assert.equal(historical.possibleDuplicate, true); assert.equal(historical.possibleDuplicateCount, 1);
  assert.equal(rows.filter((r) => r.possibleDuplicate).length, 2);
  const bank = rows.find((r) => r.bank === 'VCB')!;
  assert.equal(bank.time, '2026-02-28T13:22:00+07:00'); assert.equal(bank.datePrecision, 'MINUTE');
  assert.equal(rows.find((r) => r.category === 'Thu nhập khác')!.currency, 'VND');
  assert.equal(JSON.stringify(rows).includes('Member A'), false);
});
test('dashboard excludes internal transfers and excluded source rows from VND totals', () => {
  const rows = fromFinanceSnapshot(financeFixture()), q = parseFinanceQuery({ month: '2026-02' }, true);
  const d = buildDashboard(rows, q, new Date('2026-03-01T00:00:00Z'));
  assert.equal(d.income, 160000); assert.equal(d.expense, 25000); assert.equal(d.net, 135000);
  assert.equal(d.transactionCount, 6); assert.equal(d.includedTransactionCount, 4);
  assert.equal(d.excludedTransactionCount, 2);
  assert.equal(d.possibleDuplicateCount, 2);
  assert.equal(d.categorySummary.find((c) => c.name === 'Chuyển nội bộ')!.expense, 0);
  assert.equal(d.accountSummary.find((a) => a.name === 'Wallet A')!.expense, 12500);
});

test('reviewed history exclusions stay visible without duplicate warnings on either source', () => {
  const snapshot = financeFixture();
  snapshot.history[1][11] = true;
  const rows = fromFinanceSnapshot(snapshot);
  assert.equal(rows.length, 6);
  assert.equal(rows.find((t) => t.id === `moneylover:${snapshot.history[1][0]}`)!.excludedFromReport, true);
  assert.equal(rows.every((t) => !t.possibleDuplicate && t.possibleDuplicateCount === 0), true);
  assert.equal(buildDashboard(rows, parseFinanceQuery({ month: '2026-02' }, true)).possibleDuplicateCount, 0);
});

test('duplicate warnings use effective edit exclusions and still flag unreviewed pairs', () => {
  for (const source of ['bank', 'history'] as const) {
    const snapshot = financeFixture();
    const id = source === 'bank' ? `bank:${snapshot.bank[1][8]}` : `moneylover:${snapshot.history[1][0]}`;
    const edit = ['11111111-1111-4111-8111-111111111111', id, 'Cafe', '', true, 'CHI', '0', '2026-03-01T00:00:00Z'];
    const rows = fromFinanceSnapshot({ ...snapshot, edits: [EDIT_HEADERS, edit] });
    assert.equal(rows.every((t) => !t.possibleDuplicate && t.possibleDuplicateCount === 0), true);
    assert.equal(buildDashboard(rows, parseFinanceQuery({ month: '2026-02' }, true)).possibleDuplicateCount, 0);
  }
  const rows = fromFinanceSnapshot(financeFixture());
  const candidates = rows.filter((t) => t.possibleDuplicate);
  assert.equal(candidates.length, 2);
  assert.equal(candidates.every((t) => !t.excludedFromReport && t.possibleDuplicateCount === 1), true);
  assert.equal(buildDashboard(rows, parseFinanceQuery({ month: '2026-02' }, true)).possibleDuplicateCount, 2);
});
test('report dimensions reconcile to eligible totals; real empty sources produce empty series', async () => {
  const snapshot = financeFixture();
  snapshot.bank.push(['01/03/2026 00:01', 'VCB', 'CHI', 42000, '', '', 'Synthetic next month', 'Ăn uống', '3'.repeat(64)]);
  const rows = fromFinanceSnapshot(snapshot);
  const q = parseFinanceQuery({ from: '2026-02-28', to: '2026-03-01' }, true);
  const dashboard = buildDashboard(filterTransactions(rows, q), q);
  for (const groups of [dashboard.dailySummary, dashboard.bankSummary, dashboard.sourceSummary, dashboard.categorySummary]) {
    assert.equal(groups.reduce((sum, row) => sum + row.income, 0), dashboard.income);
    assert.equal(groups.reduce((sum, row) => sum + row.expense, 0), dashboard.expense);
    assert.equal(groups.reduce((sum, row) => sum + row.net, 0), dashboard.net);
  }
  assert.deepEqual(dashboard.dailySummary.map((row) => row.name), ['2026-02-28', '2026-03-01']);
  assert.equal(dashboard.dailySummary[1].expense, 42000);
  const empty = buildDashboard([], q);
  assert.equal(empty.net, 0);
  assert.deepEqual(empty.dailySummary, []);
  assert.deepEqual(empty.bankSummary, []);
  const denied = await readFinance({ method: 'GET', headers: { 'x-webhook-secret': SYNTHETIC_SECRET } }, testEnvironment(),
    async () => ({ readFinance: async () => { throw new Error('private upstream detail'); } }), 'dashboard');
  assert.equal(denied.httpStatus, 503);
  assert.deepEqual(denied.body, { error: 'FINANCE_READ_UNAVAILABLE' });
});

test('date periods include leap days and use Vietnam timezone for the default current month', () => {
  const current = parseFinanceQuery({}, true, new Date('2026-01-31T18:00:00Z'));
  assert.equal(current.from, '2026-02-01'); assert.equal(current.to, '2026-02-28');
  assert.equal(parseFinanceQuery({ year: '2024', month: '2' }, true).to, '2024-02-29');
  assert.equal(parseFinanceQuery({ year: '2026' }, true).to, '2026-12-31');
  assert.equal(parseFinanceQuery({ from: '2025-04-23', to: '2026-10-24' }, true).from, '2025-04-23');
  for (const query of [{ month: '13', year: '2026' }, { month: '2' }, { year: '2026', from: '2026-01-01' },
    { from: '2026-02-30', to: '2026-03-01' }, { from: '2026-03-01', to: '2026-01-01' }, { from: '2026-01-01' }]) {
    assert.throws(() => parseFinanceQuery(query, true), /INVALID_QUERY/);
  }
});
test('all transaction filters are enforced and search ignores accents', () => {
  const rows = fromFinanceSnapshot(financeFixture());
  for (const [query, expected] of [
    [{ bank: 'VCB' }, 1], [{ source: 'MONEYLOVER' }, 4], [{ wallet: 'Wallet A' }, 3],
    [{ type: 'CHI', category: 'Cafe' }, 2], [{ q: 'luong' }, 1], [{ from: '2026-03-01' }, 0],
    [{ to: '2026-02-27' }, 0], [{ from: '2026-02-28', to: '2026-02-28' }, 6],
  ] as [Record<string, string>, number][]) assert.equal(filterTransactions(rows, parseFinanceQuery(query)).length, expected);
  for (const query of [{ limit: '0' }, { limit: '101' }, { limit: '-1' }, { source: 'RAW_CAPTURE' },
    { bank: 'UNKNOWN' }, { type: 'both' }, { range: 'Raw_Capture!A:Z' }, { from: ['2026-01-01'] }]) {
    assert.throws(() => parseFinanceQuery(query), /INVALID_QUERY/);
  }
});
test('id filter returns exactly that transaction so an edit can start from its latest revision', () => {
  const rows = fromFinanceSnapshot(financeFixture());
  const target = rows[2];
  assert.deepEqual(filterTransactions(rows, parseFinanceQuery({ id: target.id })).map((t) => t.id), [target.id]);
  assert.equal(filterTransactions(rows, parseFinanceQuery({ id: 'unknown-id' })).length, 0);
  assert.throws(() => parseFinanceQuery({ id: 'x'.repeat(201) }), /INVALID_QUERY/);
  assert.throws(() => parseFinanceQuery({ id: target.id }, true), /INVALID_QUERY/);
});
test('signed pagination yields bounded pages without repeats; forged, filter-changed and stale cursors are rejected', () => {
  const rows = fromFinanceSnapshot(financeFixture()), q = parseFinanceQuery({ limit: '2' });
  const first = paginateTransactions(rows, q, SYNTHETIC_SECRET);
  const second = paginateTransactions(rows, { ...q, cursor: first.nextCursor! }, SYNTHETIC_SECRET);
  const third = paginateTransactions(rows, { ...q, cursor: second.nextCursor! }, SYNTHETIC_SECRET);
  assert.equal(first.transactions.length, 2); assert.equal(third.nextCursor, null);
  assert.equal(new Set([...first.transactions, ...second.transactions, ...third.transactions].map((t) => t.id)).size, 6);
  assert.throws(() => paginateTransactions(rows, { ...q, cursor: 'forged' }, SYNTHETIC_SECRET), /INVALID_CURSOR/);
  assert.throws(() => paginateTransactions(rows, { ...q, cursor: first.nextCursor!, type: 'THU' }, SYNTHETIC_SECRET), /INVALID_CURSOR/);
  assert.throws(() => paginateTransactions(rows.slice(1), { ...q, cursor: first.nextCursor! }, SYNTHETIC_SECRET), /CURSOR_STALE/);
});
test('malformed rows, duplicate IDs and schema drift fail closed; empty valid sheets are distinct from unavailable data', () => {
  const empty = fromFinanceSnapshot({ bank: [TRANSACTION_HEADERS], history: [HISTORY_HEADERS] });
  assert.equal(buildDashboard(empty, { limit: 50 }).income, 0);
  for (const change of [
    (s: ReturnType<typeof financeFixture>) => { s.history[0] = []; },
    (s: ReturnType<typeof financeFixture>) => { s.bank[1][0] = '30/02/2026 01:00'; },
    (s: ReturnType<typeof financeFixture>) => { s.bank[1][3] = '12500'; },
    (s: ReturnType<typeof financeFixture>) => { s.history[1][5] = ''; },
    (s: ReturnType<typeof financeFixture>) => { s.bank.push([...s.bank[1]]); },
  ]) { const snapshot = financeFixture(); change(snapshot); assert.throws(() => fromFinanceSnapshot(snapshot)); }
});
test('both read APIs require existing webhook authentication before invoking Sheets and reject query secrets', async () => {
  for (const endpoint of ['dashboard', 'transactions'] as const) {
    let calls = 0;
    const factory = async () => { calls++; return { readFinance: async () => financeFixture() }; };
    for (const headers of [{}, { 'x-webhook-secret': 'wrong' }, { 'x-webhook-secret': [SYNTHETIC_SECRET] }]) {
      const response = await readFinance({ method: 'GET', headers }, testEnvironment(), factory, endpoint);
      assert.equal(response.httpStatus, 401);
    }
    assert.equal(calls, 0);
    assert.equal((await readFinance({ method: 'GET', headers: { 'x-webhook-secret': SYNTHETIC_SECRET },
      query: { secret: SYNTHETIC_SECRET } }, testEnvironment(), factory, endpoint)).httpStatus, 400);
    assert.equal(calls, 0);
    assert.equal((await readFinance({ method: 'POST' }, testEnvironment(), factory, endpoint)).httpStatus, 405);
    assert.equal((await readFinance({ method: 'GET' }, testEnvironment({ WEBHOOK_SECRET: undefined }), factory, endpoint)).httpStatus, 503);
    const success = await readFinance({ method: 'GET', headers: { 'x-webhook-secret': SYNTHETIC_SECRET },
      query: endpoint === 'dashboard' ? { month: '2026-02' } : {} }, testEnvironment(), factory, endpoint);
    assert.equal(success.httpStatus, 200); assert.equal(calls, 1);
    const json = JSON.stringify(success.body);
    for (const privateValue of ['SPREADSHEET_ID', 'Raw_Capture', 'Member A', SYNTHETIC_SECRET, 'PRIVATE KEY']) assert.equal(json.includes(privateValue), false);
    const failed = await readFinance({ method: 'GET', headers: { 'x-webhook-secret': SYNTHETIC_SECRET } }, testEnvironment(),
      async () => { throw new Error('sensitive-upstream-details'); }, endpoint);
    assert.deepEqual(failed.body, { error: 'FINANCE_READ_UNAVAILABLE' });
  }
});
test('adapters forbid private-data caching on success and errors', async () => {
  for (const endpoint of ['dashboard', 'transactions'] as const) {
    const headers: Record<string, string> = {}; let status = 0;
    const response = { setHeader: (k: string, v: string) => { headers[k] = v; }, status: (code: number) => {
      status = code; return { json: (body: Record<string, unknown>) => body }; } };
    await handleFinanceRead({ method: 'POST' }, response, endpoint);
    assert.equal(status, 405); assert.match(headers['Cache-Control'], /no-store/);
    assert.equal(headers['Vercel-CDN-Cache-Control'], 'no-store'); assert.equal(headers.Allow, 'GET');
  }
});
