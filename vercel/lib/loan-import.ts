import { createHash } from 'node:crypto';
import { buildInterest, parseInterestSnapshot } from './interest.js';
import type { InterestSnapshot } from './interest.js';
import { maskSensitiveIdentifiers } from '../sensitive-identifiers.js';

const hash = (value: unknown) => createHash('sha256').update(JSON.stringify(value)).digest('hex');
export type ImportRecord = { id: string; recordHash: string; value: Record<string, unknown> };
export type LoanImportPlan = { format: 'FINANCE_LOAN_SOURCE_V1'; rule: string; sourceKey: string; fingerprint: string; loans: ImportRecord[]; events: ImportRecord[]; unresolved: string[] };

// The native source is already the operational ledger. Normalize and reconcile it;
// never append an inferred history or re-import it into the same book.
export function planLoanImport(snapshot: InterestSnapshot, sourceKey: string, previous?: LoanImportPlan): LoanImportPlan {
  if (!/^[a-f0-9]{64}$/.test(sourceKey)) throw Error('INVALID_SOURCE_KEY');
  const { data, asOf } = parseInterestSnapshot(snapshot), dashboard = buildInterest(data, asOf);
  buildInterest(data, [asOf, ...data.payments.map((p) => p.date), ...data.disbursements.map((t) => t.date)].sort().at(-1)!);
  const loans = data.loans.map((loan) => {
    const tranches = data.disbursements.filter((t) => t.loanId === loan.id);
    const row = snapshot.loans.find((r) => r[0] === loan.id)!;
    const value = { loanId: loan.id, borrowerName: loan.name, interestRule: dashboard.rule,
      startDate: tranches.map((t) => t.date).sort()[0], maturityDate: tranches.every((t) => t.maturity) ? tranches.map((t) => t.maturity!).sort().at(-1) : null,
      interestRateAnnual: tranches.every((t) => t.annualRate === tranches[0].annualRate) ? tranches[0].annualRate : null,
      note: maskSensitiveIdentifiers(String(row[14] ?? '')), status: dashboard.loans.find((l) => l.id === loan.id)!.status };
    return { id: loan.id, value, recordHash: hash({ inputs: row.slice(0, 6), note: row[14] ?? '' }) };
  });
  const events: ImportRecord[] = data.disbursements.map((tranche) => {
    const rowIndex = snapshot.disbursements.findIndex((r) => r[0] === tranche.id);
    const id = hash([sourceKey, 'DISBURSEMENT', tranche.id]);
    const value = { eventId: id, loanId: tranche.loanId, eventType: 'DISBURSEMENT', eventDate: tranche.date,
      amount: tranche.principal, annualRate: tranche.annualRate, maturity: tranche.maturity,
      provenance: { sheet: 'Đợt giải ngân', row: rowIndex + 1, sourceId: tranche.id } };
    return { id, value, recordHash: hash(snapshot.disbursements[rowIndex].slice(0, 6)) };
  });
  for (const payment of data.payments) {
    const row = snapshot.payments[payment.row - 1];
    const id = hash([sourceKey, 'PAYMENT_ROW', payment.loanId, payment.row]);
    events.push({ id, recordHash: hash(row.slice(0, 6)), value: { eventId: id, loanId: payment.loanId,
      eventType: payment.type === 'Thu gốc' ? 'PRINCIPAL_PAYMENT' : 'INTEREST_PAYMENT', eventDate: payment.date,
      amount: payment.amount, note: maskSensitiveIdentifiers(String(row[5] ?? '')),
      provenance: { sheet: 'Giao dịch', row: payment.row, identityConvention: 'IMMUTABLE_LEGACY_ROW' } } });
  }
  const plan: LoanImportPlan = { format: 'FINANCE_LOAN_SOURCE_V1', rule: dashboard.rule, sourceKey,
    fingerprint: hash({ loans: loans.map((r) => [r.id, r.recordHash]), events: events.map((r) => [r.id, r.recordHash]) }), loans, events, unresolved: [] };
  if (previous) {
    if (previous.format !== plan.format || previous.sourceKey !== sourceKey || previous.rule !== plan.rule
      || !Array.isArray(previous.loans) || !Array.isArray(previous.events)) throw Error('IMPORT_MANIFEST_MISMATCH');
    for (const [older, current] of [[previous.loans, loans], [previous.events, events]]) {
      for (const record of older) if (!current.some((r) => r.id === record.id && r.recordHash === record.recordHash)) throw Error('IMPORT_SOURCE_CHANGED');
    }
  }
  return plan;
}
