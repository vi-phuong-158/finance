import type { ParsedTransaction } from './vcb.js';
import { bankTimestamp, parseVnd } from './bank-fields.js';
import { maskSensitiveIdentifiers } from '../sensitive-identifiers.js';
import { matchCategory } from '../categories.js';

export function parseVietinbank(rawText: string): ParsedTransaction | null {
  const text = rawText.normalize('NFC').replace(/\s+/gu, ' ').trim();
  if ((text.match(/Thời gian:/gi) ?? []).length !== 1 || (text.match(/Giao dịch:/gi) ?? []).length !== 1) return null;
  const m = /^Thời gian: (\d{2}\/\d{2}\/\d{4}) (\d{2}:\d{2}) Tài khoản: (?:\d+|\*+) Giao dịch: ([+-][\d,.]+) VND Số dư hiện tại: ([\d,.-]+|\*+) VND Nội dung: (.+)$/i.exec(text);
  if (!m) return null;
  const time = bankTimestamp(m[1], m[2]), signed = parseVnd(m[3]);
  if (!time || signed === null || signed === 0) return null;
  const type = signed < 0 ? 'CHI' : 'THU';
  const description = maskSensitiveIdentifiers(m[5]);
  // Names embedded in transfer descriptions do not reliably identify the counterparty.
  return { time, bank: 'VIETINBANK', type, amount: Math.abs(signed), balance: parseVnd(m[4]) ?? '',
    counterparty: '', description, category: matchCategory(description, type) };
}

