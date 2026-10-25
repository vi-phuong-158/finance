import { minorUnits, parseBoolean, validDate } from './schema.js';
import type { MoneyLoverTransaction } from './schema.js';

export function readHistoryRow(row: unknown[]): MoneyLoverTransaction {
  const [id, date, type, amount, currency, vnd, category, wallet, note, party, event, excluded, source, originalNo, member] = row;
  const text = (value: unknown) => {
    if (value === undefined || value === null) return '';
    if (typeof value !== 'string') throw new Error('INVALID_HISTORY_ROW');
    return value;
  };
  const money = (value: unknown) => {
    if (typeof value !== 'number' || value < 0) throw new Error('INVALID_HISTORY_ROW');
    minorUnits(value);
    return value;
  };
  if (row.length > 15 || typeof id !== 'string' || !/^ml1_[a-f0-9]{64}$/.test(id)
    || typeof date !== 'string' || !validDate(date) || (type !== 'THU' && type !== 'CHI')
    || typeof currency !== 'string' || !/^[A-Z]{3}$/.test(currency) || source !== 'MONEYLOVER') throw new Error('INVALID_HISTORY_ROW');
  const originalAmount = money(amount);
  if (originalAmount === 0 || (['VND', 'JPY'].includes(currency) && !Number.isInteger(originalAmount))) throw new Error('INVALID_HISTORY_ROW');
  const amountVnd = vnd === '' || vnd === undefined || vnd === null ? null : money(vnd);
  if (amountVnd !== null && !Number.isSafeInteger(amountVnd)) throw new Error('INVALID_HISTORY_ROW');
  if (currency === 'VND' && amountVnd !== originalAmount) throw new Error('INVALID_HISTORY_ROW');
  return { legacyId: id, date, type, amount: originalAmount, currency, amountVnd, category: text(category),
    wallet: text(wallet), note: text(note), counterparty: text(party), event: text(event),
    excludedFromReport: parseBoolean(excluded), source, originalNo: originalNo === undefined ? '' : String(originalNo), member: text(member) };
}
