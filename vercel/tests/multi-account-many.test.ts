import test from 'node:test';
import assert from 'node:assert/strict';
import { validateAndProcessTransaction } from '../lib/transaction-service.js';
import { fromFinanceSnapshot } from '../lib/unified-transactions.js';
import { buildDashboard } from '../lib/dashboard-service.js';
import { parseFinanceQuery } from '../lib/finance-query.js';
import { ownAccountDigest, memoryIdentityKey, IDENTITY_HEADERS, MEMORY_HEADERS } from '../lib/category-memory.js';
import type { MemoryRule } from '../lib/category-memory.js';
import { handleCategoryMemoryRequest } from '../lib/category-memory-service.js';
import type { MemoryPort } from '../lib/category-memory-service.js';
import { readOwnAccounts } from '../lib/configuration.js';
import { SYNTHETIC_SECRET, testEnvironment } from './support.js';
import { TRANSACTION_HEADERS_V2, RAW_CAPTURE_HEADERS } from '../lib/sheet-schema.js';
import { HISTORY_HEADERS } from '../lib/moneylover/schema.js';
import { createEventId } from '../event-identity.js';

// Synthetic accounts only: the owner has one VCB and two BIDV accounts.
const HMAC_SECRET = 's'.repeat(48);
const VCB_ACCT = '0011004068999';
const VCB_ACCT_2 = '0011004068777';
const BIDV_ACCT_1 = '1234567890999';
const BIDV_ACCT_2 = '1234567890777';
const OTHER_ACCT = '9876543210999';

const hmacBuffer = memoryIdentityKey(HMAC_SECRET);
const vcbDigest = ownAccountDigest(VCB_ACCT, hmacBuffer);
const vcbDigest2 = ownAccountDigest(VCB_ACCT_2, hmacBuffer);
const bidvDigest1 = ownAccountDigest(BIDV_ACCT_1, hmacBuffer);
const bidvDigest2 = ownAccountDigest(BIDV_ACCT_2, hmacBuffer);
const otherDigest = ownAccountDigest(OTHER_ACCT, hmacBuffer);

const NEW_CONFIG = {
  VCB: { digest: vcbDigest, label: 'VCB' },
  BIDV: { digests: [bidvDigest1, bidvDigest2], label: 'BIDV' },
};
const OWN_ACCOUNTS = readOwnAccounts({ OWN_ACCOUNT_DIGESTS: JSON.stringify(NEW_CONFIG) });
const env = (config: unknown = NEW_CONFIG) => testEnvironment({
  CATEGORY_MEMORY_HMAC_KEY: HMAC_SECRET,
  OWN_ACCOUNT_DIGESTS: JSON.stringify(config),
});
const own = (config: unknown) => readOwnAccounts({ OWN_ACCOUNT_DIGESTS: JSON.stringify(config) });

function mockSheetsPort(appState: { transactions: unknown[][]; raw: unknown[][]; identities: unknown[][] }) {
  return async () => ({
    readHeaders: async () => ({ transaction: [...TRANSACTION_HEADERS_V2], raw: [...RAW_CAPTURE_HEADERS] }),
    readEventIds: async () => ({ transactionIds: appState.transactions.map((r) => r[8]), rawIds: appState.raw.map((r) => r[3]) }),
    appendTransaction: async (row: unknown[], identity?: unknown[] | null) => {
      appState.transactions.push(row);
      if (identity) appState.identities.push(identity);
      return 'APPENDED' as const;
    },
    appendRaw: async (row: unknown[]) => { appState.raw.push(row); return 'APPENDED' as const; },
  });
}

const vcbChiText = (to: string, amount = '5,000,000', time = '10:00:00', ref = 'TEST') =>
  `Số dư TK VCB ${VCB_ACCT} -${amount} VND lúc 29-10-2026 ${time}. Số dư 10,000,000 VND. Ref MBVCB.${ref}.CT tu ${VCB_ACCT} NGUYEN VAN A toi ${to} NGUYEN VAN A tai BIDV`;

async function ingest(appState: Parameters<typeof mockSheetsPort>[0], bank: 'VCB' | 'BIDV', text: string, postedAt: number, environment = env()) {
  const eventId = createEventId(bank, postedAt, text);
  const packageName = bank === 'VCB' ? 'com.VCB' : 'com.vnpay.bidv';
  return validateAndProcessTransaction(
    { method: 'POST', headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' },
      body: { eventId, text, postedAt, bank, packageName, title: bank } },
    environment, mockSheetsPort(appState),
  );
}
const freshState = () => ({ transactions: [] as unknown[][], raw: [] as unknown[][], identities: [] as unknown[][] });

