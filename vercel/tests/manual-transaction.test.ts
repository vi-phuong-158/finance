import test from 'node:test';
import assert from 'node:assert/strict';
import { manualRow, writeManual } from '../lib/manual-transaction.js';
import { TRANSACTION_HEADERS, TRANSACTION_HEADERS_V2 } from '../lib/sheet-schema.js';
import { HISTORY_HEADERS } from '../lib/moneylover/schema.js';
import { fromFinanceSnapshot } from '../lib/unified-transactions.js';
import { buildDashboard } from '../lib/dashboard-service.js';
import { testEnvironment, SYNTHETIC_SECRET } from './support.js';

const draft = () => ({ requestId: '11111111-1111-4111-8111-111111111111', dateTime: '2026-02-28T10:22', type: 'CHI', amount: 12500, category: 'Ăn uống', account: 'CASH', note: '=SUM(A1) account 123456789012' });
const request = (body: unknown = draft()) => ({ method: 'POST', headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' }, body });

test('manual save → canonical Transactions → reports, without a separate financial ledger', async () => {
  const rows: unknown[][] = [[...TRANSACTION_HEADERS_V2]];
  const port = async () => ({ readTransactions: async () => rows, append: async (row: unknown[]) => { rows.push(row); } });
  assert.equal((await writeManual(request(), testEnvironment(), port)).body.status, 'SAVED_TRANSACTION');
  assert.equal(rows[1].length, 11);
  assert.equal(String(rows[1][6]).includes('123456789012'), false);
  const unified = fromFinanceSnapshot({ bank: rows, history: [HISTORY_HEADERS] });
  assert.equal(unified[0].source, 'MANUAL');
  assert.equal(unified[0].bank, null);
  assert.equal(unified[0].accountOrWallet, 'Tiền mặt');
  assert.equal(buildDashboard(unified, { limit: 50 }).expense, 12500);
  assert.equal((await writeManual(request(), testEnvironment(), port)).body.status, 'DUPLICATE_IGNORED');
  assert.equal(rows.length, 2);
  assert.equal((await writeManual(request({ ...draft(), amount: 13000 }), testEnvironment(), port)).httpStatus, 409);
  assert.equal(rows.length, 2);
});

test('lost response after manual append preserves the request identity on sequential retry', async () => {
  const rows: unknown[][] = [[...TRANSACTION_HEADERS_V2]];
  const port = async () => ({ readTransactions: async () => rows, append: async (row: unknown[]) => { rows.push(row); throw new Error('lost response'); } });
  assert.equal((await writeManual(request(), testEnvironment(), port)).httpStatus, 503);
  assert.equal((await writeManual(request(), testEnvironment(), port)).body.status, 'DUPLICATE_IGNORED');
  assert.equal(rows.length, 2);
});

test('manual authentication and validation fail before any Sheets access', async () => {
  let calls = 0;
  const port = async () => { calls++; throw new Error('must not be reached'); };
  assert.equal((await writeManual({ ...request(), headers: {} }, testEnvironment(), port)).httpStatus, 401);
  assert.equal((await writeManual({ ...request(), method: 'GET' }, testEnvironment(), port)).httpStatus, 405);
  for (const changes of [{ amount: 0 }, { amount: 1.2 }, { amount: 1e12 + 1 }, { dateTime: '2026-02-30T01:00' },
    { dateTime: '2026-02-28T24:00' }, { category: 'Lương' }, { account: 'OTHER' }, { note: 'x'.repeat(1025) },
    { requestId: 'different' }, { extra: 'field' }]) {
    assert.equal((await writeManual(request({ ...draft(), ...changes }), testEnvironment(), port)).httpStatus, 400);
  }
  assert.equal(calls, 0);
});

test('legacy schemas remain readable and manual writes wait for an explicit backed-up migration', async () => {
  let writes = 0;
  const port = async () => ({ readTransactions: async () => [TRANSACTION_HEADERS], append: async () => { writes++; } });
  assert.equal((await writeManual(request(), testEnvironment(), port)).body.error, 'MANUAL_SCHEMA_MIGRATION_REQUIRED');
  assert.equal(writes, 0);
  assert.deepEqual(fromFinanceSnapshot({ bank: [TRANSACTION_HEADERS], history: [HISTORY_HEADERS] }), []);
  assert.equal(manualRow({ ...draft(), type: 'THU', category: 'Lương', account: 'VCB' })[9], 'MANUAL');
});
