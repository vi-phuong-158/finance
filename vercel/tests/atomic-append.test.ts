import test from 'node:test';
import assert from 'node:assert/strict';
import { atomicAppendRequests, guardedAppend } from '../lib/atomic-append.js';

test('atomic append combines a durable unique ID guard with literal cells in one batch', () => {
  const requests = atomicAppendRequests(42, 'a'.repeat(64), ['=NOT_A_FORMULA', 12500, '']);
  assert.equal(requests.length, 2);
  assert.equal(requests[0].addNamedRange?.namedRange.namedRangeId, `finance_${'a'.repeat(64)}`);
  assert.deepEqual(requests[1].appendCells?.rows[0].values[0], { userEnteredValue: { stringValue: '=NOT_A_FORMULA' } });
  assert.deepEqual(requests[1].appendCells?.rows[0].values[1], { userEnteredValue: { numberValue: 12500 } });
});

test('independent concurrent calls and a lost response acknowledge only one committed financial row', async () => {
  const rows: string[] = [], markers = new Set<string>();
  const atomicServer = async (id: string) => {
    await Promise.resolve();
    // Simulates the Sheets unique-ID constraint and atomic batch application, not a process mutex.
    if (markers.has(id)) throw new Error('DUPLICATE_NAMED_RANGE');
    markers.add(id); rows.push(id);
  };
  const results = await Promise.all(Array.from({ length: 20 }, () => guardedAppend(() => atomicServer('event'), async () => rows.includes('event'))));
  assert.equal(rows.length, 1);
  assert.equal(results.filter((r) => r === 'APPENDED').length, 1);
  assert.equal(results.filter((r) => r === 'DUPLICATE').length, 19);
  const lost = await guardedAppend(async () => { await atomicServer('lost'); throw new Error('timeout after commit'); }, async () => rows.includes('lost'));
  assert.equal(lost, 'DUPLICATE');
  await assert.rejects(() => guardedAppend(async () => { throw new Error('quota or orphan marker'); }, async () => false), /quota or orphan/);
  assert.equal(rows.length, 2);
});