test('readOwnAccounts: dạng cũ {digest,label} vẫn đọc được như trước (hồi quy)', () => {
  assert.deepEqual(own({ VCB: { digest: vcbDigest, label: ' VCB ••8999 ' }, BIDV: { digest: bidvDigest1, label: 'BIDV' } }), {
    VCB: { digests: [vcbDigest], label: 'VCB ••8999' },
    BIDV: { digests: [bidvDigest1], label: 'BIDV' },
  });
});

test('readOwnAccounts: dạng mới {digests:[...],label}, loại trùng, bỏ digest sai, tối đa 8 digest mỗi ngân hàng', () => {
  assert.deepEqual(OWN_ACCOUNTS, { VCB: { digests: [vcbDigest], label: 'VCB' }, BIDV: { digests: [bidvDigest1, bidvDigest2], label: 'BIDV' } });
  // Wrong-format digests are dropped one by one; the valid one still counts.
  assert.deepEqual(own({ BIDV: { digests: ['short', bidvDigest2.toUpperCase(), 42, null, bidvDigest2, bidvDigest2], label: 'BIDV' } }),
    { BIDV: { digests: [bidvDigest2], label: 'BIDV' } });
  // Both fields given: union, no duplicates.
  assert.deepEqual(own({ BIDV: { digest: bidvDigest1, digests: [bidvDigest1, bidvDigest2], label: 'BIDV' } }),
    { BIDV: { digests: [bidvDigest1, bidvDigest2], label: 'BIDV' } });
  const many = Array.from({ length: 10 }, (_, i) => ownAccountDigest(`55500000${i}`, hmacBuffer));
  assert.deepEqual(own({ BIDV: { digests: many, label: 'BIDV' } }).BIDV?.digests, many.slice(0, 8));
});

test('readOwnAccounts: mảng rỗng/không còn digest hợp lệ bỏ cả ngân hàng; JSON hỏng hoặc nhãn sai -> {} không lỗi', () => {
  assert.deepEqual(own({ VCB: { digest: vcbDigest, label: 'VCB' }, BIDV: { digests: [], label: 'BIDV' } }), { VCB: { digests: [vcbDigest], label: 'VCB' } });
  assert.deepEqual(own({ BIDV: { digests: ['bad', ''], label: 'BIDV' } }), {});
  assert.deepEqual(own({ BIDV: { digests: bidvDigest1, label: 'BIDV' } }), {});
  assert.deepEqual(own({ BIDV: { digests: [bidvDigest1], label: 'BIDV|1' } }), {});
  assert.deepEqual(own({ BIDV: { digests: [bidvDigest1], label: '' } }), {});
  assert.deepEqual(readOwnAccounts({ OWN_ACCOUNT_DIGESTS: '{"BIDV":{"digests":[' }), {});
});

test('Ingest dạng mới: VCB CHI tới digest thứ nhất và thứ hai của BIDV đều thành Chuyển nội bộ, K = nhãn VCB', async () => {
  for (const [to, postedAt] of [[BIDV_ACCT_1, 1793268000000], [BIDV_ACCT_2, 1793271600000]] as const) {
    const appState = freshState();
    const res = await ingest(appState, 'VCB', vcbChiText(to), postedAt);
    assert.equal(res.httpStatus, 200);
    assert.equal(res.body.status, 'SAVED_TRANSACTION');
    assert.equal(appState.transactions[0][7], 'Chuyển nội bộ');
    assert.equal(appState.transactions[0][10], 'VCB');
  }
});

