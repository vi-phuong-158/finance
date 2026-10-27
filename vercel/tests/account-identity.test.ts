import test from 'node:test';
import assert from 'node:assert/strict';
import { createEventId } from '../event-identity.js';
import { accountIdentity, accountIdentityKey, extractCounterpartyAccount, IDENTITY_HEADERS, memoryObservations, originalIdentities } from '../lib/category-memory.js';
import { atomicAppendRequests } from '../lib/atomic-append.js';
import { auditSchema } from '../lib/audit-migration.js';
import { readFinance } from '../lib/finance-read-service.js';
import { validateAndProcessTransaction, RAW_CAPTURE_HEADERS, TRANSACTION_HEADERS } from '../lib/transaction-service.js';
import { EDIT_HEADERS } from '../lib/edit-schema.js';
import { TRANSACTION_HEADERS_V2 } from '../lib/sheet-schema.js';
import { HISTORY_HEADERS } from '../lib/moneylover/schema.js';
import { SYNTHETIC_SECRET, testEnvironment } from './support.js';

// Every name, account and reference below is synthetic.
const HMAC_SECRET = 'k'.repeat(48);
const KEY = accountIdentityKey(testEnvironment({ CATEGORY_MEMORY_HMAC_KEY: HMAC_SECRET }))!;
const OWNER = 'NGUYEN VAN CHU';
const transfer = (sign: '-' | '+', sender: string, senderAccount: string, receiver: string, receiverAccount: string, tail = ' tai Techcombank') =>
  `Số dư TK VCB 999999999999 ${sign}150,000 VND lúc 01-03-2026 09:15:22. Số dư 1,234,567 VND. Ref MBVCB.12345678901.567890.${sender} chuyen tien.CT tu ${senderAccount} ${sender} toi ${receiverAccount} ${receiver}${tail}`;

test('the counterparty account is taken from the raw transfer text: receiver for CHI, sender for THU', () => {
  assert.deepEqual(extractCounterpartyAccount(transfer('-', OWNER, '0011001234567', 'TRAN THI A', '1903456789012'), 'CHI'),
    { account: '1903456789012', bank: 'TECHCOMBANK' });
  assert.deepEqual(extractCounterpartyAccount(transfer('+', 'TRAN THI A', '1903456789012', OWNER, '0011001234567', ''), 'THU'),
    { account: '1903456789012', bank: null });
  // Virtual accounts mixing letters and digits are kept as one token.
  assert.equal(extractCounterpartyAccount(transfer('-', OWNER, '0011001234567', 'SHOP', 'VQR123456789AB'), 'CHI')?.account, 'VQR123456789AB');
  // Ambiguous or already-masked text never yields an account.
  for (const text of [
    transfer('-', OWNER, '0011001234567', 'TRAN THI A', '***'),
    transfer('-', OWNER, '0011001234567', 'TRAN THI A', '1903456789012') + ' CT tu 0011001234567 X toi 5555555555 Y',
    transfer('-', OWNER, '0011001234567', 'TRAN THI A toi 5555555555 LE VAN B', '1903456789012'),
    transfer('-', OWNER, '0011001234567', 'TRAN THI A', '0011001234567'),
    transfer('-', OWNER, '0011001234567', 'TRAN THI A', '12345'),
    'Số dư TK VCB 999999999999 -150,000 VND lúc 01-03-2026 09:15:22. Số dư 1 VND. Ref MBVCB.1.2.GRAB',
  ]) assert.equal(extractCounterpartyAccount(text, 'CHI'), null, text);
});

test('review repro: two accounts sharing one receiver name get two different keyed identities', () => {
  const a = accountIdentity(transfer('-', OWNER, '0011001234567', 'TRAN THI A', '1903456789012'), 'CHI', KEY)!;
  const b = accountIdentity(transfer('-', OWNER, '0011001234567', 'TRAN THI A', '1903456789099'), 'CHI', KEY)!;
  const again = accountIdentity(transfer('-', OWNER, '0011001234567', 'Trần Thị A', '1903456789012', ' tai VIETINBANK (0912345678)'), 'CHI', KEY)!;
  assert.notEqual(a.key, b.key);
  // Same account: same key whatever the name spelling or receiving bank text (decision Q2: digits only).
  assert.equal(again.key, a.key);
  assert.match(a.key, /^ACCOUNT:[a-f0-9]{32}$/);
  assert.equal(a.type, 'ACCOUNT');
  assert.equal(a.label, 'TRAN THI A · TECHCOMBANK ••9012');
  for (const identity of [a, b, again]) assert.equal(`${identity.key} ${identity.label}`.includes('19034567890'), false);
  // Another key yields unrelated identities, so a leaked Sheet cannot be matched against guessed accounts without it.
  const other = accountIdentityKey(testEnvironment({ CATEGORY_MEMORY_HMAC_KEY: 'z'.repeat(48) }))!;
  assert.notEqual(accountIdentity(transfer('-', OWNER, '0011001234567', 'TRAN THI A', '1903456789012'), 'CHI', other)!.key, a.key);
});

