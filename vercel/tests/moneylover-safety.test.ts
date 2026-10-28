import assert from 'node:assert/strict';
import test from 'node:test';
import { parseMoneyLover } from '../lib/moneylover/parser.js';
import { canonicalContent, legacyIdentity } from '../lib/moneylover/identity.js';
import { summarizeSource, publicImportReport } from '../lib/moneylover/report.js';
import { importMoneyLover, planImport } from '../lib/moneylover/importer.js';
import type { HistorySnapshot, ImportPort } from '../lib/moneylover/importer.js';
import { reviewOverlaps } from '../lib/moneylover/overlap.js';
import { HISTORY_HEADERS, historyRow } from '../lib/moneylover/schema.js';
import { TRANSACTION_HEADERS } from '../lib/sheet-schema.js';
import { sourceRow, sourceTable } from './moneylover-fixtures.js';
import { fromFinanceSnapshot } from '../lib/unified-transactions.js';
import { buildDashboard } from '../lib/dashboard-service.js';
import { parseFinanceQuery } from '../lib/finance-query.js';
import { makeHistoryImportPort } from '../lib/moneylover/google-history.js';

function fixture() {
  const parsed = parseMoneyLover(sourceTable(sourceRow()));
  const bank = [TRANSACTION_HEADERS, ['28/02/2026 13:22', 'VCB', 'CHI', 12500, '', '', 'Synthetic', 'Cafe', '1'.repeat(64)]];
  const decisions = { version: 1, decisions: [{ historyId: parsed.transactions[0].legacyId,
    transactionIds: [`bank:${'1'.repeat(64)}`], action: 'EXCLUDE_HISTORY' }] };
  return { parsed, bank, decisions };
}
function memory(bank: unknown[][] = [TRANSACTION_HEADERS]) {
  let snapshot: HistorySnapshot = { exists: false, headers: [], rows: [] };
  let writes = 0;
  const port: ImportPort = { readTransactions: async () => structuredClone(bank), readHistory: async () => structuredClone(snapshot),
    appendHistory: async (rows, create) => {
      writes++; if (create) snapshot = { exists: true, headers: [...HISTORY_HEADERS], rows: [] };
      snapshot.rows.push(...structuredClone(rows));
    } };
  return { port, snapshot: () => snapshot, writes: () => writes };
}

test('history masks every free text field while keeping pre-mask multiset identity and rerun stability', async () => {
  const raw = sourceRow({ 2: 'Cafe 123456789012', 5: 'Wallet 123456789012', 6: 'TEST 123456789012',
    7: 'TEST 123456789012', 8: 'TEST 123456789012', 0: '123456789012', 10: 'TEST 123456789012' });
  const parsed = parseMoneyLover(sourceTable(raw, raw));
  assert.equal(parsed.transactions.length, 2);
  assert.equal(parsed.identicalRows, 1);
  assert.equal(JSON.stringify(parsed.transactions).includes('123456789012'), false);
  const original = { ...parsed.transactions[0], category: raw[2], wallet: raw[5], note: raw[6], counterparty: raw[7],
    event: raw[8], originalNo: raw[0], member: raw[10] } as typeof parsed.transactions[0];
  assert.equal(parsed.transactions[0].legacyId, legacyIdentity(canonicalContent(original), 1));
  const state = memory();
  await importMoneyLover(parsed, state.port, true);
  assert.equal((await importMoneyLover(parsed, state.port, true)).skipped, 2);
});

test('public reports allow only counts/statuses, including exclusions, cleanup and reconciliation failures', () => {
  const parsed = parseMoneyLover(sourceTable(sourceRow({ 4: 'JPY', 5: 'PRIVATE_WALLET' }), sourceRow({ 6: 'PRIVATE_NOTE' })));
  const report = publicImportReport({ mode: 'DRY_RUN', summary: summarizeSource(parsed), status: 'RECONCILIATION_MISMATCH',
    reconciliation: { matches: false, checks: [{ name: 'category:PRIVATE_CATEGORY', expected: 123, actual: 456 }] },
    cleanup: { removedRows: [{ amount: 999, category: 'PRIVATE_CATEGORY' }], removedCount: 1, status: 'DRY_RUN_CLEANUP' } });
  const text = JSON.stringify(report);
  for (const token of ['PRIVATE_', 'amount', 'expected', 'actual', 'wallet', 'categoryExpenseVnd', 'dateRange', 'removedRows']) assert.equal(text.includes(token), false);
  assert.equal(report.summary.sourceRows, 2);
  assert.equal(report.summary.excludedByReason.CURRENCY_NOT_VND, 1);
  assert.equal(report.reconciliation.failedCount, 1);
});

