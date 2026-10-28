import { createHash } from 'node:crypto';
import { hasExpectedHeaders } from '../sheet-schema.js';
import { canonicalContent } from './identity.js';
import { isExcludedHistoryRow, readHistoryRow } from './history-reader.js';
import { HISTORY_HEADERS, historyRow } from './schema.js';
import type { MoneyLoverTransaction } from './schema.js';
import type { ParseResult } from './parser.js';
import { reviewOverlaps } from './overlap.js';
import { historyAppendRequests } from './google-history.js';

export type HistorySnapshot = { exists: boolean; headers: unknown[]; rows: unknown[][] };
export type ImportPort = {
  readHistory: () => Promise<HistorySnapshot>;
  readTransactions: () => Promise<unknown[][]>;
  appendHistory: (rows: unknown[][], create: boolean) => Promise<void>;
  replaceHistory?: (rows: unknown[][]) => Promise<void>;
};
export function snapshotHash(snapshot: HistorySnapshot): string {
  return createHash('sha256').update(JSON.stringify(snapshot)).digest('hex');
}
export function planImport(parsed: ParseResult, snapshot: HistorySnapshot) {
  if (parsed.rejected.length) throw new Error('SOURCE_ROWS_REJECTED');
  if ((snapshot.exists && !hasExpectedHeaders(snapshot.headers, HISTORY_HEADERS))
    || (!snapshot.exists && (snapshot.headers.length || snapshot.rows.length))) throw new Error('HISTORY_SCHEMA_MISMATCH');
  const existing = new Map<string, string>(), historyIds = new Set<string>();
  for (const row of snapshot.rows.filter((r) => r.some((v) => v !== '' && v !== null && v !== undefined))) {
    const t = readHistoryRow(row);
    if (historyIds.has(t.legacyId)) throw new Error('DUPLICATE_HISTORY_ID');
    historyIds.add(t.legacyId);
    if (isExcludedHistoryRow(t)) continue;
    existing.set(t.legacyId, canonicalContent(t));
  }
  const ids = new Set<string>();
  const pending = parsed.transactions.filter((t) => {
    if (ids.has(t.legacyId)) throw new Error('DUPLICATE_SOURCE_ID');
    ids.add(t.legacyId);
    const old = existing.get(t.legacyId);
    if (old !== undefined && old !== canonicalContent(t)) throw new Error('HISTORY_CONTENT_CONFLICT');
    return old === undefined;
  });
  if (snapshot.rows.length + pending.length + 1 > 50001) throw new Error('HISTORY_SOURCE_LIMIT_EXCEEDED');
  // A deliberately conservative local ceiling; keep the complete import atomic rather than split writes.
  const bytes = Buffer.byteLength(JSON.stringify({ requests: historyAppendRequests(pending.map(historyRow), 0, !snapshot.exists) }));
  if (bytes + 1024 > 2 * 1024 * 1024) throw new Error('HISTORY_BATCH_TOO_LARGE');
  return { pending, skipped: parsed.transactions.length - pending.length, create: !snapshot.exists };
}

export type CleanupPlan = {
  toRemove: MoneyLoverTransaction[];
  toKeep: unknown[][];
  removedRows: { rowId: string; category: string; currency: string; amount: number; reason: string }[];
};

export function planCleanup(snapshot: HistorySnapshot): CleanupPlan {
  if (!snapshot.exists || !hasExpectedHeaders(snapshot.headers, HISTORY_HEADERS)) {
    throw new Error('HISTORY_SCHEMA_MISMATCH');
  }
  const toRemove: MoneyLoverTransaction[] = [];
  const toKeep: unknown[][] = [];
  const removedRows: CleanupPlan['removedRows'] = [];
  for (const row of snapshot.rows.filter((r) => r.some((v) => v !== '' && v !== null && v !== undefined))) {
    const t = readHistoryRow(row);
    if (isExcludedHistoryRow(t)) {
      toRemove.push(t);
      const reason = t.currency !== 'VND' ? 'CURRENCY_NOT_VND' : 'NON_CONSUMPTION_CATEGORY';
      removedRows.push({ rowId: t.legacyId, category: t.category, currency: t.currency, amount: t.amount, reason });
    } else {
      toKeep.push(row);
    }
  }
  return { toRemove, toKeep, removedRows };
}