test('the account key is a dedicated secret, never the webhook secret, and is optional', () => {
  assert.ok(KEY);
  assert.equal(accountIdentityKey(testEnvironment()), null);
  assert.equal(accountIdentityKey(testEnvironment({ CATEGORY_MEMORY_HMAC_KEY: SYNTHETIC_SECRET })), null);
  for (const bad of ['short', 'replace-me-with-a-real-key-000000000000', 'x'.repeat(129), `${'x'.repeat(40)} `]) {
    assert.equal(accountIdentityKey(testEnvironment({ CATEGORY_MEMORY_HMAC_KEY: bad })), null, bad);
  }
});

test('the identity row rides in the same atomic batch as the transaction', () => {
  const eventId = 'e'.repeat(64);
  const requests = atomicAppendRequests(1, eventId, ['t'], { sheetId: 7, row: [eventId, 'ACCOUNT:' + 'a'.repeat(32), 'X ••1234', '2026-03-01T00:00:00.000Z'] });
  assert.equal(requests.length, 3);
  assert.deepEqual(requests.map((r) => Object.keys(r)[0]), ['addNamedRange', 'appendCells', 'appendCells']);
  assert.equal(requests[2].appendCells?.sheetId, 7);
  assert.throws(() => atomicAppendRequests(1, eventId, ['t'], { sheetId: 1, row: ['x'] }), /INVALID_APPEND/);
  assert.equal(atomicAppendRequests(1, eventId, ['t']).length, 2);
});

const time = Date.UTC(2026, 2, 1, 2, 15);
async function ingest(text: string, env: Record<string, string | undefined>, options: { dryRun?: boolean } = {}) {
  const writes: { transaction?: unknown[]; identity?: unknown[] | null; raw?: unknown[] } = {};
  const result = await validateAndProcessTransaction({ method: 'POST', headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' },
    body: { eventId: createEventId('VCB', time, text), bank: 'VCB', packageName: 'com.VCB', title: 'VCB', text, postedAt: time, dryRun: options.dryRun ?? false } }, env, async () => ({
    readHeaders: async () => ({ transaction: [...TRANSACTION_HEADERS], raw: [...RAW_CAPTURE_HEADERS] }),
    readEventIds: async () => ({ transactionIds: [], rawIds: [] }),
    appendTransaction: async (row: unknown[], identity?: unknown[] | null) => { writes.transaction = row; writes.identity = identity ?? null; },
    appendRaw: async (row: unknown[]) => { writes.raw = row; },
  }));
  return { result, writes };
}

test('ingest stores only the keyed identity, before masking, and only when the dedicated key is configured', async () => {
  const text = transfer('-', OWNER, '0011001234567', 'TRAN THI A', '1903456789012');
  const env = testEnvironment({ CATEGORY_MEMORY_HMAC_KEY: HMAC_SECRET });
  const { result, writes } = await ingest(text, env);
  assert.equal(result.body.status, 'SAVED_TRANSACTION');
  const identity = writes.identity!;
  assert.equal(identity[0], createEventId('VCB', time, text));
  assert.equal(identity[1], accountIdentity(text, 'CHI', KEY)!.key);
  assert.equal(identity[2], 'TRAN THI A · TECHCOMBANK ••9012');
  assert.match(String(identity[3]), /^\d{4}-\d{2}-\d{2}T/);
  for (const value of [...writes.transaction!, ...identity]) assert.equal(String(value).includes('1903456789012'), false);
  // No key, a non-transfer, a dry run: the transaction path is unchanged and no identity is written.
  assert.equal((await ingest(text, testEnvironment())).writes.identity, null);
  assert.equal((await ingest('Số dư TK VCB 999999999999 -150,000 VND lúc 01-03-2026 09:15:22. Số dư 1 VND. Ref MBVCB.1.2.GRAB', env)).writes.identity, null);
  const dry = await ingest(text, env, { dryRun: true });
  assert.equal(dry.result.body.status, 'VALIDATED_TRANSACTION'); assert.equal(dry.writes.transaction, undefined);
});