test('overlap without an exact decision blocks before writes; approved exclusion keeps history but counts one source', async () => {
  const { parsed, bank, decisions } = fixture();
  const state = memory(bank);
  const dry = await importMoneyLover(parsed, state.port);
  assert.equal(dry.overlap.unresolvedCount, 1); assert.equal(state.writes(), 0);
  await assert.rejects(importMoneyLover(parsed, state.port, true), /OVERLAP_REVIEW_REQUIRED/);
  assert.equal(state.writes(), 0);
  await importMoneyLover(parsed, state.port, true, decisions);
  assert.equal((await importMoneyLover(parsed, state.port, true, decisions)).status, 'ALREADY_IMPORTED');
  assert.equal(state.snapshot().rows.length, 1);
  const rows = fromFinanceSnapshot({ bank, history: [HISTORY_HEADERS, ...state.snapshot().rows] });
  assert.equal(rows.filter((r) => r.source === 'MONEYLOVER').length, 1);
  assert.equal(rows.find((r) => r.source === 'MONEYLOVER')!.excludedFromReport, true);
  assert.equal(buildDashboard(rows, parseFinanceQuery({ month: '2026-02' }, true)).expense, 12500);
});

test('overlap decisions reject stale candidates, duplicates and unknown fields; keep-both needs explicit approval', () => {
  const { parsed, bank, decisions } = fixture();
  const stale = structuredClone(decisions); stale.decisions[0].transactionIds = [`bank:${'2'.repeat(64)}`];
  assert.throws(() => reviewOverlaps(parsed, bank, stale), /INVALID_OVERLAP_DECISIONS/);
  assert.throws(() => reviewOverlaps(parsed, bank, { ...decisions, unknown: true }), /INVALID_OVERLAP_DECISIONS/);
  assert.throws(() => reviewOverlaps(parsed, bank, { version: 1, decisions: [...decisions.decisions, ...decisions.decisions] }), /INVALID_OVERLAP_DECISIONS/);
  const keep = structuredClone(decisions); keep.decisions[0].action = 'KEEP_BOTH';
  assert.equal(reviewOverlaps(parsed, bank, keep).parsed.transactions[0].excludedFromReport, false);
  assert.equal(reviewOverlaps(parsed, bank, keep).summary.unresolvedCount, 0);
  assert.equal(parsed.transactions[0].excludedFromReport, false);
});

test('preflight blocks combined row limit and oversized batches before any write', async () => {
  const { parsed } = fixture(), t = parsed.transactions[0];
  const rows = Array.from({ length: 50000 }, (_, i) => { const row = historyRow(t); row[0] = `ml1_${i.toString(16).padStart(64, '0')}`; return row; });
  assert.throws(() => planImport(parsed, { exists: true, headers: HISTORY_HEADERS, rows }), /HISTORY_SOURCE_LIMIT_EXCEEDED/);
  const large = parseMoneyLover(sourceTable(...Array.from({ length: 120 }, (_, i) => sourceRow({ 6: `${i}${'X'.repeat(19000)}` }))));
  const state = memory();
  assert.throws(() => planImport(large, { exists: false, headers: [], rows: [] }), /HISTORY_BATCH_TOO_LARGE/);
  assert.equal(state.writes(), 0);
});

test('all-excluded source still creates and verifies the header-only tab, then reruns without writes', async () => {
  const parsed = parseMoneyLover(sourceTable(sourceRow({ 5: 'Tiết Kiệm' }))), state = memory();
  assert.equal((await importMoneyLover(parsed, state.port)).create, true);
  assert.equal(state.writes(), 0);
  assert.equal((await importMoneyLover(parsed, state.port, true)).status, 'IMPORTED');
  assert.equal(state.snapshot().exists, true); assert.deepEqual(state.snapshot().headers, HISTORY_HEADERS);
  assert.equal((await importMoneyLover(parsed, state.port, true)).status, 'ALREADY_IMPORTED');
  assert.equal(state.writes(), 1);
});

test('bank changes before append invalidate overlap review and do not write history', async () => {
  const { parsed, bank, decisions } = fixture(), state = memory(bank); let reads = 0;
  state.port.readTransactions = async () => { reads++; return reads === 1 ? bank : [TRANSACTION_HEADERS]; };
  await assert.rejects(importMoneyLover(parsed, state.port, true, decisions), /TRANSACTIONS_CHANGED_RETRY_DRY_RUN/);
  assert.equal(state.writes(), 0);
});

test('default Google import port cannot write even if a caller invokes its mutation methods', async () => {
  let writes = 0;
  const port = await makeHistoryImportPort({ spreadsheetId: 'synthetic', clientEmail: 'synthetic', privateKey: 'synthetic' },
    { spreadsheets: { batchUpdate: async () => { writes++; } } });
  await assert.rejects(port.appendHistory([], true), /HISTORY_WRITE_DISABLED/);
  await assert.rejects(port.replaceHistory!([]), /HISTORY_WRITE_DISABLED/);
  assert.equal(writes, 0);
});