test('Ingest: chuyển giữa hai tài khoản CÙNG ngân hàng của chủ không coi là chuyển nội bộ liên ngân hàng (giữ hành vi cũ)', async () => {
  const config = { VCB: { digests: [vcbDigest, vcbDigest2], label: 'VCB' }, BIDV: { digests: [bidvDigest1, bidvDigest2], label: 'BIDV' } };
  const appState = freshState();
  const text = `Số dư TK VCB ${VCB_ACCT} -5,000,000 VND lúc 29-10-2026 10:00:00. Số dư 10,000,000 VND. Ref MBVCB.SAME.CT tu ${VCB_ACCT} NGUYEN VAN A toi ${VCB_ACCT_2} NGUYEN VAN A tai VCB`;
  await ingest(appState, 'VCB', text, 1793268000000, env(config));
  assert.equal(appState.identities[0][1], `ACCOUNT:${vcbDigest2}`);
  assert.notEqual(appState.transactions[0][7], 'Chuyển nội bộ');
  // The read model does not use it as an anchor either.
  const rows = fromFinanceSnapshot({
    bank: [TRANSACTION_HEADERS_V2, ...appState.transactions],
    history: [HISTORY_HEADERS],
    identities: [IDENTITY_HEADERS, ...appState.identities],
  }, own(config));
  assert.equal(rows[0].internalTransfer, null);
  assert.equal(rows[0].excludedFromReport, false);
});

test('Ghép cặp tầng đọc: VCB CHI tới digest thứ hai + BIDV THU cùng số tiền trong 30 phút -> ghép, Thu = Chi = Net = 0', async () => {
  const appState = freshState();
  await ingest(appState, 'VCB', vcbChiText(BIDV_ACCT_2), 1793268000000);
  const bidvText = `BIDV xin thông báo tới Quý khách Thời gian giao dịch: 10:05 29/10/2026 Tài khoản thanh toán: ${BIDV_ACCT_2} Số tiền GD: +5,000,000 VND Số dư cuối: 15,000,000 VND Nội dung giao dịch: NGUYEN VAN A chuyen tien Mã giao dịch: BIDV12345`;
  await ingest(appState, 'BIDV', bidvText, 1793268300000);

  const rows = fromFinanceSnapshot({
    bank: [TRANSACTION_HEADERS_V2, ...appState.transactions],
    history: [HISTORY_HEADERS],
    identities: [IDENTITY_HEADERS, ...appState.identities],
  }, OWN_ACCOUNTS);
  const vcbTx = rows.find((r) => r.bank === 'VCB')!;
  const bidvTx = rows.find((r) => r.bank === 'BIDV')!;
  assert.ok(vcbTx.internalTransfer && bidvTx.internalTransfer);
  assert.equal(vcbTx.internalTransfer.role, 'OUT');
  assert.equal(bidvTx.internalTransfer.role, 'IN');
  assert.equal(vcbTx.internalTransfer.pairId, bidvTx.internalTransfer.pairId);
  assert.equal(bidvTx.category, 'Chuyển nội bộ');

  const dashboard = buildDashboard(rows, parseFinanceQuery({ from: '2026-10-01', to: '2026-10-31' }, true));
  assert.deepEqual([dashboard.income, dashboard.expense, dashboard.net], [0, 0, 0]);
});

test('Category Memory: cả hai digest BIDV của chủ bị loại khỏi memory index, đối tác ngoài vẫn còn', async () => {
  const commands: unknown[][] = [[...MEMORY_HEADERS],
    ...[bidvDigest1, bidvDigest2, otherDigest].map((digest, i) => [
      `00000000-0000-4000-8000-00000000000${i}`, 'SET', `ACCOUNT:${digest}`, 'CHI', 'Ăn uống', '2026-10-29T10:00:00.000Z',
    ])];
  const port: MemoryPort = {
    readFinance: async () => ({ bank: [[...TRANSACTION_HEADERS_V2]], history: [[...HISTORY_HEADERS]] }),
    readCommands: async () => structuredClone(commands),
    append: async () => 'APPENDED',
  };
  const res = await handleCategoryMemoryRequest(
    { method: 'GET', headers: { 'x-webhook-secret': SYNTHETIC_SECRET }, query: {} },
    testEnvironment({ CATEGORY_MEMORY_ENABLED: '1', CATEGORY_MEMORY_HMAC_KEY: HMAC_SECRET, OWN_ACCOUNT_DIGESTS: JSON.stringify(NEW_CONFIG) }),
    async () => port,
  );
  assert.equal(res.httpStatus, 200);
  const keys = (res.body.rules as MemoryRule[]).map((r) => r.key);
  assert.equal(keys.includes(`ACCOUNT:${bidvDigest1}`), false);
  assert.equal(keys.includes(`ACCOUNT:${bidvDigest2}`), false);
  assert.equal(keys.includes(`ACCOUNT:${otherDigest}`), true);
});
