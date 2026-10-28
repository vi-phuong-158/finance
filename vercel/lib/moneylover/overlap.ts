import { fromFinanceSnapshot } from '../unified-transactions.js';
import { HISTORY_HEADERS } from './schema.js';
import type { ParseResult } from './parser.js';

type Decision = { historyId: string; transactionIds: string[]; action: 'EXCLUDE_HISTORY' | 'KEEP_BOTH' };

// Candidates are not proof of duplication. Only exact owner-reviewed ID sets may change totals.
export function overlapCandidates(parsed: ParseResult, transactions: unknown[][]) {
  const rows = fromFinanceSnapshot({ bank: transactions, history: [HISTORY_HEADERS] });
  const groups = new Map<string, string[]>();
  for (const t of rows) {
    if (t.excludedFromReport) continue;
    const key = `${t.date}|${t.type}|${t.amount}`;
    const ids = groups.get(key) ?? []; ids.push(t.id); groups.set(key, ids);
  }
  return parsed.transactions.filter((t) => !t.excludedFromReport && t.category !== 'Chuyển nội bộ')
    .flatMap((t) => {
      const ids = groups.get(`${t.date}|${t.type}|${t.amount}`);
      return ids ? [{ historyId: t.legacyId, transactionIds: [...ids].sort() }] : [];
    });
}

export function reviewOverlaps(parsed: ParseResult, transactions: unknown[][], input?: unknown) {
  const candidates = overlapCandidates(parsed, transactions);
  const approved = new Map<string, Decision>();
  if (input !== undefined) {
    const data = input as { version?: unknown; decisions?: unknown } | null;
    if (!data || typeof data !== 'object' || Array.isArray(data) || data.version !== 1 || !Array.isArray(data.decisions)
      || Object.keys(data).some((k) => !['version', 'decisions'].includes(k))) throw new Error('INVALID_OVERLAP_DECISIONS');
    const byId = new Map(candidates.map((c) => [c.historyId, c.transactionIds]));
    for (const item of data.decisions) {
      const d = item as Decision | null;
      if (!d || typeof d !== 'object' || Array.isArray(d) || Object.keys(d).length !== 3
        || Object.keys(d).some((k) => !['historyId', 'transactionIds', 'action'].includes(k))
        || typeof d.historyId !== 'string' || approved.has(d.historyId) || !byId.has(d.historyId)
        || !['EXCLUDE_HISTORY', 'KEEP_BOTH'].includes(d.action) || !Array.isArray(d.transactionIds)
        || d.transactionIds.some((id) => typeof id !== 'string')
        || JSON.stringify([...d.transactionIds].sort()) !== JSON.stringify(byId.get(d.historyId))) throw new Error('INVALID_OVERLAP_DECISIONS');
      approved.set(d.historyId, d);
    }
  }
  const excluded = new Set([...approved.values()].filter((d) => d.action === 'EXCLUDE_HISTORY').map((d) => d.historyId));
  const unresolvedCount = candidates.length - approved.size;
  return { parsed: { ...parsed, transactions: parsed.transactions.map((t) => excluded.has(t.legacyId) ? { ...t, excludedFromReport: true } : t) },
    summary: { candidateHistoryCount: candidates.length,
      candidateTransactionCount: new Set(candidates.flatMap((c) => c.transactionIds)).size,
      reviewedCount: approved.size, excludedHistoryCount: excluded.size, unresolvedCount,
      status: unresolvedCount ? 'OVERLAP_REVIEW_REQUIRED' : 'OVERLAP_REVIEWED' } };
}
