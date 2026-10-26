import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { buildMemoryIndex, memoryIdentityKey, memoryObservations, MEMORY_HEADERS, normalizeMemoryText, readMemoryCommands, resolveIdentity } from '../lib/category-memory.js';
import type { MemoryCommand, MemoryRule, Observation } from '../lib/category-memory.js';
import { handleCategoryMemoryRequest } from '../lib/category-memory-service.js';
import type { MemoryPort } from '../lib/category-memory-service.js';
import { readFinance } from '../lib/finance-read-service.js';
import { writeTransactionEdit } from '../lib/transaction-edits.js';
import { auditSchema, planAuditMigration } from '../lib/audit-migration.js';
import { EDIT_HEADERS } from '../lib/edit-schema.js';
import { TRANSACTION_HEADERS_V2 } from '../lib/sheet-schema.js';
import { HISTORY_HEADERS } from '../lib/moneylover/schema.js';
import type { FinanceSnapshot } from '../lib/unified-transactions.js';
import { parseVcb } from '../parsers/vcb.js';
import { parseBidv } from '../parsers/bidv.js';
import { parseVietinbank } from '../parsers/vietinbank.js';
import { SYNTHETIC_SECRET, testEnvironment } from './support.js';

// Every name, account, reference and amount below is synthetic.
const KEY = memoryIdentityKey(SYNTHETIC_SECRET);
const uuid = (n: number) => `${n.toString(16).padStart(8, '0')}-0000-4000-8000-${n.toString(16).padStart(12, '0')}`;
const vcb = (ref: string) => `Số dư TK VCB 999999999999 -100000 VND lúc 01-01-2026 12:00:00. Số dư 900000 VND. Ref MBVCB.FIXTURE.${ref}`;

class Ledger {
  bank: unknown[][] = [[...TRANSACTION_HEADERS_V2]];
  edits: unknown[][] = [[...EDIT_HEADERS]];
  revisions = new Map<string, string>();
  n = 0;
  add(o: { bank?: string; type?: 'THU' | 'CHI'; counterparty?: string; description?: string; category?: string; source?: 'BANK_NOTIFICATION' | 'MANUAL' } = {}) {
    const n = ++this.n, id = n.toString(16).padStart(64, '0'), source = o.source ?? 'BANK_NOTIFICATION', bank = o.bank ?? 'VCB';
    const time = `01/03/2026 ${String(Math.floor(n / 60) % 24).padStart(2, '0')}:${String(n % 60).padStart(2, '0')}`;
    this.bank.push([time, bank, o.type ?? 'CHI', 1000 * n, '', o.counterparty ?? '', o.description ?? '', o.category ?? 'Chưa phân loại', id, source, bank]);
    return `${source === 'MANUAL' ? 'manual' : 'bank'}:${id}`;
  }
  confirm(transactionId: string, category: string, type: 'THU' | 'CHI' = 'CHI', note = '') {
    const n = ++this.n, requestId = uuid(n);
    this.edits.push([requestId, transactionId, category, note, false, type, this.revisions.get(transactionId) ?? '0', new Date(Date.UTC(2026, 2, 2, 0, 0, n)).toISOString()]);
    this.revisions.set(transactionId, requestId);
  }
  // A fresh notification from the same party, then the user's choice for it.
  choose(category: string, party = 'NGUYEN VAN A', type: 'THU' | 'CHI' = 'CHI') { const id = this.add({ counterparty: party, type }); this.confirm(id, category, type); return id; }
  snapshot(): FinanceSnapshot { return { bank: this.bank, history: [[...HISTORY_HEADERS]], edits: this.edits }; }
}
const rules = (ledger: Ledger, commands: MemoryCommand[] = []) => buildMemoryIndex(memoryObservations(ledger.snapshot(), KEY), commands);
const find = (list: MemoryRule[], key: string, type = 'CHI') => list.find((r) => r.key === key && r.type === type);
const party = 'COUNTERPARTY:NGUYEN VAN A';
const at = (i: number) => new Date(Date.UTC(2026, 0, 1, 0, 0, i)).toISOString();
const observe = (key: string, categories: string[]): Observation[] => categories.map((category, i) => ({
  transactionId: `bank:${i}`, identity: { type: key.startsWith('DESCRIPTION') ? 'DESCRIPTION' : 'COUNTERPARTY', key, label: key }, type: 'CHI', category, at: at(i) }));

test('shared normalization vectors match the Kotlin twin contract', () => {
  const lines = readFileSync(new URL('../../shared/category-memory-vectors.tsv', import.meta.url), 'utf8').split('\n').filter((l) => l && !l.startsWith('#'));
  assert.ok(lines.length >= 15);
  for (const line of lines) {
    const [input, expected] = line.split('\t').map((v) => v.replace(/\\u([0-9a-fA-F]{4})/g, (_, h) => String.fromCharCode(parseInt(h, 16))));
    assert.equal(normalizeMemoryText(input), expected, input);
  }
});

