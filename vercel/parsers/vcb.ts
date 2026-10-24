import { matchCategory } from '../categories.js';
import { maskSensitiveIdentifiers } from '../sensitive-identifiers.js';

export interface ParsedTransaction {
  time: string;
  bank: 'VCB' | 'VIETINBANK' | 'BIDV';
  type: 'THU' | 'CHI';
  amount: number;
  balance: number | '';
  counterparty: string;
  description: string;
  category: string;
}

function parsePositiveVnd(signedAmount: string): { type: 'THU' | 'CHI'; amount: number } | null {
  if (!/^[+-]?(?:\d+|\d{1,3}(?:[,.]\d{3})+)$/.test(signedAmount)) return null;

  const digits = signedAmount.replace(/[+,.\s-]/g, '');
  const amount = Number(digits);
  if (!Number.isSafeInteger(amount) || amount <= 0) return null;

  return { type: signedAmount.startsWith('-') ? 'CHI' : 'THU', amount };
}

function isValidDateTime(value: string): boolean {
  const match = value.match(/^(\d{2})-(\d{2})-(\d{4})\s+(\d{2}):(\d{2})(?::(\d{2}))?$/);
  if (!match) return false;

  const [, dayText, monthText, yearText, hourText, minuteText, secondText = '00'] = match;
  const day = Number(dayText);
  const month = Number(monthText);
  const year = Number(yearText);
  const hour = Number(hourText);
  const minute = Number(minuteText);
  const second = Number(secondText);
  const date = new Date(Date.UTC(year, month - 1, day));

  return date.getUTCFullYear() === year
    && date.getUTCMonth() === month - 1
    && date.getUTCDate() === day
    && hour <= 23
    && minute <= 59
    && second <= 59;
}

export function parseVcb(rawText: string): ParsedTransaction | null {
  const normalized = rawText.normalize('NFC').replace(/\s+/g, ' ').trim();

  // Mẫu giả lập đã ẩn danh:
  // "Số dư TK VCB ACCOUNT-TEST -10,000 VND lúc 01-01-2026 12:00:00. Số dư 50,000 VND. Ref MBVCB.TESTREF.TEST SUPERMARKET"
  const mainMatch = normalized.match(
    /Số dư TK(?:\s+VCB)?\s+\S+\s+([+-][\d,.]+)\s*VND\s+lúc\s+(\d{2}-\d{2}-\d{4}\s+\d{2}:\d{2}(?::\d{2})?)/i
  );
  if (!mainMatch) return null;

  const signedAmount = mainMatch[1];
  const parsedAmount = parsePositiveVnd(signedAmount);
  if (!parsedAmount) return null;
  const { type, amount } = parsedAmount;

  const rawTime = mainMatch[2];
  if (!isValidDateTime(rawTime)) return null;
  const time = rawTime.replace(/-/g, '/').slice(0, 16); // DD/MM/YYYY HH:mm

  const balanceMatch = normalized.match(/Số dư\s+([\d,.]+)\s*VND/i);
  const parsedBalance = balanceMatch ? parsePositiveVnd(balanceMatch[1]) : null;
  const balance = parsedBalance?.amount ?? '';

  const refMatch = normalized.match(/\bRef\s+(.*)$/i);
  const refSegment = refMatch ? refMatch[1].trim() : '';

  const accountSafeText = maskSensitiveIdentifiers(normalized);
  const safeRefSegment = maskSensitiveIdentifiers(refSegment);
  const description = refSegment ? safeRefSegment : accountSafeText;
  let counterparty = '';

  if (safeRefSegment) {
    const parts = safeRefSegment.split('.');
    counterparty = parts.length > 2 ? parts.slice(2).join('.').trim() : safeRefSegment;
  }

  const category = matchCategory(`${counterparty} ${description}`, type);

  return {
    time,
    bank: 'VCB',
    type,
    amount,
    balance,
    counterparty: counterparty || 'VCB Transaction',
    description,
    category,
  };
}

