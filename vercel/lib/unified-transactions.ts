import { hasExpectedHeaders, hasTransactionHeaders } from './sheet-schema.js';
import { canonicalBankCategory, CATEGORY_REGISTRY } from './categories.js';
import { EDIT_HEADERS } from './edit-schema.js';
import { HISTORY_HEADERS, maskHistoryText, minorUnits, validDate } from './moneylover/schema.js';
import { isExcludedHistoryRow, isSavingsWallet, readHistoryRow } from './moneylover/history-reader.js';
import { readAccountIdentities } from './category-memory.js';
import type { Bank } from './transaction-service.js';
import type { OwnAccounts } from './configuration.js';

export type InternalTransferMetadata = {
  pairId: string;
  role: 'OUT' | 'IN';
};

export type UnifiedTransaction = {
  id: string; date: string; time: string | null; datePrecision: 'DAY' | 'MINUTE';
  type: 'THU' | 'CHI'; amount: number; currency: 'VND';
  category: string; originalCategory: string; accountOrWallet: string | null; bank: Bank | null;
  counterparty: string | null; description: string | null; event: string | null;
  source: 'BANK_NOTIFICATION' | 'MONEYLOVER' | 'MANUAL'; excludedFromReport: boolean;
  possibleDuplicate: boolean; possibleDuplicateCount: number;
  revision: string;
  internalTransfer?: InternalTransferMetadata | null;
};
export type FinanceSnapshot = { bank: unknown[][]; history: unknown[][]; edits?: unknown[][]; identities?: unknown[][] };
export type FinanceReadPort = { readFinance: () => Promise<FinanceSnapshot> };
export function fromFinanceSnapshot(snapshot: FinanceSnapshot, ownAccounts: OwnAccounts = {}): UnifiedTransaction[] {
  if (!hasTransactionHeaders(snapshot.bank[0] ?? [])
    || !hasExpectedHeaders(snapshot.history[0] ?? [], HISTORY_HEADERS)) throw new Error('FINANCE_SCHEMA_MISMATCH');
  const rows: UnifiedTransaction[] = [];
  const text = (v: unknown): string => {
    if (v === null || v === undefined) return '';
    if (typeof v !== 'string') throw new Error('INVALID_FINANCE_ROW');
    return v;
  };
  const populated = (row: unknown[]) => row.some((v) => v !== '' && v !== null && v !== undefined);
  for (const row of snapshot.history.slice(1).filter(populated)) {
    const t = maskHistoryText(readHistoryRow(row));
    if (isExcludedHistoryRow(t)) continue;
    rows.push({ id: `moneylover:${t.legacyId}`, date: t.date, time: null, datePrecision: 'DAY', type: t.type,
      amount: t.amount, currency: 'VND', category: t.category, originalCategory: t.category,
      accountOrWallet: t.wallet || null, bank: null, counterparty: t.counterparty || null, description: t.note || null,
      event: t.event || null, source: 'MONEYLOVER', excludedFromReport: t.excludedFromReport || t.category === 'Chuyển nội bộ',
      possibleDuplicate: false, possibleDuplicateCount: 0, revision: '0' });
  }
  for (const row of snapshot.bank.slice(1).filter(populated)) {
    const [dateTime, bank, type, amount, , party, description, category, id, source, account] = row;
    const manual = source === 'MANUAL';
    const match = /^(\d{2})\/(\d{2})\/(\d{4}) (\d{2}):(\d{2})$/.exec(text(dateTime));
    if (!match || row.length > snapshot.bank[0].length || !['VCB', 'BIDV', 'VIETINBANK', ...(manual ? ['CASH'] : [])].includes(text(bank))
      || !['', 'BANK_NOTIFICATION', 'MANUAL'].includes(text(source)) || (manual && !text(account).trim())
      || (type !== 'THU' && type !== 'CHI') || typeof amount !== 'number' || !Number.isSafeInteger(amount) || amount <= 0
      || !/^[a-f0-9]{64}$/.test(text(id))) throw new Error('INVALID_FINANCE_ROW');
    minorUnits(amount);
    const date = `${match[3]}-${match[2]}-${match[1]}`, time = `${match[4]}:${match[5]}`;
    if (!validDate(date) || Number(match[4]) > 23 || Number(match[5]) > 59) throw new Error('INVALID_FINANCE_ROW');
    const canonical = canonicalBankCategory(text(category));
    const rawAccount = text(account).trim();
    const effectiveAccount = rawAccount === text(bank) ? '' : rawAccount;
    const bankAccount = effectiveAccount || ownAccounts[bank as Bank]?.label || text(bank);
    rows.push({ id: `${manual ? 'manual' : 'bank'}:${id}`, date, time: `${date}T${time}:00+07:00`, datePrecision: 'MINUTE', type,
      amount, currency: 'VND', category: canonical, originalCategory: text(category),
      accountOrWallet: manual ? text(account) : bankAccount, bank: bank === 'CASH' ? null : bank as Bank, counterparty: text(party) || null,
      description: text(description) || null, event: null, source: manual ? 'MANUAL' : 'BANK_NOTIFICATION', excludedFromReport: canonical === 'Chuyển nội bộ',
      possibleDuplicate: false, possibleDuplicateCount: 0, revision: '0', internalTransfer: null });
  }
  if (new Set(rows.map((t) => t.id)).size !== rows.length) throw new Error('DUPLICATE_FINANCE_ID');
  if (snapshot.edits) {
    if (!hasExpectedHeaders(snapshot.edits[0] ?? [], EDIT_HEADERS)) throw new Error('FINANCE_SCHEMA_MISMATCH');
    const edits = snapshot.edits.slice(1).filter(populated), ids = new Set<string>();
    const targets = new Map(rows.map((row) => [row.id, row]));
    for (const edit of edits) {
      const [requestId, id, category, note, excluded, type, baseRevision, createdAt] = edit;
      const target = typeof id === 'string' ? targets.get(id) : undefined;
      if (edit.length !== 8 || typeof requestId !== 'string' || ids.has(requestId) || !/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/.test(requestId)
        || !target || typeof category !== 'string' || typeof note !== 'string' || Buffer.byteLength(note) > 1024 || typeof excluded !== 'boolean'
        || (type !== 'THU' && type !== 'CHI') || baseRevision !== target.revision || typeof createdAt !== 'string'
        || !Number.isFinite(Date.parse(createdAt)) || !CATEGORY_REGISTRY.some((c) => c.name === category && c.active && (c.type === 'BOTH' || c.type === type))) throw new Error('INVALID_FINANCE_ROW');
      ids.add(requestId);
      target.revision = requestId; target.category = category; target.description = note || null;
      target.type = type; target.excludedFromReport = excluded || category === 'Chuyển nội bộ';
    }
  }

  const identities = snapshot.identities ? readAccountIdentities(snapshot.identities) : new Map();
  type AnchorInfo = { tx: UnifiedTransaction; targetBank: string };
  const anchors: AnchorInfo[] = [];
  for (const t of rows) {
    if (t.source !== 'BANK_NOTIFICATION' || !t.bank || !t.time) continue;
    // R1: Anchor is valid only if revision === '0' or its category is still 'Chuyển nội bộ'
    if (t.revision !== '0' && t.category !== 'Chuyển nội bộ') continue;
    const iden = identities.get(t.id);
    if (!iden || !iden.key.startsWith('ACCOUNT:')) continue;
    const digest = iden.key.slice('ACCOUNT:'.length);
    for (const [ownBank, config] of Object.entries(ownAccounts)) {
      if (ownBank !== t.bank && config?.digest === digest) {
        anchors.push({ tx: t, targetBank: ownBank });
        break;
      }
    }
  }

  if (anchors.length > 0) {
    const candidateMap = new Map<UnifiedTransaction, UnifiedTransaction[]>();
    const reverseMap = new Map<UnifiedTransaction, UnifiedTransaction[]>();
    for (const a of anchors) {
      // R2 & K3: Candidate must have revision === '0' and not already be 'Chuyển nội bộ'
      const candidates = rows.filter((c) => (
        c !== a.tx
        && c.source === 'BANK_NOTIFICATION'
        && c.bank === a.targetBank
        && c.type !== a.tx.type
        && c.amount === a.tx.amount
        && c.time != null
        && c.revision === '0'
        && c.category !== 'Chuyển nội bộ'
        && Math.abs(Date.parse(c.time) - Date.parse(a.tx.time!)) <= 30 * 60 * 1000
      ));
      candidateMap.set(a.tx, candidates);
      for (const c of candidates) {
        reverseMap.set(c, [...(reverseMap.get(c) ?? []), a.tx]);
      }
    }

    const paired = new Set<string>();
    for (const a of anchors) {
      const candidates = candidateMap.get(a.tx) ?? [];
      if (candidates.length !== 1) continue;
      const c = candidates[0];
      const matchingAnchors = reverseMap.get(c) ?? [];
      const isMutual = candidateMap.get(c)?.length === 1 && candidateMap.get(c)![0] === a.tx;
      if (matchingAnchors.length !== 1 && !isMutual) continue;
      if (paired.has(a.tx.id) || paired.has(c.id)) continue;

      paired.add(a.tx.id);
      paired.add(c.id);
      const outTx = a.tx.type === 'CHI' ? a.tx : c;
      const inTx = a.tx.type === 'THU' ? a.tx : c;
      const pairId = `${outTx.id}<->${inTx.id}`;
      outTx.internalTransfer = { pairId, role: 'OUT' };
      inTx.internalTransfer = { pairId, role: 'IN' };
      if (outTx.revision === '0') {
        outTx.category = 'Chuyển nội bộ';
        outTx.excludedFromReport = true;
      }
      if (inTx.revision === '0') {
        inTx.category = 'Chuyển nội bộ';
        inTx.excludedFromReport = true;
      }
    }
  }

  // Validate edit chains before hiding a savings record, so its edits are not orphaned.
  const visibleRows = rows.filter((t) => t.source !== 'MONEYLOVER' || !isSavingsWallet(t.accountOrWallet ?? ''));
  const groups = new Map<string, { bank: number; history: number }>();
  const key = (t: UnifiedTransaction) => `${t.date}|${t.type}|${t.amount}`;
  for (const t of visibleRows) {
    if (t.source === 'MANUAL' || t.excludedFromReport) continue;
    const group = groups.get(key(t)) ?? { bank: 0, history: 0 };
    group[t.source === 'MONEYLOVER' ? 'history' : 'bank']++;
    groups.set(key(t), group);
  }
  for (const t of visibleRows) {
    if (t.source === 'MANUAL' || t.excludedFromReport) continue;
    const group = groups.get(key(t));
    t.possibleDuplicateCount = group ? group[t.source === 'MONEYLOVER' ? 'bank' : 'history'] : 0;
    t.possibleDuplicate = t.possibleDuplicateCount > 0;
  }
  return visibleRows.sort((a, b) => transactionKey(a) < transactionKey(b) ? 1 : transactionKey(a) > transactionKey(b) ? -1 : 0);
}
export function transactionKey(t: UnifiedTransaction): string { return `${t.date}|${t.time ?? ''}|${t.id}`; }
