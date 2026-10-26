import test from 'node:test';
import assert from 'node:assert/strict';
import { planLoanImport } from '../lib/loan-import.js';
import { LOAN_HEADERS, DISBURSEMENT_HEADERS, PAYMENT_HEADERS, INTEREST_RULE } from '../lib/interest.js';
import type { InterestSnapshot } from '../lib/interest.js';

const serial = (date: string) => Date.parse(date + 'T00:00:00Z') / 86400000 + 25569;
function snapshot(): InterestSnapshot {
  const config: unknown[][] = Array.from({ length: 12 }, () => []);
  config[1] = ['', serial('2024-04-26')]; config[11] = ['', INTEREST_RULE];
  return { loans: [LOAN_HEADERS, ['LN-A', 'Synthetic borrower', 12000000, 0.12, serial('2024-01-08'), '']],
    disbursements: [DISBURSEMENT_HEADERS, ['D1', 'LN-A', 3000000, 0.12, serial('2024-01-08'), ''], ['D2', 'LN-A', 9000000, 0.06, serial('2024-02-25'), '']],
    payments: [PAYMENT_HEADERS, [serial('2024-03-29'), 'LN-A', 'Thu gốc', '', 1000000, 'Synthetic'],
      [serial('2024-03-29'), 'LN-A', 'Thu lãi', '', 50000, 'Synthetic'], [serial('2024-03-29'), 'LN-A', 'Thu lãi', '', 50000, 'Synthetic']], config };
}
test('native loan mapping is repeatable, preserves duplicate receipts and per-tranche rates without invented history', () => {
  const source = snapshot(), before = structuredClone(source), first = planLoanImport(source, 'a'.repeat(64));
  const second = planLoanImport(source, 'a'.repeat(64), first);
  assert.deepEqual(first, second);
  assert.deepEqual(source, before);
  assert.equal(first.loans.length, 1);
  assert.equal(first.loans[0].value.interestRateAnnual, null); // Mixed rates, never invent one aggregate rate.
  assert.equal(first.events.length, 5);
  assert.equal(new Set(first.events.map((e) => e.id)).size, 5);
  assert.equal(first.events.filter((e) => e.value.eventType === 'INTEREST_PAYMENT').length, 2);
  assert.deepEqual(first.unresolved, []);
});
test('changed or deleted legacy rows block re-import; valid appends retain earlier event IDs', () => {
  const source = snapshot(), first = planLoanImport(source, 'a'.repeat(64));
  const changed = snapshot(); changed.payments[2][4] = 51000;
  assert.throws(() => planLoanImport(changed, 'a'.repeat(64), first), /SOURCE_CHANGED/);
  const removed = snapshot(); removed.payments.pop();
  assert.throws(() => planLoanImport(removed, 'a'.repeat(64), first), /SOURCE_CHANGED/);
  source.payments.push([serial('2024-04-01'), 'LN-A', 'Thu lãi', '', 10000, 'Synthetic append']);
  const appended = planLoanImport(source, 'a'.repeat(64), first);
  assert.deepEqual(appended.events.slice(0, 5).map((e) => e.id), first.events.map((e) => e.id));
  const missing = snapshot(); missing.disbursements.pop();
  assert.throws(() => planLoanImport(missing, 'a'.repeat(64)), /PRINCIPAL_MISMATCH/);
  assert.throws(() => planLoanImport(snapshot(), 'b'.repeat(64), first), /MANIFEST_MISMATCH/);
});
