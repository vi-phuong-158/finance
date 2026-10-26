import test from 'node:test';
import assert from 'node:assert/strict';
import { writeTransactionEdit } from '../lib/transaction-edits.js';
import { EDIT_HEADERS } from '../lib/edit-schema.js';
import { financeFixture } from './moneylover-fixtures.js';
import { fromFinanceSnapshot } from '../lib/unified-transactions.js';
import type { FinanceSnapshot } from '../lib/unified-transactions.js';
import { buildDashboard } from '../lib/dashboard-service.js';
import { SYNTHETIC_SECRET, testEnvironment } from './support.js';

const firstId = '11111111-1111-4111-8111-111111111111';
const secondId = '22222222-1111-4111-8111-111111111111';
const payload = (changes: Record<string, unknown> = {}) => ({ requestId: firstId, transactionId: `bank:${'1'.repeat(64)}`, category: 'Ăn uống', note: 'Synthetic note 123456789012', excluded: false, type: 'CHI', baseRevision: '0', ...changes });
const request = (body: unknown = payload()) => ({ method: 'POST', headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' }, body });
const env = () => testEnvironment({ TRANSACTION_EDITS_ENABLED: '1' });
function server() {
  const snapshot: FinanceSnapshot = { ...financeFixture(), edits: [[...EDIT_HEADERS]] };
  const guards = new Set<string>();
  return { snapshot, async read() { return structuredClone(snapshot); }, async append(row: unknown[], guard: string) {
    await Promise.resolve(); if (guards.has(guard)) throw Error('revision conflict');
    guards.add(guard); snapshot.edits!.push(row);
  } };
}

test('edits update read/report values while retaining immutable original rows and every audit revision', async () => {
  const port = server(), original = structuredClone(port.snapshot.bank);
  assert.equal((await writeTransactionEdit(request(), env(), async () => port)).body.status, 'SAVED_TRANSACTION_EDIT');
  assert.equal((await writeTransactionEdit(request(), env(), async () => port)).body.status, 'DUPLICATE_IGNORED');
  let tx = fromFinanceSnapshot(port.snapshot).find((t) => t.id === payload().transactionId)!;
  assert.equal(tx.category, 'Ăn uống'); assert.equal(tx.revision, firstId);
  assert.equal(tx.description!.includes('123456789012'), false);
  const next = payload({ requestId: secondId, baseRevision: firstId, excluded: true, note: 'Synthetic exclusion' });
  assert.equal((await writeTransactionEdit(request(next), env(), async () => port)).httpStatus, 200);
  tx = fromFinanceSnapshot(port.snapshot).find((t) => t.id === next.transactionId)!;
  assert.equal(tx.excludedFromReport, true);
  assert.equal(buildDashboard(fromFinanceSnapshot(port.snapshot), { limit: 50 }).expense, 12500);
  assert.deepEqual(port.snapshot.bank, original);
  assert.equal(port.snapshot.edits!.length, 3);
  assert.equal((await writeTransactionEdit(request(payload({ note: 'different' })), env(), async () => port)).httpStatus, 409);
  assert.equal((await writeTransactionEdit(request(payload({ requestId: '33333333-1111-4111-8111-111111111111' })), env(), async () => port)).body.error, 'EDIT_CONFLICT');
});

test('edit auth/feature/input gates and bad audit chains fail closed', async () => {
  const port = server(); let reads = 0;
  const factory = async () => { reads++; return port; };
  assert.equal((await writeTransactionEdit({ ...request(), headers: {} }, env(), factory)).httpStatus, 401);
  assert.equal((await writeTransactionEdit(request(), testEnvironment(), factory)).body.error, 'TRANSACTION_EDITS_DISABLED');
  for (const body of [payload({ category: 'unknown' }), payload({ type: 'THU' }), payload({ amount: 1 }), payload({ excluded: 'yes' })]) {
    assert.equal((await writeTransactionEdit(request(body), env(), factory)).httpStatus, 400);
  }
  assert.equal(reads, 0);
  port.snapshot.edits = [EDIT_HEADERS, [firstId, payload().transactionId, 'Ăn uống', '', false, 'CHI', 'bad', new Date().toISOString()]];
  assert.throws(() => fromFinanceSnapshot(port.snapshot), /INVALID_FINANCE_ROW/);
});

test('concurrent edit revisions have one winner; lost response acknowledges the stored matching revision', async () => {
  const port = server();
  const results = await Promise.all([payload(), payload({ requestId: secondId, category: 'Mua sắm' })]
    .map((body) => writeTransactionEdit(request(body), env(), async () => port)));
  assert.equal(results.filter((r) => r.httpStatus === 200).length, 1);
  assert.equal(port.snapshot.edits!.length, 2);
  const loss = server();
  const lossy = { read: loss.read.bind(loss), append: async (row: unknown[], guard: string) => { await loss.append(row, guard); throw Error('timeout'); } };
  assert.equal((await writeTransactionEdit(request(), env(), async () => lossy)).body.status, 'DUPLICATE_IGNORED');
  assert.equal(loss.snapshot.edits!.length, 2);
});
