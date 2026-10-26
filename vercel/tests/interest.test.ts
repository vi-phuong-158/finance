import assert from 'node:assert/strict';
import test from 'node:test';
import { buildInterest, dateOnly, firstPeriod, periodStart, sheetDate, parseInterestSnapshot,
  LOAN_HEADERS, PAYMENT_HEADERS, DISBURSEMENT_HEADERS, INTEREST_RULE } from '../lib/interest.js';
import type { InterestData, InterestSnapshot } from '../lib/interest.js';
import { readInterest, handleInterestRead } from '../lib/interest-read-service.js';
import { interestReadRanges } from '../lib/google-interest-read.js';
import { trancheFormulas, loanFormulas } from '../lib/interest-sheet-formulas.js';
import { testEnvironment, SYNTHETIC_SECRET } from './support.js';

const serial = (s: string) => Date.parse(s + 'T00:00:00Z') / 86400000 + 25569;
function fixture(): InterestData {
  return { loans: [{ id: 'LN-A', name: 'Borrower fixture', principal: 12000000 }],
    disbursements: [
      { id: 'D1', loanId: 'LN-A', principal: 3000000, annualRate: 0.12, date: '2024-01-08', maturity: '2025-01-08' },
      { id: 'D2', loanId: 'LN-A', principal: 9000000, annualRate: 0.12, date: '2024-02-25', maturity: '2025-02-25' },
    ], payments: [
      { row: 3, loanId: 'LN-A', type: 'Thu gốc', amount: 1000000, date: '2024-03-29' },
      { row: 4, loanId: 'LN-A', type: 'Thu lãi', amount: 100000, date: '2024-03-29' },
      { row: 5, loanId: 'LN-A', type: 'Thu lãi', amount: 80000, date: '2024-03-29' },
    ] };
}
function snapshot(): InterestSnapshot {
  const data = fixture(), d = buildInterest(data, '2024-04-26').loans[0];
  const row: unknown[] = ['LN-A', 'Borrower fixture', 12000000, 0.12, serial('2024-01-08'), serial('2025-02-25'),
    d.currentInterest, d.completedPeriods, d.accruedInterest, d.paidInterest, d.interestDue,
    d.repaidPrincipal, d.outstandingPrincipal, d.status, '', d.collectedThisPeriod, d.advanceInterest, d.currentRemaining];
  const config: unknown[][] = Array.from({ length: 12 }, () => []);
  config[1] = ['Ngày tính lãi', serial('2024-04-26')]; config[11] = ['Phiên bản', INTEREST_RULE];
  return { loans: [LOAN_HEADERS, row], payments: [PAYMENT_HEADERS, ['', '', '', '', '', 'Instructions only'],
    ...data.payments.map((p) => [serial(p.date), p.loanId, p.type, '', p.amount, ''])],
  disbursements: [DISBURSEMENT_HEADERS, ...data.disbursements.map((t) => [t.id, t.loanId, t.principal, t.annualRate, serial(t.date), serial(t.maturity!)])], config };
}