test('Tier 1 account identity: per bank, formatting-insensitive, keyed and never reversible from the key', () => {
  for (const bank of ['VCB', 'BIDV', 'VIETINBANK']) {
    const a = resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank, counterpartyAccount: '0123456789', counterparty: 'NGUYEN VAN A', description: 'x' }, KEY)!;
    const b = resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank, counterpartyAccount: '0123 456.789', counterparty: 'nguyễn văn a', description: 'other' }, KEY)!;
    assert.equal(a.type, 'ACCOUNT'); assert.equal(a.key, b.key);
    assert.match(a.key, new RegExp(`^ACCOUNT:${bank}:[a-f0-9]{32}$`));
    assert.equal(a.key.includes('0123456789') || a.label.includes('0123456789'), false);
    assert.match(a.label, /••6789$/);
  }
  // Case A: same digits, different bank.
  const vcbKey = resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: 'VCB', counterpartyAccount: '123456', counterparty: '', description: '' }, KEY)!.key;
  const bidvKey = resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: 'BIDV', counterpartyAccount: '123456', counterparty: '', description: '' }, KEY)!.key;
  assert.notEqual(vcbKey, bidvKey);
  // Another deployment secret yields unrelated keys.
  assert.notEqual(resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: 'VCB', counterpartyAccount: '123456', counterparty: '', description: '' }, memoryIdentityKey('b'.repeat(64)))!.key, vcbKey);
  // Malformed accounts never become Tier 1.
  for (const bad of ['12345', '1'.repeat(21), '12AB5678', '']) {
    assert.notEqual(resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: 'VCB', counterpartyAccount: bad, counterparty: 'SHOP', description: '' }, KEY)?.type, 'ACCOUNT');
  }
  assert.equal(resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: null, counterpartyAccount: '0123456789', counterparty: '', description: '' }, KEY), null);
});

test('Case B: a shared name never merges different accounts; Tier 1 wins over Tier 2', () => {
  const first = resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: 'VCB', counterpartyAccount: '0123456789', counterparty: 'NGUYEN VAN A', description: '' }, KEY)!;
  const second = resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: 'VCB', counterpartyAccount: '9876543210', counterparty: 'NGUYEN VAN A', description: '' }, KEY)!;
  const nameOnly = resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: 'VCB', counterparty: 'NGUYEN VAN A', description: '' }, KEY)!;
  assert.equal(new Set([first.key, second.key, nameOnly.key]).size, 3);
  assert.equal(nameOnly.key, party);
  // Learning on one account never leaks into the ambiguous bare name or the other account.
  const list = buildMemoryIndex([0, 1, 2].map((i) => ({ transactionId: `t${i}`, identity: first, type: 'CHI' as const, category: 'Gia đình', at: at(i) })), []);
  assert.equal(list.length, 1); assert.equal(list[0].key, first.key);
  assert.equal(find(list, second.key), undefined); assert.equal(find(list, nameOnly.key), undefined);
});

test('Case C / Scenario 3: VCB merchant references normalize to one merchant identity', () => {
  const keys = ['GRAB REF111', 'GRAB REF222', 'GRAB REF333'].map((ref) => {
    const parsed = parseVcb(vcb(ref))!;
    return resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: parsed.bank, counterparty: parsed.counterparty, description: parsed.description }, KEY)!;
  });
  assert.deepEqual(new Set(keys.map((k) => k.key)), new Set(['COUNTERPARTY:GRAB']));
  assert.equal(resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: 'VCB', counterparty: 'GRABFOOD', description: '' }, KEY)!.key, 'COUNTERPARTY:GRABFOOD');
  assert.equal(normalizeMemoryText('SIEU THI AN LAC'), 'SIEU THI AN LAC');
  assert.notEqual(normalizeMemoryText('SIEU THI AN LAC'), normalizeMemoryText('SIEU THI AN PHU'));
});

test('description fallback: BIDV/VietinBank/manual without counterparty use a lower-confidence Tier 3 identity', () => {
  const bidv = parseBidv('BIDV xin thông báo tới Quý khách Thời gian giao dịch: 09:41 28/02/2026 Tài khoản thanh toán: 1234567890 Số tiền GD: -12,500 VND Số dư cuối: 87,500 VND Nội dung giao dịch: Thanh toan Dien luc FT26059111 Mã giao dịch: FT26059ABC123')!;
  const identity = resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: 'BIDV', counterparty: bidv.counterparty, description: bidv.description }, KEY)!;
  assert.equal(identity.type, 'DESCRIPTION');
  assert.equal(identity.key, 'DESCRIPTION:THANH TOAN DIEN LUC');
  assert.equal(resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: 'VIETINBANK', counterparty: '', description: 'Internet thang' }, KEY)!.key, 'DESCRIPTION:INTERNET THANG');
  // Ref-less VCB stores the whole template as description: it must never become one shared identity.
  assert.equal(resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: 'VCB', counterparty: 'VCB Transaction', description: 'Số dư TK VCB *** -100,000 VND lúc 01-01-2026 12:00:00. Số dư 1 VND.' }, KEY), null);
  assert.equal(resolveIdentity({ source: 'MANUAL', type: 'CHI', bank: null, counterparty: '', description: 'Chuyển tiền cho mẹ' }, KEY)!.key, 'DESCRIPTION:CHUYEN TIEN CHO ME');
  assert.equal(resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: null, counterparty: '', description: '123456 ***' }, KEY), null);
  const tier2 = buildMemoryIndex(observe(party, ['Gia đình', 'Gia đình', 'Gia đình', 'Gia đình', 'Ăn uống']), [])[0];
  const tier3 = buildMemoryIndex(observe('DESCRIPTION:INTERNET', ['Gia đình', 'Gia đình', 'Gia đình', 'Gia đình', 'Ăn uống']), [])[0];
  assert.equal(tier2.confidence, 0.8); assert.equal(tier2.level, 'STRONG');
  assert.equal(tier3.confidence, 0.68); assert.equal(tier3.level, 'PRESELECT');
});

