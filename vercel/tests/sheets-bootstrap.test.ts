import assert from 'node:assert/strict';
import test from 'node:test';
import { bootstrapSheets, planSheetsBootstrap } from '../lib/sheets-bootstrap.js';
import type { SheetSnapshot } from '../lib/sheets-bootstrap.js';
import { RAW_CAPTURE_HEADERS, TRANSACTION_HEADERS } from '../lib/sheet-schema.js';

const snapshot = (title: string, rows: unknown[][] = [], id = 1): SheetSnapshot => ({ title, rows, id, type: 'GRID' });

test('plans both missing tabs with literal headers without deleting unrelated sheets', () => {
  const requests = planSheetsBootstrap([snapshot('Personal notes')]);
  assert.equal(requests.length, 4);
  assert.deepEqual(requests[0], { addSheet: { properties: { sheetId: 2, title: 'Transactions' } } });
  assert.ok(requests.every((request) => 'addSheet' in request || 'updateCells' in request));
});

test('fills completely empty target sheets but preserves populated compatible tables', () => {
  const sheets = [snapshot('Transactions', [TRANSACTION_HEADERS, ['synthetic', 'VCB', 'CHI', 1000]], 1), snapshot('Raw_Capture', [], 2)];
  const before = structuredClone(sheets);
  const plan = planSheetsBootstrap(sheets);
  assert.equal(plan.length, 1);
  assert.ok('updateCells' in plan[0]);
  assert.equal(plan[0].updateCells.start.sheetId, 2);
  assert.deepEqual(sheets, before);
});

test('is idempotent after bootstrap and never rewrites valid headers', () => {
  assert.deepEqual(planSheetsBootstrap([snapshot('Transactions', [TRANSACTION_HEADERS]), snapshot('Raw_Capture', [RAW_CAPTURE_HEADERS], 2)]), []);
});

test('fails closed on missing, reordered, formula, partial, and extra-column schemas', () => {
  for (const rows of [
    [[], ['data without headers']],
    [['bank', 'time']],
    [['=FORMULA()']],
    [['time']],
    [[...TRANSACTION_HEADERS, 'unexpected']],
    [TRANSACTION_HEADERS, [...Array(9).fill(''), 'unexpected data']],
  ]) assert.throws(() => planSheetsBootstrap([snapshot('Transactions', rows)]), /SHEET_SCHEMA_MISMATCH/);
  assert.throws(() => planSheetsBootstrap([{ ...snapshot('Transactions'), type: 'DATA_SOURCE' }]), /SHEET_SCHEMA_MISMATCH/);
});

test('preflights all sheets before any writes and defaults to read-only planning', async () => {
  let writes = 0;
  const port = { readSnapshot: async () => [], apply: async () => { writes++; } };
  assert.equal((await bootstrapSheets(port, false)).status, 'CHANGES_REQUIRED');
  assert.equal(writes, 0);
  assert.equal((await bootstrapSheets(port, true)).status, 'BOOTSTRAPPED');
  assert.equal(writes, 1);
  await assert.rejects(bootstrapSheets({ ...port, readSnapshot: async () => [snapshot('Raw_Capture', [['wrong']])] }, true));
  assert.equal(writes, 1);
});

test('missing/unauthorized spreadsheet and API failures never report success or write', async () => {
  let writes = 0;
  await assert.rejects(bootstrapSheets({ readSnapshot: async () => { throw new Error('not found'); }, apply: async () => { writes++; } }, true));
  assert.equal(writes, 0);
  await assert.rejects(bootstrapSheets({ readSnapshot: async () => [], apply: async () => { throw new Error('API rejected atomic batch'); } }, true));
});