function snapshot(identities?: unknown[][]) {
  const bank: unknown[][] = [[...TRANSACTION_HEADERS_V2]];
  const ids = [1, 2, 3].map((n) => n.toString(16).padStart(64, '0'));
  ids.forEach((id, n) => bank.push([`01/03/2026 09:1${n}`, 'VCB', 'CHI', 1000 + n, '', 'TRAN THI A', 'x', 'Chưa phân loại', id, 'BANK_NOTIFICATION', 'VCB']));
  return { ids, value: { bank, history: [[...HISTORY_HEADERS]], edits: [[...EDIT_HEADERS]], ...(identities ? { identities } : {}) } };
}

test('read side: a stored account identity wins over the shared name; other rows keep the name tier', async () => {
  const first = accountIdentity(transfer('-', OWNER, '0011001234567', 'TRAN THI A', '1903456789012'), 'CHI', KEY)!;
  const second = accountIdentity(transfer('-', OWNER, '0011001234567', 'TRAN THI A', '1903456789099'), 'CHI', KEY)!;
  const ids = snapshot().ids;
  const { value } = snapshot([[...IDENTITY_HEADERS], [ids[0], first.key, first.label, '2026-03-01T00:00:00.000Z'], [ids[1], second.key, second.label, '2026-03-01T00:00:00.000Z'],
    // An identity for a transaction that is not in the ledger is ignored.
    ['f'.repeat(64), first.key, first.label, '2026-03-01T00:00:00.000Z']]);
  const identities = originalIdentities(value);
  assert.deepEqual([identities.get(`bank:${ids[0]}`)?.key, identities.get(`bank:${ids[1]}`)?.key, identities.get(`bank:${ids[2]}`)?.key],
    [first.key, second.key, 'COUNTERPARTY:TRAN THI A']);
  assert.equal(memoryObservations(value).length, 0);
  const page = await readFinance({ method: 'GET', headers: { 'x-webhook-secret': SYNTHETIC_SECRET }, query: {} }, testEnvironment({ CATEGORY_MEMORY_ENABLED: '1' }),
    async () => ({ readFinance: async () => value }), 'transactions');
  const keys = new Map(((page.body as Record<string, unknown>).transactions as { id: string; memoryKey: string }[]).map((t) => [t.id, t.memoryKey]));
  assert.equal(keys.get(`bank:${ids[0]}`), first.key); assert.equal(keys.get(`bank:${ids[1]}`), second.key);
  // Without the tab everything still reads, as before.
  assert.equal(originalIdentities(snapshot().value).get(`bank:${ids[0]}`)?.key, 'COUNTERPARTY:TRAN THI A');
});

test('corrupt identity rows fail closed instead of guessing', async () => {
  const good = accountIdentity(transfer('-', OWNER, '0011001234567', 'TRAN THI A', '1903456789012'), 'CHI', KEY)!;
  const id = snapshot().ids[0];
  for (const rows of [
    [['wrong']],
    [[...IDENTITY_HEADERS], [id, 'ACCOUNT:VCB:' + 'a'.repeat(32), good.label, '2026-03-01T00:00:00.000Z']],
    [[...IDENTITY_HEADERS], [id, 'COUNTERPARTY:TRAN THI A', good.label, '2026-03-01T00:00:00.000Z']],
    [[...IDENTITY_HEADERS], ['not-an-event', good.key, good.label, '2026-03-01T00:00:00.000Z']],
    [[...IDENTITY_HEADERS], [id, good.key, good.label, '2026-03-01T00:00:00.000Z'], [id, good.key, good.label, '2026-03-01T00:00:00.000Z']],
    [[...IDENTITY_HEADERS], [id, good.key, 'bad|label', '2026-03-01T00:00:00.000Z']],
    [[...IDENTITY_HEADERS], [id, good.key, good.label, '2026-03-01T00:00:00.000Z', 'stray']],
  ]) {
    const page = await readFinance({ method: 'GET', headers: { 'x-webhook-secret': SYNTHETIC_SECRET }, query: {} }, testEnvironment({ CATEGORY_MEMORY_ENABLED: '1' }),
      async () => ({ readFinance: async () => snapshot(rows).value }), 'transactions');
    assert.equal(page.httpStatus, 503, JSON.stringify(rows));
  }
});

test('migration prepares the identity tab with its fixed headers', () => {
  assert.deepEqual(auditSchema('transaction-identity'), { title: 'Transaction_Identity', headers: IDENTITY_HEADERS });
  assert.deepEqual(IDENTITY_HEADERS, ['event_id', 'identity_key', 'label', 'created_at']);
});