export async function cleanupImportedHistory(port: ImportPort, apply = false) {
  const before = await port.readHistory();
  const plan = planCleanup(before);
  const base = {
    totalBefore: before.rows.filter((r) => r.some((v) => v !== '' && v !== null && v !== undefined)).length,
    removedCount: plan.toRemove.length,
    remainingCount: plan.toKeep.length,
    removedRows: plan.removedRows,
  };
  if (!apply) return { ...base, status: 'DRY_RUN_CLEANUP' };
  if (!plan.toRemove.length) return { ...base, status: 'ALREADY_CLEAN' };
  if (snapshotHash(await port.readHistory()) !== snapshotHash(before)) throw new Error('HISTORY_CHANGED_RETRY_DRY_RUN');
  if (!port.replaceHistory) throw new Error('REPLACE_HISTORY_NOT_SUPPORTED');
  await port.replaceHistory(plan.toKeep);
  const after = await port.readHistory();
  const check = planCleanup(after);
  if (check.toRemove.length) throw new Error('CLEANUP_READBACK_MISMATCH');
  return { ...base, status: 'CLEANED_UP' };
}

export async function importMoneyLover(source: ParseResult, port: ImportPort, apply = false, decisions?: unknown) {
  if (source.rejected.length) throw new Error('SOURCE_ROWS_REJECTED');
  const transactions = await port.readTransactions();
  const review = reviewOverlaps(source, transactions, decisions), parsed = review.parsed;
  const before = await port.readHistory(), plan = planImport(parsed, before);
  const base = { inserted: 0, skipped: plan.skipped, pending: plan.pending.length, rejected: parsed.rejected.length,
    create: plan.create, overlap: review.summary };
  if (!apply) return { ...base, status: 'DRY_RUN' };
  if (review.summary.unresolvedCount) throw new Error('OVERLAP_REVIEW_REQUIRED');
  if (!plan.pending.length && !plan.create) return { ...base, status: 'ALREADY_IMPORTED' };
  if (snapshotHash(await port.readHistory()) !== snapshotHash(before)) throw new Error('HISTORY_CHANGED_RETRY_DRY_RUN');
  if (JSON.stringify(await port.readTransactions()) !== JSON.stringify(transactions)) throw new Error('TRANSACTIONS_CHANGED_RETRY_DRY_RUN');
  let responseLost = false;
  try {
    // One atomic Sheets batch; no automatic retry after an ambiguous write.
    await port.appendHistory(plan.pending.map(historyRow), plan.create);
  } catch { responseLost = true; }
  let after: HistorySnapshot;
  try { after = await port.readHistory(); } catch { throw new Error('IMPORT_OUTCOME_UNKNOWN_READBACK_REQUIRED'); }
  const check = planImport(parsed, after);
  if (check.create || check.pending.length) throw new Error(responseLost ? 'IMPORT_OUTCOME_UNKNOWN_READBACK_REQUIRED' : 'IMPORT_READBACK_MISMATCH');
  const previous = new Map(before.rows.map((r) => [String(r[0]), JSON.stringify(r)]));
  if (after.rows.some((r) => previous.has(String(r[0])) && previous.get(String(r[0])) !== JSON.stringify(r))
    || [...previous.keys()].some((id) => !after.rows.some((r) => r[0] === id))) throw new Error('HISTORY_CHANGED_DURING_IMPORT');
  return { ...base, inserted: plan.pending.length, pending: 0, status: responseLost ? 'IMPORTED_RECONCILED_AFTER_ERROR' : 'IMPORTED' };
}
