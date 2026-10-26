import { createHmac } from 'node:crypto';
import { CATEGORY_REGISTRY } from './categories.js';
import { hasExpectedHeaders } from './sheet-schema.js';
import { fromFinanceSnapshot } from './unified-transactions.js';
import type { FinanceSnapshot, UnifiedTransaction } from './unified-transactions.js';

// Rule-based memory derived from the user's own choices. See docs/category-memory/DESIGN.md.
export const MEMORY_HEADERS = ['request_id', 'action', 'identity_key', 'type', 'category', 'created_at'];
export const MEMORY_WINDOW = 10;
export const MEMORY_RULE_LIMIT = 2000;
const SET_WEIGHT = 2;
const TIER_FACTOR = { ACCOUNT: 1, COUNTERPARTY: 1, DESCRIPTION: 0.85 } as const;
// Bank-generated markers only. Never merchant names: the system must learn categories, not hard-code them.
export const TECHNICAL_TOKENS = new Set(['MBVCB', 'IBFT', 'REF', 'TRACE', 'FT', 'QR', 'POS', 'ECOM', 'NAPAS', 'CT', 'CK', 'GD', 'TK', 'ND', 'VND', 'DEN']);
const UUID = /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/;
export const IDENTITY_KEY = /^(?:ACCOUNT:(?:VCB|BIDV|VIETINBANK):[a-f0-9]{32}|(?:COUNTERPARTY|DESCRIPTION):[A-Z0-9]+(?: [A-Z0-9]+){0,7})$/;

export type IdentityType = keyof typeof TIER_FACTOR;
export type Identity = { type: IdentityType; key: string; label: string };
export type IdentitySource = { bank: string | null; counterpartyAccount?: string | null; counterparty: string | null; description: string | null };
export type Observation = { transactionId: string; identity: Identity; type: 'THU' | 'CHI'; category: string; at: string };
export type MemoryCommand = { requestId: string; action: 'SET' | 'DELETE' | 'RESET_ALL'; identityKey: string; type: '' | 'THU' | 'CHI'; category: string; createdAt: string };
export type MemoryLevel = 'HINT' | 'AMBIGUOUS' | 'PRESELECT' | 'STRONG';
export type MemoryRule = { key: string; identityType: IdentityType; label: string; type: 'THU' | 'CHI'; category: string; count: number; total: number;
  confidence: number; level: MemoryLevel; recent: string[]; lastUsedAt: string; pinned: boolean };

// Twin of CategoryMemory.normalize in Kotlin; shared/category-memory-vectors.tsv must pass on both sides.
export function normalizeMemoryText(text: string): string {
  const tokens = text.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[đĐ]/g, 'D')
    .replace(/[^A-Za-z0-9]+/g, ' ').toUpperCase().split(' ')
    .filter((token) => token && !/\d/.test(token) && !TECHNICAL_TOKENS.has(token));
  const normalized = tokens.slice(0, 8).join(' ');
  return normalized.length >= 2 ? normalized : '';
}

export function memoryIdentityKey(secret: string): Buffer {
  return createHmac('sha256', secret).update('category-memory-identity-v1').digest();
}

export function resolveIdentity(source: IdentitySource, hmacKey: Buffer): Identity | null {
  const account = (source.counterpartyAccount ?? '').replace(/[\s.-]/g, '');
  if (/^\d{6,20}$/.test(account) && ['VCB', 'BIDV', 'VIETINBANK'].includes(source.bank ?? '')) {
    // Accounts are low-entropy: keyed HMAC so neither the Sheet nor the client cache can reverse them.
    const digest = createHmac('sha256', hmacKey).update(`${source.bank}:${account}`).digest('hex').slice(0, 32);
    const name = normalizeMemoryText(source.counterparty ?? '');
    return { type: 'ACCOUNT', key: `ACCOUNT:${source.bank}:${digest}`, label: `${name || 'Tài khoản'} · ${source.bank} ••${account.slice(-4)}` };
  }
  const party = source.counterparty === 'VCB Transaction' ? '' : normalizeMemoryText(source.counterparty ?? '');
  if (party) return { type: 'COUNTERPARTY', key: `COUNTERPARTY:${party}`, label: party };
  const description = normalizeMemoryText(source.description ?? '');
  return description ? { type: 'DESCRIPTION', key: `DESCRIPTION:${description}`, label: description } : null;
}

const identitySource = (t: UnifiedTransaction): IdentitySource => ({ bank: t.bank, counterparty: t.counterparty, description: t.description });

// Identity always comes from the ORIGINAL stored fields, never from an edited note or client input.
export function originalIdentities(snapshot: FinanceSnapshot, hmacKey: Buffer): Map<string, Identity | null> {
  return new Map(fromFinanceSnapshot({ bank: snapshot.bank, history: snapshot.history })
    .map((t) => [t.id, resolveIdentity(identitySource(t), hmacKey)]));
}

export function memoryObservations(snapshot: FinanceSnapshot, hmacKey: Buffer): Observation[] {
  const identities = originalIdentities(snapshot, hmacKey);
  const lastEdit = new Map<string, string>();
  // fromFinanceSnapshot validated the whole chain; rows are in revision order so the last one wins.
  for (const row of (snapshot.edits ?? []).slice(1)) if (typeof row[1] === 'string' && typeof row[7] === 'string') lastEdit.set(row[1], row[7]);
  const observations: Observation[] = [];
  for (const t of fromFinanceSnapshot(snapshot)) {
    const at = lastEdit.get(t.id) ?? (t.source === 'MANUAL' ? t.time : null);
    const identity = identities.get(t.id);
    if (!at || !identity || t.category === 'Chưa phân loại') continue;
    observations.push({ transactionId: t.id, identity, type: t.type, category: t.category, at: new Date(at).toISOString() });
  }
  return observations;
}

