import test from 'node:test';
import assert from 'node:assert/strict';
import { loanCommand, planLoanCommand, writeLoan } from '../lib/loan-write.js';
import type { LoanWriteSnapshot } from '../lib/loan-write.js';
import { LOAN_AUDIT_HEADERS } from '../lib/loan-audit-schema.js';
import { buildInterest, parseInterestSnapshot, LOAN_HEADERS, DISBURSEMENT_HEADERS, PAYMENT_HEADERS, INTEREST_RULE } from '../lib/interest.js';
import { SYNTHETIC_SECRET, testEnvironment } from './support.js';

const now = new Date('2024-04-26T04:00:00Z');
const serial = (s: string) => Date.parse(s + 'T00:00:00Z') / 86400000 + 25569;
function fixture(): LoanWriteSnapshot {
  const config: unknown[][] = Array.from({ length: 12 }, () => []);
  config[1] = ['Ngày xem', serial('2024-04-26')]; config[11] = ['Rule', INTEREST_RULE];
  return { interest: { loans: [LOAN_HEADERS, ['LN-A', 'Synthetic borrower', 12000000, 0.12, serial('2024-01-08'), '', '', '', '', '', '', '', '', '', 'Original note']],
    disbursements: [DISBURSEMENT_HEADERS, ['D-A', 'LN-A', 12000000, 0.12, serial('2024-01-08'), '']],
    payments: [PAYMENT_HEADERS], config }, audit: [LOAN_AUDIT_HEADERS],
    ids: { 'Khoản vay': 1, 'Đợt giải ngân': 2, 'Giao dịch': 3, Finance_App_Audit: 4 }, locale: 'en_US' };
}
const uuid = (n: number) => `${String(n).padStart(8, '0')}-1111-4111-8111-111111111111`;
const command = (operation: string, changes: Record<string, unknown> = {}) => ({ requestId: uuid(1), operation, loanId: 'LN-A', date: '2024-03-29', amount: 1000000, note: 'Synthetic', ...changes });
function server(initial = fixture()) {
  let state = structuredClone(initial);
  const markers = new Set<string>();
  let commits = 0;
  return {
    read: async () => structuredClone(state),
    async write(requests: Record<string, unknown>[]) {
      await Promise.resolve();
      const next = structuredClone(state), guards = new Set(markers);
      const tables: Record<number, unknown[][]> = { 1: next.interest.loans, 2: next.interest.disbursements, 3: next.interest.payments, 4: next.audit };
      for (const request of requests) {
        if (request.addNamedRange) {
          const id = (request.addNamedRange as { namedRange: { namedRangeId: string } }).namedRange.namedRangeId;
          if (guards.has(id)) throw new Error('DUPLICATE_GUARD');
          guards.add(id);
        } else {
          const operation = (request.updateCells ?? request.appendCells) as { sheetId?: number; start?: { sheetId: number; rowIndex: number; columnIndex: number }; rows: { values: { userEnteredValue: Record<string, unknown> }[] }[] };
          const table = tables[operation.start?.sheetId ?? operation.sheetId!];
          const values = operation.rows[0].values.map((cell) => Object.values(cell.userEnteredValue)[0]);
          if (operation.start) {
            const r = operation.start.rowIndex;
            table[r] ??= [];
            for (let i = 0; i < values.length; i++) table[r][operation.start.columnIndex + i] = values[i];
          } else table.push(values);
        }
      }
      state = next; guards.forEach((id) => markers.add(id)); commits++;
    },
    get commits() { return commits; },
  };
}
const env = () => testEnvironment({ INTEREST_SPREADSHEET_ID: 'synthetic-interest-ledger-001', LOAN_WRITES_ENABLED: '1' });
const request = (body: unknown) => ({ method: 'POST', headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' }, body });

test('loan create/disburse/principal/interest/update use native sources, preserve history, and replay safely', async () => {
  const port = server();
  const before = await port.read();
  const create = { requestId: uuid(2), operation: 'CREATE_LOAN', borrowerName: '=Synthetic borrower', amount: 5000000, annualRate: 0.12, date: '2024-02-25', maturity: '', note: 'Synthetic new loan' };
  const added = await writeLoan(request(create), env(), async () => port, now);
  assert.equal(added.httpStatus, 200);
  assert.equal(added.body.status, 'SAVED_LOAN_EVENT');
  assert.equal((await writeLoan(request(create), env(), async () => port, now)).body.status, 'DUPLICATE_IGNORED');
  assert.equal((await port.read()).interest.loans[1][1], before.interest.loans[1][1]);
  const newId = String(added.body.loanId);
  const disburse = command('DISBURSEMENT', { requestId: uuid(3), loanId: newId, amount: 1000000, annualRate: 0.06, maturity: '' });
  assert.equal((await writeLoan(request(disburse), env(), async () => port, now)).httpStatus, 200);
  assert.equal((await writeLoan(request(command('PRINCIPAL_PAYMENT', { requestId: uuid(4), loanId: newId })), env(), async () => port, now)).httpStatus, 200);
  const paid = command('INTEREST_PAYMENT', { requestId: uuid(5), loanId: newId, amount: 25000 });
  assert.equal((await writeLoan(request(paid), env(), async () => port, now)).httpStatus, 200);
  assert.equal((await writeLoan(request({ requestId: uuid(6), operation: 'UPDATE_INFO', loanId: newId, borrowerName: 'Updated synthetic borrower', note: 'Updated' }), env(), async () => port, now)).httpStatus, 200);
  const snapshot = await port.read(), data = parseInterestSnapshot(snapshot.interest).data;
  const result = buildInterest(data, '2024-04-26').loans.find((l) => l.id === newId)!;
  assert.equal(result.principal, 6000000);
  assert.equal(result.outstandingPrincipal, 5000000);
  assert.equal(result.paidInterest, 25000);
  assert.equal(result.currentInterest, 45000); // 4M at 12% and 1M at 6%, from next 25th.
  assert.equal(snapshot.audit.length, 6);
  assert.equal(snapshot.interest.loans[2][1], 'Updated synthetic borrower');
  assert.equal(snapshot.interest.payments.length, 3);
  assert.equal((await writeLoan(request({ ...paid, amount: 26000 }), env(), async () => port, now)).httpStatus, 409);
});

test('loan auth/feature/validation/simulation/schema gates fail before writes', async () => {
  const port = server(); let calls = 0;
  const factory = async () => { calls++; return port; };
  assert.equal((await writeLoan({ ...request(command('PRINCIPAL_PAYMENT')), headers: {} }, env(), factory, now)).httpStatus, 401);
  assert.equal((await writeLoan(request(command('PRINCIPAL_PAYMENT')), { ...env(), LOAN_WRITES_ENABLED: undefined }, factory, now)).body.error, 'LOAN_WRITES_DISABLED');
  assert.equal(calls, 0);
  for (const body of [command('PRINCIPAL_PAYMENT', { amount: 0 }), command('PRINCIPAL_PAYMENT', { date: '2024-02-30' }),
    command('DISBURSEMENT', { annualRate: 5.9, maturity: '' }), command('PRINCIPAL_PAYMENT', { extra: true })]) {
    assert.equal((await writeLoan(request(body), env(), factory, now)).httpStatus, 400);
  }
  assert.equal((await writeLoan(request(command('PRINCIPAL_PAYMENT', { amount: 13000000 })), env(), factory, now)).body.error, 'INTEREST_OVERPAYMENT');
  const simulation = fixture(); simulation.interest.config[2] = ['', serial('2024-04-26')];
  assert.throws(() => planLoanCommand(simulation, loanCommand(command('PRINCIPAL_PAYMENT')), now), /SIMULATION/);
  const missing = fixture(); missing.audit = [];
  assert.throws(() => planLoanCommand(missing, loanCommand(command('PRINCIPAL_PAYMENT')), now), /MIGRATION/);
  assert.equal(port.commits, 0);
});

test('two repayments based on one balance cannot both commit, and a lost response never duplicates a receipt', async () => {
  const port = server();
  const a = command('PRINCIPAL_PAYMENT', { requestId: uuid(7), amount: 7000000 });
  const b = command('PRINCIPAL_PAYMENT', { requestId: uuid(8), amount: 7000000 });
  const outcomes = await Promise.all([a, b].map((body) => writeLoan(request(body), env(), async () => port, now)));
  assert.equal(outcomes.filter((r) => r.httpStatus === 200).length, 1);
  assert.equal(port.commits, 1);
  const retry = await writeLoan(request(outcomes[0].httpStatus === 200 ? b : a), env(), async () => port, now);
  assert.equal(retry.body.error, 'INTEREST_OVERPAYMENT');
  const loss = server();
  const lossy = { read: loss.read, write: async (requests: Record<string, unknown>[]) => { await loss.write(requests); throw Error('timeout after commit'); } };
  assert.equal((await writeLoan(request(command('INTEREST_PAYMENT')), env(), async () => lossy, now)).body.status, 'DUPLICATE_IGNORED');
  assert.equal(loss.commits, 1);
});
