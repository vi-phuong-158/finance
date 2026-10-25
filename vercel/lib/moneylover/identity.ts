import { createHash } from 'node:crypto';
import type { MoneyLoverTransaction } from './schema.js';

export function canonicalContent(t: Omit<MoneyLoverTransaction, 'legacyId'>): string {
  // Export ordinal is provenance only. Reordering or renumbering does not change identity.
  return JSON.stringify([t.date, t.type, t.amount, t.currency, t.amountVnd, t.category, t.wallet,
    t.note, t.counterparty, t.event, t.excludedFromReport, t.member]
    .map((v) => typeof v === 'string' ? v.normalize('NFC').replace(/\r\n?/g, '\n') : v));
}
export function legacyIdentity(content: string, occurrence: number): string {
  if (!Number.isSafeInteger(occurrence) || occurrence < 1) throw new Error('INVALID_OCCURRENCE');
  return `ml1_${createHash('sha256').update(`${content}\n${occurrence}`, 'utf8').digest('hex')}`;
}