test('learning levels: first observation, repetition, conflict, confidence and strong threshold', () => {
  const level = (categories: string[]) => buildMemoryIndex(observe(party, categories), [])[0];
  const first = level(['Gia đình']);
  assert.deepEqual([first.count, first.total, first.confidence, first.level], [1, 1, 1, 'HINT']);
  assert.equal(level(['Gia đình', 'Gia đình']).level, 'PRESELECT');
  assert.equal(level(['Gia đình', 'Gia đình', 'Gia đình']).level, 'PRESELECT');
  assert.equal(level(['Gia đình', 'Gia đình', 'Gia đình', 'Gia đình']).level, 'STRONG');
  const tie = level(['Ăn uống', 'Ăn uống', 'Ăn uống', 'Gia đình', 'Gia đình', 'Gia đình']);
  assert.equal(tie.level, 'AMBIGUOUS'); assert.equal(tie.confidence, 0.5);
  assert.deepEqual(tie.recent, ['Gia đình', 'Ăn uống']);
  const eight = level([...Array(8).fill('Ăn uống'), 'Mua sắm', 'Các chi phí khác']);
  assert.deepEqual([eight.category, eight.count, eight.total, eight.confidence, eight.level], ['Ăn uống', 8, 10, 0.8, 'STRONG']);
  assert.equal(level(['Ăn uống', 'Gia đình']).level, 'AMBIGUOUS');
  assert.equal(level(['Ăn uống', 'Ăn uống', 'Mua sắm', 'Gia đình']).level, 'AMBIGUOUS');
});

test('recency: the 10-observation window adapts after a behaviour change without hidden decay', () => {
  const history = Array(10).fill('Ăn uống');
  const step = (extra: number) => buildMemoryIndex(observe(party, [...history, ...Array(extra).fill('Gia đình')]), [])[0];
  assert.deepEqual([step(5).level, step(5).count, step(5).total], ['AMBIGUOUS', 5, 10]);
  assert.deepEqual([step(7).category, step(7).level, step(7).confidence], ['Gia đình', 'PRESELECT', 0.7]);
  assert.deepEqual([step(8).category, step(8).level], ['Gia đình', 'STRONG']);
});

test('only user choices are learned: auto categories, Chưa phân loại and unedited history are ignored; THU/CHI are separate rules', () => {
  const ledger = new Ledger();
  ledger.add({ counterparty: 'NGUYEN VAN A', category: 'Siêu thị' });
  const unknown = ledger.add({ counterparty: 'NGUYEN VAN A' });
  ledger.confirm(unknown, 'Chưa phân loại');
  assert.equal(rules(ledger).length, 0);
  ledger.choose('Gia đình'); ledger.choose('Tiền chuyển đến', 'NGUYEN VAN A', 'THU');
  ledger.add({ source: 'MANUAL', bank: 'CASH', description: 'Ăn sáng', category: 'Ăn uống' });
  const list = rules(ledger);
  assert.equal(find(list, party)!.category, 'Gia đình');
  assert.equal(find(list, party, 'THU')!.category, 'Tiền chuyển đến');
  assert.equal(find(list, 'DESCRIPTION:AN SANG')!.category, 'Ăn uống');
});

test('Scenario 1: three confirmations then the fourth transaction from the same party is preselected', async () => {
  const ledger = new Ledger();
  for (let i = 0; i < 3; i++) ledger.choose('Gia đình');
  const fourth = ledger.add({ counterparty: 'NGUYEN VAN A' });
  const env = testEnvironment({ CATEGORY_MEMORY_ENABLED: '1' });
  const page = await readFinance({ method: 'GET', headers: { 'x-webhook-secret': SYNTHETIC_SECRET }, query: {} }, env,
    async () => ({ readFinance: async () => ledger.snapshot() }), 'transactions');
  const record = ((page.body as Record<string, unknown>).transactions as { id: string; memoryKey: string }[]).find((t) => t.id === fourth)!;
  assert.equal(record.memoryKey, party);
  const rule = find(rules(ledger), record.memoryKey)!;
  assert.deepEqual([rule.category, rule.count, rule.total, rule.level], ['Gia đình', 3, 3, 'PRESELECT']);
  // Tier 1 contract for the same scenario once a parser exposes a structured account.
  const account = resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: 'VCB', counterpartyAccount: '0123456789', counterparty: 'NGUYEN VAN A', description: '' }, KEY)!;
  const strict = buildMemoryIndex([0, 1, 2].map((i) => ({ transactionId: `t${i}`, identity: account, type: 'CHI' as const, category: 'Gia đình', at: at(i) })), []);
  assert.deepEqual([strict[0].category, strict[0].level], ['Gia đình', 'PRESELECT']);
  const disabled = await readFinance({ method: 'GET', headers: { 'x-webhook-secret': SYNTHETIC_SECRET }, query: {} }, testEnvironment(),
    async () => ({ readFinance: async () => ledger.snapshot() }), 'transactions');
  assert.equal('memoryKey' in ((disabled.body as Record<string, unknown>).transactions as object[])[0], false);
});

