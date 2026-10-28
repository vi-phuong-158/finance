import { maskSensitiveIdentifiers } from '../../sensitive-identifiers.js';

export const HISTORY_TAB = 'MoneyLover_History';
export const HISTORY_HEADERS = [
  'legacy_id', 'date', 'type', 'amount', 'currency', 'amount_vnd', 'category', 'wallet',
  'note', 'counterparty', 'event', 'excluded_from_report', 'source', 'original_no', 'member',
];
export type MoneyLoverTransaction = {
  legacyId: string;
  date: string;
  type: 'THU' | 'CHI';
  amount: number;
  currency: string;
  amountVnd: number | null;
  category: string;
  wallet: string;
  note: string;
  counterparty: string;
  event: string;
  excludedFromReport: boolean;
  source: 'MONEYLOVER';
  originalNo: string;
  member: string;
};

export function maskHistoryText(t: MoneyLoverTransaction): MoneyLoverTransaction {
  return { ...t, ...Object.fromEntries(
    (['category', 'wallet', 'note', 'counterparty', 'event', 'originalNo', 'member'] as const)
      .map((key) => [key, maskSensitiveIdentifiers(t[key])])) };
}

export function historyRow(transaction: MoneyLoverTransaction): (string | number | boolean)[] {
  const t = maskHistoryText(transaction);
  return [t.legacyId, t.date, t.type, t.amount, t.currency, t.amountVnd ?? '', t.category,
    t.wallet, t.note, t.counterparty, t.event, t.excludedFromReport, t.source, t.originalNo, t.member];
}

export function validDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value < '1900-01-01' || value > '9999-12-31') return false;
  const d = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(d.getTime()) && d.toISOString().slice(0, 10) === value;
}

export function parseBoolean(value: unknown): boolean {
  if (value === undefined || value === null || value === '') return false;
  if (value === true || value === 1) return true;
  if (value === false || value === 0) return false;
  if (typeof value === 'string') {
    const s = value.trim().normalize('NFC').toLowerCase();
    if (['true', '1', 'yes', 'có', 'x', '✅'].includes(s)) return true;
    if (['', 'false', '0', 'no', 'không'].includes(s)) return false;
  }
  throw new Error('INVALID_EXCLUDED_FLAG');
}

// Use decimal minor units while summing; never silently round financial data.
export function minorUnits(value: number): number {
  const scaled = value * 100;
  if (!Number.isFinite(value) || (value !== 0 && Math.round(scaled) === 0)
    || Math.abs(scaled - Math.round(scaled)) > Number.EPSILON * Math.max(1, Math.abs(scaled)) * 2
    || !Number.isSafeInteger(Math.round(scaled))) throw new Error('INVALID_AMOUNT');
  return Math.round(scaled);
}
export function sumMoney(values: number[]): number {
  let sum = 0;
  for (const value of values) {
    sum += minorUnits(value);
    if (!Number.isSafeInteger(sum)) throw new Error('TOTAL_OUT_OF_RANGE');
  }
  return sum / 100;
}
