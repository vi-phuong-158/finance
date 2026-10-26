import assert from 'node:assert/strict';
import test from 'node:test';
import { migrateTransactions, planTransactionsMigration } from '../lib/transactions-migration.js';
import { TRANSACTION_HEADERS, TRANSACTION_HEADERS_V2 } from '../lib/sheet-schema.js';

test('V2 migration defaults to dry-run, backs up before one atomic header update and preserves A:I', async () => {
  const table = { sheetId: 10, columns: 26, rows: [[...TRANSACTION_HEADERS], ['Synthetic original transaction']] };
  const order: string[] = [];
  const port = { read: async () => structuredClone(table), write: async (requests: Record<string, unknown>[]) => {
    order.push('write'); assert.equal(requests.length, 1); table.rows[0] = [...TRANSACTION_HEADERS_V2];
  } };
  assert.equal((await migrateTransactions(port)).status, 'CHANGES_REQUIRED');
  assert.equal(order.length, 0);
  assert.equal((await migrateTransactions(port, { apply: true, backup: async (snapshot) => { order.push('backup'); assert.deepEqual(snapshot, table); } })).status, 'MIGRATED');
  assert.deepEqual(order, ['backup', 'write']);
  assert.equal((await migrateTransactions(port, { apply: true })).status, 'ALREADY_READY');
  assert.deepEqual(table.rows[1], ['Synthetic original transaction']);
});

test('migration refuses missing backups, changed snapshots, unknown headers and unsafe rollback', async () => {
  const table = { sheetId: 10, columns: 9, rows: [[...TRANSACTION_HEADERS]] };
  let reads = 0, writes = 0;
  const port = { read: async () => { reads++; return { ...table, columns: reads > 1 ? 12 : 9 }; }, write: async () => { writes++; } };
  await assert.rejects(() => migrateTransactions(port, { apply: true }), /BACKUP_REQUIRED/);
  reads = 0;
  await assert.rejects(() => migrateTransactions(port, { apply: true, backup: async () => {} }), /SOURCE_CHANGED/);
  assert.equal(writes, 0);
  assert.equal(planTransactionsMigration(table).length, 2);
  assert.throws(() => planTransactionsMigration({ ...table, rows: [['unknown']] }), /SCHEMA_MISMATCH/);
  assert.throws(() => planTransactionsMigration({ ...table, rows: [TRANSACTION_HEADERS_V2, ['', '', '', '', '', '', '', '', '', 'MANUAL', 'CASH']] }, true), /ROLLBACK_HAS_V2_DATA/);
  assert.equal(planTransactionsMigration({ ...table, columns: 26, rows: [TRANSACTION_HEADERS_V2] }, true).length, 1);
});
