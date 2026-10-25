import { createHash } from 'node:crypto';
import { hasExpectedHeaders } from '../sheet-schema.js';
import { canonicalContent } from './identity.js';
import { readHistoryRow } from './history-reader.js';
import { HISTORY_HEADERS, historyRow } from './schema.js';
import type { ParseResult } from './parser.js';

export type HistorySnapshot = { exists: boolean; headers: unknown[]; rows: unknown[][] };
export type ImportPort = {
  readHistory: () => Promise<HistorySnapshot>;
  appendHistory: (rows: unknown[][], create: boolean) => Promise<void>;
};
export function snapshotHash(snapshot: HistorySnapshot): string {
  return createHash('sha256').update(JSON.stringify(snapshot)).digest('hex');
}
export function planImport(parsed: ParseResult, snapshot: HistorySnapshot) {
  if (parsed.rejected.length) throw new Error('SOURCE_ROWS_REJECTED');
  if ((snapshot.exists && !hasExpectedHeaders(snapshot.headers, HISTORY_HEADERS))
    || (!snapshot.exists && (snapshot.headers.length || snapshot.rows.length))) throw new Error('HISTORY_SCHEMA_MISMATCH');
  const existing = new Map<string, string>();
  for (const row of snapshot.rows.filter((r) => r.some((v) => v !== '' && v !== null && v !== undefined))) {
    const t = readHistoryRow(row);
    if (existing.has(t.legacyId)) throw new Error('DUPLICATE_HISTORY_ID');
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
  return { pending, skipped: parsed.transactions.length - pending.length, create: !snapshot.exists };
}

export async function importMoneyLover(parsed: ParseResult, port: ImportPort, apply = false) {
  const before = await port.readHistory(), plan = planImport(parsed, before);
  const base = { inserted: 0, skipped: plan.skipped, pending: plan.pending.length, rejected: parsed.rejected.length };
  if (!apply) return { ...base, status: 'DRY_RUN' };
  if (!plan.pending.length) return { ...base, status: 'ALREADY_IMPORTED' };
  if (snapshotHash(await port.readHistory()) !== snapshotHash(before)) throw new Error('HISTORY_CHANGED_RETRY_DRY_RUN');
  let responseLost = false;
  try {
    // One atomic Sheets batch; no automatic retry after an ambiguous write.
    await port.appendHistory(plan.pending.map(historyRow), plan.create);
  } catch { responseLost = true; }
  let after: HistorySnapshot;
  try { after = await port.readHistory(); } catch { throw new Error('IMPORT_OUTCOME_UNKNOWN_READBACK_REQUIRED'); }
  const check = planImport(parsed, after);
  if (check.pending.length) throw new Error(responseLost ? 'IMPORT_OUTCOME_UNKNOWN_READBACK_REQUIRED' : 'IMPORT_READBACK_MISMATCH');
  const previous = new Map(before.rows.map((r) => [String(r[0]), JSON.stringify(r)]));
  if (after.rows.some((r) => previous.has(String(r[0])) && previous.get(String(r[0])) !== JSON.stringify(r))
    || [...previous.keys()].some((id) => !after.rows.some((r) => r[0] === id))) throw new Error('HISTORY_CHANGED_DURING_IMPORT');
  return { ...base, inserted: plan.pending.length, pending: 0, status: responseLost ? 'IMPORTED_RECONCILED_AFTER_ERROR' : 'IMPORTED' };
}
