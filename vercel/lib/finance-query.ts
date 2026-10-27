import { createHash, createHmac, timingSafeEqual } from 'node:crypto';
import { normalizeCategoryText } from './categories.js';
import { validDate } from './moneylover/schema.js';
import { transactionKey } from './unified-transactions.js';
import type { UnifiedTransaction } from './unified-transactions.js';

export type FinanceQuery = { from?: string; to?: string; type?: string; category?: string; bank?: string; wallet?: string; source?: string; q?: string; id?: string; limit: number; cursor?: string };
export function parseFinanceQuery(input: Record<string, unknown>, dashboard = false, now = new Date()): FinanceQuery {
  // `id` lets the app fetch one transaction's latest revision before editing it.
  const allowed = new Set(['from', 'to', 'type', 'category', 'bank', 'wallet', 'source', 'q', ...(dashboard ? ['month', 'year'] : ['limit', 'cursor', 'id'])]);
  for (const [key, value] of Object.entries(input)) {
    if (!allowed.has(key) || typeof value !== 'string' || value.length > (key === 'cursor' ? 2048 : 200)) throw new Error('INVALID_QUERY');
  }
  const q = input as Record<string, string>;
  const result: FinanceQuery = { limit: 50 };
  for (const key of ['from', 'to'] as const) {
    if (q[key] !== undefined) { if (!validDate(q[key])) throw new Error('INVALID_QUERY'); result[key] = q[key]; }
  }
  if (dashboard) {
    if ((q.month !== undefined || q.year !== undefined) && (q.from !== undefined || q.to !== undefined)) throw new Error('INVALID_QUERY');
    let month = q.month, year = q.year;
    if (month !== undefined && /^\d{4}-\d{2}$/.test(month)) {
      if (year && year !== month.slice(0, 4)) throw new Error('INVALID_QUERY');
      year = month.slice(0, 4); month = month.slice(5);
    }
    if (year !== undefined && !/^(19\d{2}|[2-9]\d{3})$/.test(year)) throw new Error('INVALID_QUERY');
    if (month !== undefined && (!year || !/^(0?[1-9]|1[0-2])$/.test(month))) throw new Error('INVALID_QUERY');
    if (month && year) {
      const last = new Date(Date.UTC(Number(year), Number(month), 0)).getUTCDate();
      result.from = `${year}-${month.padStart(2, '0')}-01`; result.to = `${year}-${month.padStart(2, '0')}-${last}`;
    } else if (year) { result.from = `${year}-01-01`; result.to = `${year}-12-31`; }
    else if (!result.from && !result.to) {
      const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit' }).formatToParts(now);
      const y = parts.find((p) => p.type === 'year')!.value, m = parts.find((p) => p.type === 'month')!.value;
      result.from = `${y}-${m}-01`; result.to = `${y}-${m}-${new Date(Date.UTC(Number(y), Number(m), 0)).getUTCDate()}`;
    } else if (!result.from || !result.to) throw new Error('INVALID_QUERY');
  }
  if (result.from && result.to && result.from > result.to) throw new Error('INVALID_QUERY');
  const enums: Record<string, string[]> = { type: ['THU', 'CHI'], bank: ['VCB', 'BIDV', 'VIETINBANK'], source: ['MONEYLOVER', 'BANK_NOTIFICATION', 'MANUAL'] };
  for (const key of ['type', 'bank', 'source'] as const) {
    if (q[key] !== undefined) { if (!enums[key].includes(q[key])) throw new Error('INVALID_QUERY'); result[key] = q[key]; }
  }
  for (const key of ['category', 'wallet', 'q', 'id'] as const) if (q[key] !== undefined) result[key] = q[key];
  if (q.limit !== undefined) {
    if (!/^\d{1,3}$/.test(q.limit) || Number(q.limit) < 1 || Number(q.limit) > 100) throw new Error('INVALID_QUERY');
    result.limit = Number(q.limit);
  }
  if (q.cursor !== undefined) result.cursor = q.cursor;
  return result;
}
export function filterTransactions(rows: UnifiedTransaction[], q: FinanceQuery): UnifiedTransaction[] {
  return rows.filter((t) => (!q.id || t.id === q.id) && (!q.from || t.date >= q.from) && (!q.to || t.date <= q.to)
    && (!q.type || t.type === q.type) && (!q.category || t.category === q.category)
    && (!q.bank || t.bank === q.bank) && (!q.wallet || (t.source === 'MONEYLOVER' && t.accountOrWallet === q.wallet))
    && (!q.source || t.source === q.source) && (!q.q || normalizeCategoryText([t.description, t.counterparty, t.category, t.accountOrWallet, t.event].join(' ')).includes(normalizeCategoryText(q.q))));
}
export function paginateTransactions(rows: UnifiedTransaction[], query: FinanceQuery, secret: string) {
  const { cursor, ...scope } = query;
  const scopeHash = createHash('sha256').update(JSON.stringify(scope)).digest('hex');
  const snapshot = createHash('sha256').update(JSON.stringify(rows)).digest('hex');
  let start = 0;
  if (cursor) {
    try {
      const [payload, signature, extra] = cursor.split('.');
      const expected = createHmac('sha256', secret).update(payload).digest('base64url');
      if (extra !== undefined || !signature || signature.length !== expected.length || !timingSafeEqual(Buffer.from(signature), Buffer.from(expected))) throw new Error();
      const decoded = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
      if (decoded.scope !== scopeHash || typeof decoded.key !== 'string') throw new Error();
      if (decoded.snapshot !== snapshot) throw new Error('CURSOR_STALE');
      const index = rows.findIndex((t) => transactionKey(t) === decoded.key);
      if (index < 0) throw new Error();
      start = index + 1;
    } catch (error) { throw new Error((error as Error).message === 'CURSOR_STALE' ? 'CURSOR_STALE' : 'INVALID_CURSOR'); }
  }
  const transactions = rows.slice(start, start + query.limit);
  let nextCursor: string | null = null;
  if (start + query.limit < rows.length) {
    const payload = Buffer.from(JSON.stringify({ key: transactionKey(transactions.at(-1)!), scope: scopeHash, snapshot })).toString('base64url');
    nextCursor = `${payload}.${createHmac('sha256', secret).update(payload).digest('base64url')}`;
  }
  return { transactions, nextCursor, limit: query.limit, total: rows.length };
}
