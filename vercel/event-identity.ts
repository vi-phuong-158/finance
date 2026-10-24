import { createHash } from 'node:crypto';

// Keep this whitespace set identical to EventIdentity.kt (ECMAScript whitespace).
export function normalizeTransactionText(value: string): string {
  return value.normalize('NFC').replace(/[\u0009-\u000d\u0020\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]+/g, ' ').trim();
}

export function createEventId(bank: string, postedAt: number, text: string): string {
  return createHash('sha256')
    .update(`${bank}:${postedAt}:${normalizeTransactionText(text)}`, 'utf8')
    .digest('hex');
}
