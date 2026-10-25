import { canonicalContent, legacyIdentity } from './identity.js';
import { minorUnits, parseBoolean, validDate } from './schema.js';
import type { MoneyLoverTransaction } from './schema.js';
import { isNonConsumptionCategory } from '../categories.js';

export type SourceRow = { rowNumber: number; cells: unknown[] };
export type SourceTable = { rows: SourceRow[]; date1904?: boolean };
export type ExclusionReason = 'CURRENCY_NOT_VND' | 'NON_CONSUMPTION_CATEGORY';
export type ExcludedRow = {
  row: number;
  reason: ExclusionReason;
  category: string;
  currency: string;
  wallet: string;
  amount: number;
};
export type ParseResult = {
  transactions: MoneyLoverTransaction[];
  rejected: { row: number; code: string }[];
  excluded: ExcludedRow[];
  identicalRows: number;
};
const ALIASES: Record<string, string[]> = {
  date: ['Ngày', 'Date'], category: ['Nhóm', 'Category'], amount: ['Số tiền', 'Amount'],
  currency: ['Đơn vị tiền tệ', 'Tiền tệ', 'Currency'], wallet: ['Ví', 'Wallet'],
  note: ['Ghi chú', 'Note'], counterparty: ['Với', 'With'], event: ['Sự kiện', 'Event'],
  excluded: ['Không tính vào báo cáo', 'Excluded from report'], originalNo: ['No.', 'STT'], member: ['Thành viên', 'Member'],
};
const REQUIRED = ['date', 'category', 'amount', 'currency', 'wallet'];

function stringCell(value: unknown): string {
  if (value === null || value === undefined) return '';
  if (typeof value !== 'string' && typeof value !== 'number') throw new Error('INVALID_TEXT_CELL');
  const text = String(value);
  if (text.length > 20_000) throw new Error('TEXT_TOO_LONG');
  return text;
}
function sourceDate(value: unknown, date1904: boolean): string {
  if (typeof value === 'number') {
    // Whole serial days only: a fractional serial contains a time we must not discard.
    if (!Number.isInteger(value) || value < 0 || (!date1904 && value === 60)) throw new Error('INVALID_DATE');
    const days = date1904 ? value : value - (value > 60 ? 1 : 0);
    const ms = Date.UTC(date1904 ? 1904 : 1899, date1904 ? 0 : 11, date1904 ? 1 : 31) + days * 86_400_000;
    if (!Number.isFinite(ms) || Math.abs(ms) > 8.64e15) throw new Error('INVALID_DATE');
    value = new Date(ms).toISOString().slice(0, 10);
  }
  let s = stringCell(value).trim();
  const local = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(s);
  if (local) s = `${local[3]}-${local[2]}-${local[1]}`;
  if (!validDate(s)) throw new Error('INVALID_DATE');
  return s;
}

export function parseMoneyLover(table: SourceTable): ParseResult {
  const nonempty = table.rows.filter((r) => r.cells.some((v) => v !== null && v !== undefined && v !== ''));
  if (nonempty.length < 2) throw new Error('EMPTY_TRANSACTION_SHEET');
  const columns = new Map<string, number>();
  for (const [i, cell] of nonempty[0].cells.entries()) {
    const h = stringCell(cell).normalize('NFC').trim();
    if (!h) continue;
    const key = Object.keys(ALIASES).find((k) => ALIASES[k].includes(h));
    if (!key || columns.has(key)) throw new Error('INVALID_MONEYLOVER_HEADERS');
    columns.set(key, i);
  }
  if (REQUIRED.some((key) => !columns.has(key))) throw new Error('INVALID_MONEYLOVER_HEADERS');
  const result: ParseResult = { transactions: [], rejected: [], excluded: [], identicalRows: 0 };
  const occurrences = new Map<string, number>();
  for (const row of nonempty.slice(1)) {
    try {
      if (row.cells.some((v, i) => v !== '' && v !== null && v !== undefined && ![...columns.values()].includes(i))) {
        throw new Error('UNMAPPED_SOURCE_COLUMN');
      }
      const cell = (key: string) => columns.has(key) ? row.cells[columns.get(key)!] : undefined;
      const raw = cell('amount');
      if (typeof raw !== 'number' && (typeof raw !== 'string' || !/^[+-]?\d+(?:\.\d{1,2})?$/.test(raw.trim()))) {
        throw new Error('INVALID_AMOUNT');
      }
      const signed = Number(raw);
      if (signed === 0) throw new Error('ZERO_AMOUNT');
      minorUnits(signed);
      const currency = stringCell(cell('currency')).trim().toUpperCase();
      if (!/^[A-Z]{3}$/.test(currency)) throw new Error('INVALID_CURRENCY');
      if (currency === 'VND' && !Number.isSafeInteger(signed)) throw new Error('INVALID_MINOR_UNIT');
      const category = stringCell(cell('category'));
      const wallet = stringCell(cell('wallet'));

      if (currency !== 'VND') {
        result.excluded.push({
          row: row.rowNumber,
          reason: 'CURRENCY_NOT_VND',
          category,
          currency,
          wallet,
          amount: signed,
        });
        continue;
      }

      if (isNonConsumptionCategory(category)) {
        result.excluded.push({
          row: row.rowNumber,
          reason: 'NON_CONSUMPTION_CATEGORY',
          category,
          currency,
          wallet,
          amount: signed,
        });
        continue;
      }

      const t: Omit<MoneyLoverTransaction, 'legacyId'> = {
        date: sourceDate(cell('date'), table.date1904 ?? false), type: signed < 0 ? 'CHI' : 'THU',
        amount: Math.abs(signed), currency: 'VND', amountVnd: Math.abs(signed),
        category, wallet, note: stringCell(cell('note')),
        counterparty: stringCell(cell('counterparty')), event: stringCell(cell('event')),
        excludedFromReport: parseBoolean(cell('excluded')), source: 'MONEYLOVER',
        originalNo: stringCell(cell('originalNo')), member: stringCell(cell('member')),
      };
      const content = canonicalContent(t), occurrence = (occurrences.get(content) ?? 0) + 1;
      occurrences.set(content, occurrence);
      if (occurrence > 1) result.identicalRows++;
      result.transactions.push({ legacyId: legacyIdentity(content, occurrence), ...t });
    } catch (error) {
      result.rejected.push({ row: row.rowNumber, code: (error as Error).message });
    }
  }
  return result;
}