export function validMemoryCategory(category: unknown, type: unknown): boolean {
  return typeof category === 'string' && category !== 'Chưa phân loại'
    && CATEGORY_REGISTRY.some((c) => c.name === category && c.active && (c.type === 'BOTH' || c.type === type));
}

export function validCommand(c: MemoryCommand): boolean {
  if (!UUID.test(c.requestId) || !Number.isFinite(Date.parse(c.createdAt))) return false;
  if (c.action === 'RESET_ALL') return c.identityKey === '' && c.type === '' && c.category === '';
  if (!IDENTITY_KEY.test(c.identityKey) || (c.type !== 'THU' && c.type !== 'CHI')) return false;
  return c.action === 'SET' ? validMemoryCategory(c.category, c.type) : c.action === 'DELETE' && c.category === '';
}

export function readMemoryCommands(rows: unknown[][] | null): MemoryCommand[] {
  if (!rows) return [];
  if (!hasExpectedHeaders(rows[0] ?? [], MEMORY_HEADERS)) throw new Error('CATEGORY_MEMORY_SCHEMA_MISMATCH');
  const seen = new Set<string>();
  return rows.slice(1).filter((r) => r.some((v) => v !== '' && v != null)).map((r) => {
    const [requestId, action, identityKey, type, category, createdAt] = r.map((v) => v ?? '');
    const command = { requestId, action, identityKey, type, category, createdAt } as MemoryCommand;
    if (r.length > MEMORY_HEADERS.length || [requestId, action, identityKey, type, category, createdAt].some((v) => typeof v !== 'string')
      || !validCommand(command) || seen.has(command.requestId)) throw new Error('CATEGORY_MEMORY_SCHEMA_MISMATCH');
    seen.add(command.requestId);
    return command;
  });
}

type Entry = { at: string; category: string; order: string };
export function buildMemoryIndex(observations: Observation[], commands: MemoryCommand[]): MemoryRule[] {
  const rules = new Map<string, { identity: Identity | null; type: 'THU' | 'CHI'; entries: Entry[] }>();
  const rule = (key: string, type: 'THU' | 'CHI', identity: Identity | null) => {
    const id = `${key}|${type}`, existing = rules.get(id);
    if (existing) { existing.identity ??= identity; return existing; }
    const created = { identity, type, entries: [] as Entry[] };
    rules.set(id, created);
    return created;
  };
  for (const o of observations) rule(o.identity.key, o.type, o.identity).entries.push({ at: o.at, category: o.category, order: `1|${o.transactionId}` });
  const sortedCommands = [...commands].sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.requestId.localeCompare(b.requestId));
  // A command replaces history up to and including its own timestamp; later choices still count.
  const cut = (r: { entries: Entry[] }, at: string) => { r.entries = r.entries.filter((e) => e.at > at); };
  for (const c of sortedCommands) {
    const at = new Date(c.createdAt).toISOString();
    if (c.action === 'RESET_ALL') { for (const r of rules.values()) cut(r, at); continue; }
    const r = rule(c.identityKey, c.type as 'THU' | 'CHI', null);
    cut(r, at);
    if (c.action === 'SET') r.entries.push(...Array.from({ length: SET_WEIGHT }, (_, i) => ({ at, category: c.category, order: `0|${c.requestId}|${i}` })));
  }
  const result: MemoryRule[] = [];
  for (const [id, r] of rules) {
    const window = r.entries.sort((a, b) => a.at.localeCompare(b.at) || a.order.localeCompare(b.order)).slice(-MEMORY_WINDOW);
    if (!window.length) continue;
    const key = id.slice(0, id.lastIndexOf('|'));
    const identityType = key.slice(0, key.indexOf(':')) as IdentityType;
    const counts = new Map<string, { count: number; last: number }>();
    window.forEach((e, i) => counts.set(e.category, { count: (counts.get(e.category)?.count ?? 0) + 1, last: i }));
    const ranked = [...counts].sort((a, b) => b[1].count - a[1].count || b[1].last - a[1].last);
    const [category, { count }] = ranked[0], second = ranked[1]?.[1].count ?? 0;
    const confidence = Math.round(count / window.length * TIER_FACTOR[identityType] * 100) / 100;
    const level: MemoryLevel = window.length === 1 ? 'HINT' : count < 2 || count === second || confidence < 0.6 ? 'AMBIGUOUS'
      : window.length >= 4 && confidence >= 0.8 ? 'STRONG' : 'PRESELECT';
    // A rule created only by SET has no observed identity; its key is the safest available label.
    const label = r.identity?.label ?? key.slice(key.indexOf(':') + 1).replace(/^(?:VCB|BIDV|VIETINBANK):[a-f0-9]{32}$/, 'Tài khoản');
    result.push({ key, identityType, label, type: r.type, category, count, total: window.length, confidence, level,
      recent: [...counts].sort((a, b) => b[1].last - a[1].last).map(([name]) => name).slice(0, 3),
      lastUsedAt: window.at(-1)!.at, pinned: window.some((e) => e.order.startsWith('0|')) });
  }
  return result.sort((a, b) => b.lastUsedAt.localeCompare(a.lastUsedAt) || a.key.localeCompare(b.key)).slice(0, MEMORY_RULE_LIMIT);
}