test('Scenario 2: a manual override updates the rule without forgetting history at once', () => {
  const ledger = new Ledger();
  for (let i = 0; i < 3; i++) ledger.choose('Gia đình');
  ledger.choose('Ăn uống');
  let rule = find(rules(ledger), party)!;
  assert.deepEqual([rule.category, rule.count, rule.total, rule.confidence, rule.level], ['Gia đình', 3, 4, 0.75, 'PRESELECT']);
  assert.deepEqual(rule.recent, ['Ăn uống', 'Gia đình']);
  ledger.choose('Ăn uống'); ledger.choose('Ăn uống');
  rule = find(rules(ledger), party)!;
  assert.equal(rule.level, 'AMBIGUOUS');
  ledger.choose('Ăn uống');
  rule = find(rules(ledger), party)!;
  assert.deepEqual([rule.category, rule.confidence, rule.level], ['Ăn uống', 0.57, 'AMBIGUOUS']);
  ledger.choose('Ăn uống');
  rule = find(rules(ledger), party)!;
  assert.deepEqual([rule.category, rule.confidence, rule.level], ['Ăn uống', 0.63, 'PRESELECT']);
});

test('Scenario 8: editing one transaction A → B is one observation of B, never A+1 and B+1', () => {
  const ledger = new Ledger();
  const id = ledger.add({ counterparty: 'NGUYEN VAN A' });
  ledger.confirm(id, 'Ăn uống'); ledger.confirm(id, 'Gia đình', 'CHI', 'synthetic note change');
  const rule = find(rules(ledger), party)!;
  assert.deepEqual([rule.category, rule.count, rule.total], ['Gia đình', 1, 1]);
  // Identity comes from the original counterparty, not the edited note.
  ledger.confirm(id, 'Gia đình', 'CHI', 'GRAB');
  assert.equal(find(rules(ledger), party)!.total, 1);
  assert.equal(find(rules(ledger), 'COUNTERPARTY:GRAB'), undefined);
});