test('25 boundaries, leap month and year transitions never count days', () => {
  assert.equal(periodStart('2024-02-24'), '2024-01-25');
  assert.equal(periodStart('2024-02-25'), '2024-02-25');
  assert.equal(firstPeriod('2024-01-26'), '2024-02-25');
  assert.equal(firstPeriod('2024-12-26'), '2025-01-25');
  assert.equal(firstPeriod('2024-02-25'), '2024-02-25');
  assert.equal(sheetDate(serial('2024-02-29')), '2024-02-29');
  for (const d of ['2024-02-30', '2024-13-01', '24-01-01']) assert.throws(() => dateOnly(d));
});
test('merged disbursements, mid-period principal payment, duplicate cash receipts and prior dates preserve history', () => {
  const data = fixture(), before = structuredClone(data);
  const apr24 = buildInterest(data, '2024-04-24').loans[0];
  assert.equal(apr24.accruedInterest, 150000); assert.equal(apr24.currentInterest, 120000);
  const apr25 = buildInterest(data, '2024-04-25').loans[0];
  assert.equal(apr25.accruedInterest, 270000); assert.equal(apr25.currentInterest, 110000);
  assert.equal(apr25.paidInterest, 180000); assert.equal(apr25.interestDue, 90000);
  assert.equal(apr25.outstandingPrincipal, 11000000); assert.equal(apr25.payments.length, 3);
  assert.equal(buildInterest(data, '2024-04-26').loans[0].accruedInterest, apr25.accruedInterest);
  assert.equal(buildInterest(data, '2024-03-28').loans[0].paidInterest, 0);
  assert.equal(buildInterest(data, '2024-01-01').totals.outstandingPrincipal, 0);
  assert.deepEqual(data, before);
});
test('boundary principal payment reduces the new period; early interest is an advance, not lost to MAX(0)', () => {
  const data = fixture(); data.payments[0].date = '2024-03-25';
  assert.equal(buildInterest(data, '2024-03-25').loans[0].currentInterest, 110000);
  data.payments.push({ row: 6, loanId: 'LN-A', type: 'Thu lãi', amount: 500000, date: '2024-04-01' });
  const d = buildInterest(data, '2024-04-26').loans[0];
  assert.equal(d.advanceInterest, d.paidInterest - d.accruedInterest);
  assert.equal(d.interestDue, 0); assert.equal(d.currentRemaining, 0);
  assert.equal(d.collectedThisPeriod, 0);
});
test('maturities keep separate cutoff dates and no partial final period or interest capitalization', () => {
  const data = fixture();
  const old = buildInterest(data, '2025-03-25');
  assert.equal(old.totals.currentInterest, 0);
  assert.equal(old.loans[0].completedPeriods, 12);
  assert.equal(buildInterest(data, '2025-04-24').totals.accruedInterest, old.totals.accruedInterest);
  data.payments.push({ row: 6, loanId: 'LN-A', type: 'Thu gốc', amount: 11000000, date: '2024-04-28' });
  const may = buildInterest(data, '2024-05-25').loans[0];
  assert.equal(may.currentInterest, 0); assert.equal(may.status, 'Đã tất toán');
  assert.equal(may.accruedInterest, 380000);
  data.payments[0].amount = 13000000;
  assert.throws(() => buildInterest(data, '2024-05-25'), /INTEREST_OVERPAYMENT/);
});
test('snapshot headers, invalid money/date, orphan and duplicate IDs fail closed', () => {
  const s = snapshot(); assert.equal(parseInterestSnapshot(s).data.payments.length, 3);
  for (const mutate of [
    (s: InterestSnapshot) => { s.config[11][1] = 'DAILY'; },
    (s: InterestSnapshot) => { s.loans[0] = []; },
    (s: InterestSnapshot) => { s.disbursements[1][3] = '#REF!'; },
    (s: InterestSnapshot) => { s.payments[2][4] = -5; },
    (s: InterestSnapshot) => { s.payments[2][0] = 0; },
    (s: InterestSnapshot) => { s.payments[2][1] = 'UNKNOWN'; },
    (s: InterestSnapshot) => { s.disbursements.push([...s.disbursements[1]]); },
  ]) { const value = snapshot(); mutate(value); assert.throws(() => parseInterestSnapshot(value)); }
});
test('interest API authenticates before Sheets, does not fall back to the bank spreadsheet and disallows client ranges', async () => {
  let calls = 0;
  const factory = async () => { calls++; return { readInterest: async () => snapshot() }; };
  const env = testEnvironment({ INTEREST_SPREADSHEET_ID: 'synthetic-interest-ledger-001' });
  const request = { method: 'GET', headers: { 'x-webhook-secret': SYNTHETIC_SECRET } };
  assert.equal((await readInterest({ ...request, method: 'POST' }, env, factory)).httpStatus, 405);
  assert.equal((await readInterest(request, testEnvironment(), factory)).httpStatus, 503);
  assert.equal((await readInterest({ ...request, headers: {} }, env, factory)).httpStatus, 401);
  assert.equal((await readInterest({ ...request, query: { range: 'Raw_Capture!A:Z' } }, env, factory)).httpStatus, 400);
  assert.equal((await readInterest({ ...request, query: { asOf: '2024-02-30' } }, env, factory)).httpStatus, 400);
  assert.equal(calls, 0);
  assert.equal((await readInterest(request, env, factory)).httpStatus, 200);
  assert.equal((await readInterest({ ...request, query: { asOf: '2024-03-28' } }, env, factory)).httpStatus, 200);
  const drift = async () => ({ readInterest: async () => { const s = snapshot(); s.loans[1][8] = 999999; return s; } });
  const bad = await readInterest(request, env, drift);
  assert.equal(bad.httpStatus, 503); assert.deepEqual(bad.body, { error: 'INTEREST_RECONCILIATION_REQUIRED' });
  const secretError = await readInterest(request, env, async () => { throw Error('private-upstream-details'); });
  assert.deepEqual(secretError.body, { error: 'INTEREST_READ_UNAVAILABLE' });
});
test('allowlisted bounded ranges reject source truncation; monthly formula templates are reproducible', () => {
  const tabs = [['Khoản vay', 300, 20], ['Giao dịch', 500, 6], ['Đợt giải ngân', 300, 12], ['Cấu hình', 20, 4]]
    .map(([title, rows, columns]) => ({ title: String(title), rows: Number(rows), columns: Number(columns), type: 'GRID' }));
  assert.deepEqual(interestReadRanges(tabs), ["'Khoản vay'!A1:T300", "'Giao dịch'!A1:F500", "'Đợt giải ngân'!A1:L300", "'Cấu hình'!A1:B20"]);
  assert.throws(() => interestReadRanges([{ ...tabs[0], rows: 301 }, ...tabs.slice(1)]), /LIMIT/);
  assert.throws(() => interestReadRanges(tabs.slice(1)), /SCHEMA/);
  assert.equal(trancheFormulas(2).length, 6); assert.equal(loanFormulas(2).length, 8);
  assert.equal([...trancheFormulas(2), ...loanFormulas(2)].some((f) => f.includes('/365')), false);
});
test('HTTP adapter marks financial responses private and uncached even for rejected requests', async () => {
  const headers: Record<string, string> = {};
  let status = 0;
  await handleInterestRead({ method: 'POST' }, { setHeader: (k, v) => { headers[k] = v; },
    status: (code) => { status = code; return { json: () => undefined }; } });
  assert.equal(status, 405); assert.equal(headers['Cache-Control'], 'no-store');
  assert.equal(headers['Vary'], 'X-Webhook-Secret'); assert.equal(headers.Allow, 'GET');
});
