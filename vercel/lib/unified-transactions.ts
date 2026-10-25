import { hasExpectedHeaders, TRANSACTION_HEADERS } from './sheet-schema.js';
import { canonicalBankCategory } from './categories.js';
import { HISTORY_HEADERS, minorUnits, validDate } from './moneylover/schema.js';
import { isExcludedHistoryRow, readHistoryRow } from './moneylover/history-reader.js';
import type { Bank } from './transaction-service.js';

export type UnifiedTransaction = {
  id: string; date: string; time: string | null; datePrecision: 'DAY' | 'MINUTE';
  type: 'THU' | 'CHI'; amount: number; currency: 'VND';
  category: string; originalCategory: string; accountOrWallet: string | null; bank: Bank | null;
  counterparty: string | null; description: string | null; event: string | null;
  source: 'BANK_NOTIFICATION' | 'MONEYLOVER'; excludedFromReport: boolean;
  possibleDuplicate: boolean; possibleDuplicateCount: number;
};
export type FinanceSnapshot = { bank: unknown[][]; history: unknown[][] };
export type FinanceReadPort = { readFinance: () => Promise<FinanceSnapshot> };
export function fromFinanceSnapshot(snapshot: FinanceSnapshot): UnifiedTransaction[] {
  if (!hasExpectedHeaders(snapshot.bank[0] ?? [], TRANSACTION_HEADERS)
    || !hasExpectedHeaders(snapshot.history[0] ?? [], HISTORY_HEADERS)) throw new Error('FINANCE_SCHEMA_MISMATCH');
  const rows: UnifiedTransaction[] = [];
  const text = (v: unknown): string => {
    if (v === null || v === undefined) return '';
    if (typeof v !== 'string') throw new Error('INVALID_FINANCE_ROW');
    return v;
  };
  const populated = (row: unknown[]) => row.some((v) => v !== '' && v !== null && v !== undefined);
  for (const row of snapshot.history.slice(1).filter(populated)) {
    const t = readHistoryRow(row);
    if (isExcludedHistoryRow(t)) continue;
    rows.push({ id: `moneylover:${t.legacyId}`, date: t.date, time: null, datePrecision: 'DAY', type: t.type,
      amount: t.amount, currency: 'VND', category: t.category, originalCategory: t.category,
      accountOrWallet: t.wallet || null, bank: null, counterparty: t.counterparty || null, description: t.note || null,
      event: t.event || null, source: 'MONEYLOVER', excludedFromReport: t.excludedFromReport || t.category === 'Chuyển nội bộ',
      possibleDuplicate: false, possibleDuplicateCount: 0 });
  }
  for (const row of snapshot.bank.slice(1).filter(populated)) {
    const [dateTime, bank, type, amount, , party, description, category, id] = row;
    const match = /^(\d{2})\/(\d{2})\/(\d{4}) (\d{2}):(\d{2})$/.exec(text(dateTime));
    if (!match || row.length > 9 || !['VCB', 'BIDV', 'VIETINBANK'].includes(text(bank))
      || (type !== 'THU' && type !== 'CHI') || typeof amount !== 'number' || !Number.isSafeInteger(amount) || amount <= 0
      || !/^[a-f0-9]{64}$/.test(text(id))) throw new Error('INVALID_FINANCE_ROW');
    minorUnits(amount);
    const date = `${match[3]}-${match[2]}-${match[1]}`, time = `${match[4]}:${match[5]}`;
    if (!validDate(date) || Number(match[4]) > 23 || Number(match[5]) > 59) throw new Error('INVALID_FINANCE_ROW');
    const canonical = canonicalBankCategory(text(category));
    rows.push({ id: `bank:${id}`, date, time: `${date}T${time}:00+07:00`, datePrecision: 'MINUTE', type,
      amount, currency: 'VND', category: canonical, originalCategory: text(category),
      accountOrWallet: text(bank), bank: bank as Bank, counterparty: text(party) || null,
      description: text(description) || null, event: null, source: 'BANK_NOTIFICATION', excludedFromReport: canonical === 'Chuyển nội bộ',
      possibleDuplicate: false, possibleDuplicateCount: 0 });
  }
  if (new Set(rows.map((t) => t.id)).size !== rows.length) throw new Error('DUPLICATE_FINANCE_ID');
  const groups = new Map<string, { bank: number; history: number }>();
  const key = (t: UnifiedTransaction) => `${t.date}|${t.type}|${t.amount}`;
  for (const t of rows) {
    const group = groups.get(key(t)) ?? { bank: 0, history: 0 };
    group[t.source === 'MONEYLOVER' ? 'history' : 'bank']++;
    groups.set(key(t), group);
  }
  for (const t of rows) {
    const group = groups.get(key(t));
    t.possibleDuplicateCount = group ? group[t.source === 'MONEYLOVER' ? 'bank' : 'history'] : 0;
    t.possibleDuplicate = t.possibleDuplicateCount > 0;
  }
  return rows.sort((a, b) => transactionKey(a) < transactionKey(b) ? 1 : transactionKey(a) > transactionKey(b) ? -1 : 0);
}
export function transactionKey(t: UnifiedTransaction): string { return `${t.date}|${t.time ?? ''}|${t.id}`; }
