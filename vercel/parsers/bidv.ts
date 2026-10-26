import type { ParsedTransaction } from './vcb.js';
import { bankTimestamp, parseVnd } from './bank-fields.js';
import { maskSensitiveIdentifiers } from '../sensitive-identifiers.js';
import { matchCategory } from '../categories.js';

export function parseBidv(rawText: string): ParsedTransaction | null {
  const text = rawText.normalize('NFC').replace(/\s+/gu, ' ').trim();
  if ((text.match(/Thời gian giao dịch:/gi) ?? []).length !== 1 || (text.match(/Số tiền GD:/gi) ?? []).length !== 1) return null;
  // Owner-confirmed envelope; unknown formats stay in masked Raw_Capture.
  const m = /^BIDV xin thông báo tới Quý khách Thời gian giao dịch: (\d{2}:\d{2}) (\d{2}\/\d{2}\/\d{4}) Tài khoản thanh toán: (?:\d+|\*+) Số tiền GD: ([+-][\d,.]+) VND Số dư cuối: ([\d,.-]+|\*+) VND Nội dung giao dịch: (.+) Mã giao dịch: ([A-Za-z0-9_-]+)$/i.exec(text);
  if (!m) return null;
  const time = bankTimestamp(m[2], m[1]), signed = parseVnd(m[3]);
  if (!time || signed === null || signed === 0) return null;
  const type = signed < 0 ? 'CHI' : 'THU';
  const description = maskSensitiveIdentifiers(`${m[5]} · Mã giao dịch: ${m[6]}`);
  return { time, bank: 'BIDV', type, amount: Math.abs(signed), balance: parseVnd(m[4]) ?? '',
    counterparty: '', description, category: matchCategory(description, type) };
}