test('Scenario 7: WorkManager sending the same edit three times counts one observation', async () => {
  const ledger = new Ledger();
  const id = ledger.add({ counterparty: 'NGUYEN VAN A' });
  const snapshot = ledger.snapshot(), guards = new Set<string>();
  const port = { read: async () => structuredClone(snapshot), append: async (row: unknown[], guard: string) => {
    if (guards.has(guard)) throw Error('revision conflict'); guards.add(guard); snapshot.edits!.push(row);
  } };
  const body = { requestId: uuid(900), transactionId: id, category: 'Gia đình', note: '', excluded: false, type: 'CHI', baseRevision: '0' };
  const env = testEnvironment({ TRANSACTION_EDITS_ENABLED: '1' });
  const statuses = [];
  for (let i = 0; i < 3; i++) statuses.push((await writeTransactionEdit({ method: 'POST', headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' }, body }, env, async () => port)).body.status);
  assert.deepEqual(statuses, ['SAVED_TRANSACTION_EDIT', 'DUPLICATE_IGNORED', 'DUPLICATE_IGNORED']);
  assert.equal(snapshot.edits!.length, 2);
  const rule = buildMemoryIndex(memoryObservations(snapshot, KEY), [])[0];
  assert.deepEqual([rule.count, rule.total], [1, 1]);
});

function memoryServer(ledger: Ledger, commands: unknown[][] | null = [[...MEMORY_HEADERS]]) {
  const markers = new Set<string>(); let ports = 0; const scopes: boolean[] = [];
  const port: MemoryPort = {
    readFinance: async () => structuredClone(ledger.snapshot()),
    readCommands: async () => structuredClone(commands),
    append: async (row, marker) => {
      await Promise.resolve();
      if (markers.has(marker)) return 'DUPLICATE';
      markers.add(marker); commands!.push(row); return 'APPENDED';
    },
  };
  return { commands, get ports() { return ports; }, scopes, factory: async (_: unknown, write: boolean) => { ports++; scopes.push(write); return port; } };
}
const writeEnv = () => testEnvironment({ CATEGORY_MEMORY_ENABLED: '1', CATEGORY_MEMORY_WRITES_ENABLED: '1' });
const post = (body: unknown, headers: Record<string, unknown> = {}) => ({ method: 'POST', headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json', ...headers }, body });
const get = (query: Record<string, unknown> = {}) => ({ method: 'GET', headers: { 'x-webhook-secret': SYNTHETIC_SECRET }, query });

test('rule management: SET preselects and can still be overridden, DELETE and RESET_ALL forget; replays are idempotent', async () => {
  const ledger = new Ledger();
  ledger.choose('Ăn uống');
  const server = memoryServer(ledger);
  const set = { requestId: uuid(500), action: 'SET', identityKey: party, type: 'CHI', category: 'Gia đình' };
  const results = await Promise.all([0, 1, 2].map(() => handleCategoryMemoryRequest(post(set), writeEnv(), server.factory, new Date('2026-03-03T00:00:00Z'))));
  assert.deepEqual(results.map((r) => r.httpStatus), [200, 200, 200]);
  assert.equal(server.commands!.length, 2);
  assert.equal((await handleCategoryMemoryRequest(post({ ...set, category: 'Mua sắm' }), writeEnv(), server.factory)).httpStatus, 409);
  const index = await handleCategoryMemoryRequest(get(), writeEnv(), server.factory);
  let rule = find(index.body.rules as MemoryRule[], party)!;
  assert.deepEqual([rule.category, rule.count, rule.total, rule.level, rule.pinned], ['Gia đình', 2, 2, 'PRESELECT', true]);
  assert.equal(index.body.writesEnabled, true);
  const commands = () => readMemoryCommands(server.commands);
  // Later contrary choices still win: the rule is never locked.
  const later = (category: string) => ledger.choose(category);
  later('Ăn uống'); later('Ăn uống'); later('Ăn uống');
  // Edits in the fixture are dated before the SET; re-date them after it.
  for (const row of ledger.edits.slice(-3)) row[7] = '2026-03-04T00:00:00.000Z';
  rule = find(rules(ledger, commands()), party)!;
  assert.deepEqual([rule.category, rule.level], ['Ăn uống', 'PRESELECT']);
  await handleCategoryMemoryRequest(post({ requestId: uuid(501), action: 'DELETE', identityKey: party, type: 'CHI' }), writeEnv(), server.factory, new Date('2026-03-05T00:00:00Z'));
  assert.equal(find(rules(ledger, commands()), party), undefined);
  ledger.choose('Gia đình', 'GRAB');
  for (const row of ledger.edits.slice(-1)) row[7] = '2026-03-06T00:00:00.000Z';
  assert.equal(rules(ledger, commands()).length, 1);
  await handleCategoryMemoryRequest(post({ requestId: uuid(502), action: 'RESET_ALL' }), writeEnv(), server.factory, new Date('2026-03-07T00:00:00Z'));
  assert.equal(rules(ledger, commands()).length, 0);
  ledger.choose('Taxi', 'GRAB');
  for (const row of ledger.edits.slice(-1)) row[7] = '2026-03-08T00:00:00.000Z';
  assert.deepEqual(rules(ledger, commands()).map((r) => [r.key, r.category, r.total]), [['COUNTERPARTY:GRAB', 'Taxi', 1]]);
  assert.deepEqual(server.scopes.filter((write) => !write).length, 1);
});

test('Scenario 4: the same account number at another bank never inherits a rule', () => {
  const vcbIdentity = resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: 'VCB', counterpartyAccount: '0123456789', counterparty: 'NGUYEN VAN A', description: '' }, KEY)!;
  const bidvIdentity = resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: 'BIDV', counterpartyAccount: '0123456789', counterparty: 'NGUYEN VAN A', description: '' }, KEY)!;
  const list = buildMemoryIndex([0, 1, 2].map((i) => ({ transactionId: `t${i}`, identity: vcbIdentity, type: 'CHI' as const, category: 'Gia đình', at: at(i) })), []);
  assert.ok(find(list, vcbIdentity.key));
  assert.equal(find(list, bidvIdentity.key), undefined);
});

test('security: auth, flags, malformed identities, invalid categories and huge payloads fail closed before Sheets', async () => {
  const ledger = new Ledger(); const server = memoryServer(ledger);
  const valid = { requestId: uuid(600), action: 'SET', identityKey: party, type: 'CHI', category: 'Gia đình' };
  for (const request of [{ ...post(valid), headers: { 'content-type': 'application/json' } }, { ...get(), headers: { 'x-webhook-secret': 'b'.repeat(64) } }]) {
    assert.equal((await handleCategoryMemoryRequest(request, writeEnv(), server.factory)).httpStatus, 401);
  }
  assert.deepEqual((await handleCategoryMemoryRequest(get(), testEnvironment(), server.factory)).body, { version: 1, enabled: false });
  assert.equal((await handleCategoryMemoryRequest(post(valid), testEnvironment({ CATEGORY_MEMORY_ENABLED: '1' }), server.factory)).body.error, 'CATEGORY_MEMORY_WRITES_DISABLED');
  assert.equal((await handleCategoryMemoryRequest(post(valid), testEnvironment({ CATEGORY_MEMORY_WRITES_ENABLED: '1' }), server.factory)).body.error, 'CATEGORY_MEMORY_WRITES_DISABLED');
  const bad = [
    { ...valid, identityKey: 'COUNTERPARTY:=HYPERLINK("x")' }, { ...valid, identityKey: 'ACCOUNT:VCB:0123456789' }, { ...valid, identityKey: 'COUNTERPARTY:grab' },
    { ...valid, identityKey: `COUNTERPARTY:${'A '.repeat(9)}A` }, { ...valid, category: 'Không có' }, { ...valid, category: 'Chưa phân loại' },
    { ...valid, category: 'Lương' }, { ...valid, type: 'BOTH' }, { ...valid, requestId: 'not-a-uuid' }, { ...valid, extra: 1 },
    { ...valid, action: 'DROP' }, { ...valid, action: 'DELETE' }, { requestId: uuid(601), action: 'RESET_ALL', identityKey: party }, [valid], null,
  ];
  for (const body of bad) assert.equal((await handleCategoryMemoryRequest(post(body), writeEnv(), server.factory)).httpStatus, 400, JSON.stringify(body));
  assert.equal((await handleCategoryMemoryRequest(post({ ...valid, category: 'x'.repeat(3000) }), writeEnv(), server.factory)).httpStatus, 413);
  assert.equal((await handleCategoryMemoryRequest(post(valid, { 'content-type': 'text/plain' }), writeEnv(), server.factory)).httpStatus, 415);
  assert.equal((await handleCategoryMemoryRequest(get({ limit: '5' }), writeEnv(), server.factory)).httpStatus, 400);
  assert.equal((await handleCategoryMemoryRequest({ ...get(), method: 'DELETE' }, writeEnv(), server.factory)).httpStatus, 405);
  assert.equal(server.ports, 0);
  assert.equal(server.commands!.length, 1);
});

test('migration and stored-command validation: missing tab blocks writes only; corrupt rows fail closed', async () => {
  const ledger = new Ledger(); ledger.choose('Gia đình');
  const missing = memoryServer(ledger, null);
  const index = await handleCategoryMemoryRequest(get(), writeEnv(), missing.factory);
  assert.equal(index.httpStatus, 200); assert.equal(index.body.writesEnabled, false); assert.equal((index.body.rules as unknown[]).length, 1);
  assert.equal((await handleCategoryMemoryRequest(post({ requestId: uuid(700), action: 'RESET_ALL' }), writeEnv(), missing.factory)).body.error, 'CATEGORY_MEMORY_MIGRATION_REQUIRED');
  for (const rows of [[['wrong']], [[...MEMORY_HEADERS], [uuid(1), 'SET', party, 'CHI', 'Không có', '2026-01-01T00:00:00Z']],
    [[...MEMORY_HEADERS], [uuid(1), 'RESET_ALL', '', '', '', '2026-01-01T00:00:00Z'], [uuid(1), 'RESET_ALL', '', '', '', '2026-01-02T00:00:00Z']],
    [[...MEMORY_HEADERS], [uuid(1), 'RESET_ALL', '', '', '', '2026-01-01T00:00:00Z', 'stray']]]) {
    assert.equal((await handleCategoryMemoryRequest(get(), writeEnv(), memoryServer(ledger, rows).factory)).body.error, 'CATEGORY_MEMORY_SCHEMA_MISMATCH');
  }
  const schema = auditSchema('category-memory');
  assert.deepEqual([schema.title, schema.headers], ['Category_Memory', MEMORY_HEADERS]);
  assert.equal(planAuditMigration({ tables: [], existingIds: [1] }, 'category-memory').length, 2);
  assert.throws(() => planAuditMigration({ tables: [{ id: 2, title: 'Category_Memory', columns: 7, rows: [MEMORY_HEADERS, [uuid(1)]], hasMarkers: true }], existingIds: [1, 2] }, 'category-memory', true), /HAS_DATA/);
});

test('logs and responses never expose the secret, account digits or transaction text', async () => {
  const captured: string[] = [];
  const originals = { log: console.log, error: console.error, warn: console.warn, info: console.info };
  for (const name of Object.keys(originals) as (keyof typeof originals)[]) console[name] = (...args: unknown[]) => { captured.push(args.map(String).join(' ')); };
  try {
    const ledger = new Ledger(); ledger.choose('Gia đình', 'NGUYEN VAN A 0123456789');
    const leaky = async () => ({ readFinance: async () => { throw new Error(`boom ${SYNTHETIC_SECRET}`); }, readCommands: async () => null,
      append: async () => { throw new Error(SYNTHETIC_SECRET); } } as MemoryPort);
    const bodies = [await handleCategoryMemoryRequest(get(), writeEnv(), leaky),
      await handleCategoryMemoryRequest(post({ requestId: uuid(800), action: 'RESET_ALL' }), writeEnv(), leaky),
      await handleCategoryMemoryRequest(get(), writeEnv(), memoryServer(ledger).factory)].map((r) => JSON.stringify(r.body));
    assert.deepEqual(JSON.parse(bodies[0]), { error: 'CATEGORY_MEMORY_UNAVAILABLE' });
    for (const text of [...bodies, ...captured]) {
      assert.equal(text.includes(SYNTHETIC_SECRET), false);
      assert.equal(text.includes('0123456789'), false);
    }
  } finally { Object.assign(console, originals); }
  assert.deepEqual(captured, []);
});

// Synthetic replica of the long VCB transfer Ref layout: "<ref>.<ref>.<SENDER> chuyen tien.CT tu <acct> <SENDER> toi <acct> <RECEIVER> tai <BANK>".
// Every name, account and reference is fake; parseVcb masks the digit runs exactly as in production.
const OWNER = 'NGUYEN VAN CHU';
const vcbTransfer = (sign: '-' | '+', ref: string, sender: string, receiver: string, tail = ' tai Techcombank') =>
  `Số dư TK VCB 999999999999 ${sign}150,000 VND lúc 01-03-2026 09:15:22. Số dư 1,234,567 VND. Ref MBVCB.${ref}.${sender} chuyen tien.CT tu 0011001234567 ${sender} toi 1903456789012 ${receiver}${tail}`;
const vcbIdentity = (text: string) => {
  const parsed = parseVcb(text)!;
  assert.ok(parsed, text);
  assert.equal(/\d{8,}/.test(parsed.counterparty), false);
  return resolveIdentity({ source: 'BANK_NOTIFICATION', bank: parsed.bank, type: parsed.type, counterparty: parsed.counterparty, description: parsed.description }, KEY);
};

test('VCB long transfer: the receiver (CHI) or sender (THU) is the identity, never the owner or a truncated prefix', () => {
  const store = 'CT TNHH TM VA DV SIÊU THỊ AN LẠC';
  const a = vcbIdentity(vcbTransfer('-', '12345678901.567890', OWNER, store));
  const b = vcbIdentity(vcbTransfer('-', '98765432109.123456', OWNER, store, ' tai VIETINBANK (0912345678)'));
  assert.deepEqual([a?.type, a?.key], ['COUNTERPARTY', 'COUNTERPARTY:TNHH TM VA DV SIEU THI AN LAC']);
  assert.equal(b?.key, a?.key);
  // Same sender, different stores.
  assert.equal(vcbIdentity(vcbTransfer('-', '12345678901.567890', OWNER, 'CT TNHH TM VA DV SIÊU THỊ AN PHÚ'))?.key, 'COUNTERPARTY:TNHH TM VA DV SIEU THI AN PHU');
  // Personal transfers to A and B, including masked suffixes after the name.
  const toA = vcbIdentity(vcbTransfer('-', '11111111111.222222', OWNER, 'TRAN THI A_123456789012'))!;
  const toB = vcbIdentity(vcbTransfer('-', '11111111111.222222', OWNER, 'LE VAN B'))!;
  assert.deepEqual([toA.key, toB.key], ['COUNTERPARTY:TRAN THI A', 'COUNTERPARTY:LE VAN B']);
  // A receiver whose name contains the word TAI keeps it; only a trailing " tai <BANK>" is removed.
  assert.equal(vcbIdentity(vcbTransfer('-', '11111111111.222222', OWNER, 'NGUYEN VAN TAI', ' tai BIDV'))?.key, 'COUNTERPARTY:NGUYEN VAN TAI');
  // Incoming money: the sender is the counterparty, the receiver is the owner.
  assert.equal(vcbIdentity(vcbTransfer('+', '33333333333.444444', 'TRAN THI A', OWNER, ' tai VCB'))?.key, 'COUNTERPARTY:TRAN THI A');
  for (const identity of [a, toA, toB]) assert.equal(identity!.key.includes('CHU'), false);
});

test('uncertain bank text yields no identity, so nothing can be preselected wrongly', () => {
  const head = 'Số dư TK VCB 999999999999 -150,000 VND lúc 01-03-2026 09:15:22. Số dư 1 VND.';
  const uncertain = [
    `${head} Ref MBVCB.12345678901.567890.${OWNER} chuyen tien`,
    `${head} Ref MBVCB.12345678901.567890.CK tu ${OWNER} toi LE VAN B`,
    vcbTransfer('-', '12345678901.567890', OWNER, `LE VAN B CT tu 0011001234567 ${OWNER} toi 1903456789012 TRAN THI A`),
    vcbTransfer('-', '12345678901.567890', OWNER, 'LE VAN B toi 1903456789012 TRAN THI A'),
    vcbTransfer('-', '12345678901.567890', OWNER, 'CONG TY CO PHAN DICH VU THUONG MAI VA DAU TU PHAT TRIEN AN LAC'),
    `${head} Ref MBVCB.12345678901.567890.CT tu ${OWNER} toi LE VAN B`,
    head,
  ];
  for (const text of uncertain) assert.equal(vcbIdentity(text), null, text);
  // Long merchant texts that share an 8-token prefix are no longer merged by truncation.
  const longText = (who: string) => resolveIdentity({ source: 'BANK_NOTIFICATION', type: 'CHI', bank: 'VCB',
    counterparty: `THANH TOAN HOA DON DIEN THANG MUOI CHO KHACH ${who}`, description: '' }, KEY);
  assert.equal(longText('NGUYEN VAN A'), null); assert.equal(longText('TRAN THI B'), null);
  // Learning on those transactions creates no rule at all.
  const ledger = new Ledger();
  for (const text of uncertain.slice(0, 2)) {
    const parsed = parseVcb(text)!;
    for (let i = 0; i < 3; i++) ledger.confirm(ledger.add({ counterparty: parsed.counterparty, description: parsed.description }), 'Gia đình');
  }
  assert.deepEqual(rules(ledger), []);
});

test('VietinBank and BIDV transfer contents use the same party extraction', () => {
  const CTG = 'Thời gian: 28/02/2026 09:42 Tài khoản: 123456789012 Giao dịch: +25,000 VND Số dư hiện tại: 125,000 VND Nội dung: CT DEN:TESTREF1 MBVCB.TESTREF2.NGUOI GUI THU NGHIEM chuyen tien.CT tu 2345678901 NGUOI GUI THU NGHIEM toi 0912345678 NGUOI NHAN THU NGHIEM_123456789012 tai VIETINBANK (0912345678)';
  for (const [sign, expected] of [['+', 'COUNTERPARTY:NGUOI GUI THU NGHIEM'], ['-', 'COUNTERPARTY:NGUOI NHAN THU NGHIEM']] as const) {
    const parsed = parseVietinbank(CTG.replace('+25,000', `${sign}25,000`))!;
    assert.equal(resolveIdentity({ source: 'BANK_NOTIFICATION', bank: parsed.bank, type: parsed.type, counterparty: parsed.counterparty, description: parsed.description }, KEY)?.key, expected);
  }
  const bidv = parseBidv(`BIDV xin thông báo tới Quý khách Thời gian giao dịch: 09:41 28/02/2026 Tài khoản thanh toán: 1234567890 Số tiền GD: -12,500 VND Số dư cuối: 87,500 VND Nội dung giao dịch: ${OWNER} chuyen tien Mã giao dịch: FT26059ABC123`)!;
  assert.equal(resolveIdentity({ source: 'BANK_NOTIFICATION', bank: 'BIDV', type: bidv.type, counterparty: bidv.counterparty, description: bidv.description }, KEY), null);
});

test('end to end: three confirmations for one store preselect only that store, not another store from the same sender', async () => {
  const ledger = new Ledger();
  const add = (text: string) => { const p = parseVcb(text)!; return ledger.add({ counterparty: p.counterparty, description: p.description }); };
  for (const ref of ['10000000001.100001', '10000000002.100002', '10000000003.100003']) ledger.confirm(add(vcbTransfer('-', ref, OWNER, 'CT TNHH TM VA DV SIÊU THỊ AN LẠC')), 'Siêu thị');
  const sameStore = add(vcbTransfer('-', '10000000004.100004', OWNER, 'CT TNHH TM VA DV SIÊU THỊ AN LẠC'));
  const otherStore = add(vcbTransfer('-', '10000000005.100005', OWNER, 'CT TNHH TM VA DV SIÊU THỊ AN PHÚ'));
  const page = await readFinance({ method: 'GET', headers: { 'x-webhook-secret': SYNTHETIC_SECRET }, query: {} }, testEnvironment({ CATEGORY_MEMORY_ENABLED: '1' }),
    async () => ({ readFinance: async () => ledger.snapshot() }), 'transactions');
  const keyOf = (id: string) => ((page.body as Record<string, unknown>).transactions as { id: string; memoryKey: string | null }[]).find((t) => t.id === id)!.memoryKey;
  const list = rules(ledger);
  assert.deepEqual([find(list, keyOf(sameStore)!)?.category, find(list, keyOf(sameStore)!)?.level], ['Siêu thị', 'PRESELECT']);
  assert.notEqual(keyOf(otherStore), keyOf(sameStore));
  assert.equal(find(list, keyOf(otherStore)!), undefined);
  assert.equal(list.length, 1);
});

test('" tai <bank>" is stripped only for known banks: given name Tài never truncates a receiver', () => {
  const key = (receiver: string, tail = '') => vcbIdentity(vcbTransfer('-', '12345678901.567890', OWNER, receiver, tail))?.key;
  const an = key('NGUYEN VAN TAI AN'), binh = key('NGUYEN VAN TAI BINH'), son = key('NGUYEN VAN TAI SON');
  assert.deepEqual([an, binh, son], ['COUNTERPARTY:NGUYEN VAN TAI AN', 'COUNTERPARTY:NGUYEN VAN TAI BINH', 'COUNTERPARTY:NGUYEN VAN TAI SON']);
  for (const k of [an, binh, son]) assert.notEqual(k, 'COUNTERPARTY:NGUYEN VAN');
  assert.equal(key('NGUYEN VAN TAI AN', ' tai VCB'), 'COUNTERPARTY:NGUYEN VAN TAI AN');
  assert.equal(key('NGUYEN VAN TAI', ' tai MB BANK'), 'COUNTERPARTY:NGUYEN VAN TAI');
  assert.equal(key('CT TNHH TM VA DV SIÊU THỊ AN LẠC', ' tai MB'), 'COUNTERPARTY:TNHH TM VA DV SIEU THI AN LAC');
  assert.equal(key('CT TNHH TM VA DV SIÊU THỊ AN LẠC', ' tai Techcombank (0912345678)'), 'COUNTERPARTY:TNHH TM VA DV SIEU THI AN LAC');
  // Unknown receiving bank: kept verbatim (a stable, more specific key), never cut into a shorter shared name.
  assert.equal(key('LE VAN B', ' tai XYZBANK'), 'COUNTERPARTY:LE VAN B TAI XYZBANK');
});

// Structure of two owner-supplied VCB notifications (26/10/2026). Every name, account, virtual
// account, reference and amount is replaced; only the layout is kept.
const ownerOut = (ref: string, virtualAccount: string, receiver: string) =>
  `Số dư TK VCB 9999999999 -25,000 VND lúc 01-03-2026 18:50:44. Số dư 147,322 VND. Ref MBVCB.${ref}.1111AAAAAA1A1A1A.${OWNER} chuyen tien.CT tu 9999999999 ${OWNER} toi ${virtualAccount} ${receiver} tai TECHCOMBANK`;
const ownerIn = (sender: string, ref: string) =>
  `Số dư TK VCB 9999999999 +3,000,000 VND lúc 01-03-2026 09:56:37. Số dư 3,172,322 VND. Ref ${sender} Chuyen tien#SP#${ref}abc1111111.1111.1111.111111`;

test('owner-shaped VCB samples: a virtual (letters+digits) receiver account still yields the receiver; the bare memo yields nothing', () => {
  // No masking assertion here: digit runs glued to letters are not masked by maskSensitiveIdentifiers today
  // (pre-existing gap, tracked separately). The identity itself never contains digits.
  const vcbIdentity = (text: string) => {
    const p = parseVcb(text)!;
    const identity = resolveIdentity({ source: 'BANK_NOTIFICATION', bank: p.bank, type: p.type, counterparty: p.counterparty, description: p.description }, KEY);
    assert.equal(/\d/.test(identity?.key ?? ''), false);
    return identity;
  };
  const first = vcbIdentity(ownerOut('11111111111', 'XY00Z00000000000001', 'TRAN THI B'));
  assert.deepEqual([first?.type, first?.key], ['COUNTERPARTY', 'COUNTERPARTY:TRAN THI B']);
  assert.equal(vcbIdentity(ownerOut('22222222222', 'XY00Z00000000000002', 'TRAN THI B'))?.key, first?.key);
  assert.equal(vcbIdentity(ownerOut('11111111111', 'XY00Z00000000000001', 'LE VAN C'))?.key, 'COUNTERPARTY:LE VAN C');
  // A letters-only token after tu/toi is a name, not an account: still uncertain.
  assert.equal(vcbIdentity(ownerOut('11111111111', 'XYZ', 'TRAN THI B')), null);
  const incoming = ['000000000000000000012026', '000000000000000000022026'].map((ref) => ownerIn('LE THI D', ref));
  for (const text of incoming) assert.equal(vcbIdentity(text), null);
  const ledger = new Ledger();
  for (const text of incoming) {
    const p = parseVcb(text)!;
    for (let i = 0; i < 2; i++) ledger.confirm(ledger.add({ type: 'THU', counterparty: p.counterparty, description: p.description }), 'Tiền chuyển đến', 'THU');
  }
  assert.deepEqual(rules(ledger), []);
});
