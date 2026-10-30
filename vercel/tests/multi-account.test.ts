import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { validateAndProcessTransaction } from '../lib/transaction-service.js';
import { fromFinanceSnapshot } from '../lib/unified-transactions.js';
import type { FinanceSnapshot } from '../lib/unified-transactions.js';
import { buildDashboard } from '../lib/dashboard-service.js';
import { parseFinanceQuery } from '../lib/finance-query.js';
import {
  buildMemoryIndex,
  accountIdentityKey,
  ownAccountDigest,
  memoryIdentityKey,
  IDENTITY_HEADERS,
  memoryObservations,
} from '../lib/category-memory.js';
import type { Observation } from '../lib/category-memory.js';
import { readOwnAccounts } from '../lib/configuration.js';
import { SYNTHETIC_SECRET, testEnvironment } from './support.js';
import { TRANSACTION_HEADERS_V2, RAW_CAPTURE_HEADERS } from '../lib/sheet-schema.js';
import { EDIT_HEADERS } from '../lib/edit-schema.js';
import { HISTORY_HEADERS, historyRow } from '../lib/moneylover/schema.js';
import { createEventId } from '../event-identity.js';
import { writeTransactionEdit } from '../lib/transaction-edits.js';
import { overlapCandidates } from '../lib/moneylover/overlap.js';
import { importMoneyLover } from '../lib/moneylover/importer.js';
import { parseMoneyLover } from '../lib/moneylover/parser.js';
import { sourceRow, sourceTable } from './moneylover-fixtures.js';

const HMAC_SECRET = 's'.repeat(48);
const VCB_ACCT = '0011004068999';
const BIDV_ACCT = '1234567890999';
const OTHER_ACCT = '9876543210999';

const hmacBuffer = memoryIdentityKey(HMAC_SECRET);
const vcbDigest = ownAccountDigest(VCB_ACCT, hmacBuffer);
const bidvDigest = ownAccountDigest(BIDV_ACCT, hmacBuffer);
const otherDigest = ownAccountDigest(OTHER_ACCT, hmacBuffer);

const OWN_CONFIG = {
  VCB: { digest: vcbDigest, label: 'VCB ••8999' },
  BIDV: { digest: bidvDigest, label: 'BIDV ••0999' },
};

const envWithOwnAccounts = (overrides: Record<string, string | undefined> = {}) => testEnvironment({
  CATEGORY_MEMORY_HMAC_KEY: HMAC_SECRET,
  OWN_ACCOUNT_DIGESTS: JSON.stringify(OWN_CONFIG),
  ...overrides,
});

function mockSheetsPort(appState: { transactions: unknown[][]; raw: unknown[][]; identities?: unknown[][] }) {
  return async () => ({
    readHeaders: async () => ({
      transaction: [...TRANSACTION_HEADERS_V2],
      raw: [...RAW_CAPTURE_HEADERS],
    }),
    readEventIds: async () => ({
      transactionIds: appState.transactions.map((r) => r[8]),
      rawIds: appState.raw.map((r) => r[3]),
    }),
    appendTransaction: async (row: unknown[], identity?: unknown[] | null) => {
      appState.transactions.push(row);
      if (identity && appState.identities) appState.identities.push(identity);
      return 'APPENDED' as const;
    },
    appendRaw: async (row: unknown[]) => {
      appState.raw.push(row);
      return 'APPENDED' as const;
    },
  });
}

test('T1: VCB CHI toi <TK BIDV của chủ> -> Chuyển nội bộ, không vào Tổng chi', async () => {
  const postedAt = 1793268000000;
  const vcbText = `Số dư TK VCB ${VCB_ACCT} -5,000,000 VND lúc 29-10-2026 10:00:00. Số dư 10,000,000 VND. Ref MBVCB.TEST.CT tu ${VCB_ACCT} NGUYEN VAN A toi ${BIDV_ACCT} NGUYEN VAN A tai BIDV`;
  const eventId = createEventId('VCB', postedAt, vcbText);
  const appState = { transactions: [] as unknown[][], raw: [] as unknown[][], identities: [] as unknown[][] };
  const port = mockSheetsPort(appState);

  const res = await validateAndProcessTransaction(
    {
      method: 'POST',
      headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' },
      body: { eventId, text: vcbText, postedAt, bank: 'VCB', packageName: 'com.VCB', title: 'VCB' },
    },
    envWithOwnAccounts(),
    port
  );

  assert.equal(res.httpStatus, 200);
  assert.equal(res.body.status, 'SAVED_TRANSACTION');
  assert.equal(appState.transactions.length, 1);
  const row = appState.transactions[0];
  assert.equal(row[1], 'VCB');
  assert.equal(row[2], 'CHI');
  assert.equal(row[3], 5_000_000);
  assert.equal(row[7], 'Chuyển nội bộ');
  assert.equal(row[8], eventId);
  assert.equal(row[9], 'BANK_NOTIFICATION');
  assert.equal(row[10], 'VCB ••8999');

  // Verify dashboard calculation: excluded from expense
  const rows = fromFinanceSnapshot({
    bank: [TRANSACTION_HEADERS_V2, row],
    history: [HISTORY_HEADERS],
    identities: [IDENTITY_HEADERS, ...(appState.identities ?? [])],
  }, OWN_CONFIG);

  assert.equal(rows[0].category, 'Chuyển nội bộ');
  assert.equal(rows[0].excludedFromReport, true);

  const query = parseFinanceQuery({ from: '2026-10-01', to: '2026-10-31' }, true);
  const dashboard = buildDashboard(rows, query);
  assert.equal(dashboard.expense, 0);
  assert.equal(dashboard.income, 0);
  assert.equal(dashboard.net, 0);
});

test('T2: T1 + BIDV THU cùng số tiền trong 30 phút -> Ghép cặp; Thu = Chi = Net = 0 cho cặp', async () => {
  const vcbPostedAt = 1793268000000;
  const vcbText = `Số dư TK VCB ${VCB_ACCT} -5,000,000 VND lúc 29-10-2026 10:00:00. Số dư 10,000,000 VND. Ref MBVCB.TEST.CT tu ${VCB_ACCT} NGUYEN VAN A toi ${BIDV_ACCT} NGUYEN VAN A tai BIDV`;
  const vcbEventId = createEventId('VCB', vcbPostedAt, vcbText);

  const bidvPostedAt = 1793268300000;
  const bidvText = `BIDV xin thông báo tới Quý khách Thời gian giao dịch: 10:05 29/10/2026 Tài khoản thanh toán: ${BIDV_ACCT} Số tiền GD: +5,000,000 VND Số dư cuối: 15,000,000 VND Nội dung giao dịch: NGUYEN VAN A chuyen tien Mã giao dịch: BIDV12345`;
  const bidvEventId = createEventId('BIDV', bidvPostedAt, bidvText);

  const appState = { transactions: [] as unknown[][], raw: [] as unknown[][], identities: [] as unknown[][] };
  const port = mockSheetsPort(appState);
  const env = envWithOwnAccounts();

  await validateAndProcessTransaction(
    { method: 'POST', headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' },
      body: { eventId: vcbEventId, text: vcbText, postedAt: vcbPostedAt, bank: 'VCB', packageName: 'com.VCB', title: 'VCB' } },
    env, port
  );
  await validateAndProcessTransaction(
    { method: 'POST', headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' },
      body: { eventId: bidvEventId, text: bidvText, postedAt: bidvPostedAt, bank: 'BIDV', packageName: 'com.vnpay.bidv', title: 'BIDV' } },
    env, port
  );

  const snapshot = {
    bank: [TRANSACTION_HEADERS_V2, ...appState.transactions],
    history: [HISTORY_HEADERS],
    identities: [IDENTITY_HEADERS, ...appState.identities],
  };

  const rows = fromFinanceSnapshot(snapshot, OWN_CONFIG);
  assert.equal(rows.length, 2);

  const vcbTx = rows.find((r) => r.bank === 'VCB')!;
  const bidvTx = rows.find((r) => r.bank === 'BIDV')!;

  assert.ok(vcbTx.internalTransfer);
  assert.ok(bidvTx.internalTransfer);
  assert.equal(vcbTx.internalTransfer.role, 'OUT');
  assert.equal(bidvTx.internalTransfer.role, 'IN');
  assert.equal(vcbTx.internalTransfer.pairId, bidvTx.internalTransfer.pairId);

  // Both should be Chuyển nội bộ and excluded from reports
  assert.equal(vcbTx.category, 'Chuyển nội bộ');
  assert.equal(vcbTx.excludedFromReport, true);
  assert.equal(bidvTx.category, 'Chuyển nội bộ');
  assert.equal(bidvTx.excludedFromReport, true);

  const query = parseFinanceQuery({ from: '2026-10-01', to: '2026-10-31' }, true);
  const dashboard = buildDashboard(rows, query);
  assert.equal(dashboard.income, 0);
  assert.equal(dashboard.expense, 0);
  assert.equal(dashboard.net, 0);
});

test('T3: Chỉ có vế VCB (BIDV chưa tới) -> Vế VCB đã trung lập; không lỗi', async () => {
  const vcbRow = [
    '29/10/2026 10:00', 'VCB', 'CHI', 5_000_000, 10_000_000,
    'NGUYEN VAN A', 'MBVCB.TEST.CT...', 'Chuyển nội bộ',
    'a'.repeat(64), 'BANK_NOTIFICATION', 'VCB ••8999',
  ];
  const identityRow = ['a'.repeat(64), `ACCOUNT:${bidvDigest}`, 'NGUYEN VAN A ••0999', '2026-10-29T10:00:00.000Z'];

  const rows = fromFinanceSnapshot({
    bank: [TRANSACTION_HEADERS_V2, vcbRow],
    history: [HISTORY_HEADERS],
    identities: [IDENTITY_HEADERS, identityRow],
  }, OWN_CONFIG);

  assert.equal(rows.length, 1);
  assert.equal(rows[0].category, 'Chuyển nội bộ');
  assert.equal(rows[0].excludedFromReport, true);
  assert.equal(rows[0].internalTransfer, null);

  const query = parseFinanceQuery({ from: '2026-10-01', to: '2026-10-31' }, true);
  const dashboard = buildDashboard(rows, query);
  assert.equal(dashboard.expense, 0);
  assert.equal(dashboard.income, 0);
  assert.equal(dashboard.net, 0);
});

test('T4: BIDV THU cùng số tiền nhưng không có vế nhận diện được -> Không ghép, tính Thu bình thường', async () => {
  const bidvRow = [
    '29/10/2026 10:05', 'BIDV', 'THU', 5_000_000, 15_000_000,
    '', 'NGUYEN VAN B chuyen tien', 'Chưa phân loại',
    'b'.repeat(64), 'BANK_NOTIFICATION', 'BIDV ••0999',
  ];

  const rows = fromFinanceSnapshot({
    bank: [TRANSACTION_HEADERS_V2, bidvRow],
    history: [HISTORY_HEADERS],
  }, OWN_CONFIG);

  assert.equal(rows.length, 1);
  assert.equal(rows[0].internalTransfer, null);
  assert.equal(rows[0].excludedFromReport, false);

  const query = parseFinanceQuery({ from: '2026-10-01', to: '2026-10-31' }, true);
  const dashboard = buildDashboard(rows, query);
  assert.equal(dashboard.income, 5_000_000);
  assert.equal(dashboard.expense, 0);
  assert.equal(dashboard.net, 5_000_000);
});

test('T5: Hai BIDV THU cùng số tiền trong cửa sổ -> Không ghép (mơ hồ)', async () => {
  const vcbRow = [
    '29/10/2026 10:00', 'VCB', 'CHI', 5_000_000, 10_000_000,
    'NGUYEN VAN A', 'MBVCB.TEST.CT...', 'Chuyển nội bộ',
    'a'.repeat(64), 'BANK_NOTIFICATION', 'VCB ••8999',
  ];
  const identityRow = ['a'.repeat(64), `ACCOUNT:${bidvDigest}`, 'NGUYEN VAN A ••0999', '2026-10-29T10:00:00.000Z'];

  const bidvRow1 = [
    '29/10/2026 10:05', 'BIDV', 'THU', 5_000_000, 15_000_000,
    '', 'Tien vao 1', 'Chưa phân loại',
    '1'.repeat(64), 'BANK_NOTIFICATION', 'BIDV ••0999',
  ];
  const bidvRow2 = [
    '29/10/2026 10:10', 'BIDV', 'THU', 5_000_000, 20_000_000,
    '', 'Tien vao 2', 'Chưa phân loại',
    '2'.repeat(64), 'BANK_NOTIFICATION', 'BIDV ••0999',
  ];

  const rows = fromFinanceSnapshot({
    bank: [TRANSACTION_HEADERS_V2, vcbRow, bidvRow1, bidvRow2],
    history: [HISTORY_HEADERS],
    identities: [IDENTITY_HEADERS, identityRow],
  }, OWN_CONFIG);

  // Ambiguity: neither BIDV should be paired
  const b1 = rows.find((r) => r.id === `bank:${'1'.repeat(64)}`)!;
  const b2 = rows.find((r) => r.id === `bank:${'2'.repeat(64)}`)!;
  const vcb = rows.find((r) => r.id === `bank:${'a'.repeat(64)}`)!;

  assert.equal(b1.internalTransfer, null);
  assert.equal(b2.internalTransfer, null);
  assert.equal(vcb.internalTransfer, null);
  assert.equal(b1.excludedFromReport, false);
  assert.equal(b2.excludedFromReport, false);

  const query = parseFinanceQuery({ from: '2026-10-01', to: '2026-10-31' }, true);
  const dashboard = buildDashboard(rows, query);
  assert.equal(dashboard.income, 10_000_000);
});

test('T6: VCB CHI 5tr cho đối tác ngoài + BIDV THU 5tr từ ngoài -> Không ghép', async () => {
  const vcbRow = [
    '29/10/2026 10:00', 'VCB', 'CHI', 5_000_000, 10_000_000,
    'CONG TY TNHH ABC', 'MBVCB.TEST.CT...', 'Mua sắm',
    'a'.repeat(64), 'BANK_NOTIFICATION', 'VCB ••8999',
  ];
  const identityRow = ['a'.repeat(64), `ACCOUNT:${otherDigest}`, 'CONG TY ABC ••0999', '2026-10-29T10:00:00.000Z'];

  const bidvRow = [
    '29/10/2026 10:05', 'BIDV', 'THU', 5_000_000, 15_000_000,
    '', 'KHACH HANG CHUYEN TIEN', 'Chưa phân loại',
    'b'.repeat(64), 'BANK_NOTIFICATION', 'BIDV ••0999',
  ];

  const rows = fromFinanceSnapshot({
    bank: [TRANSACTION_HEADERS_V2, vcbRow, bidvRow],
    history: [HISTORY_HEADERS],
    identities: [IDENTITY_HEADERS, identityRow],
  }, OWN_CONFIG);

  assert.equal(rows[0].internalTransfer, null);
  assert.equal(rows[1].internalTransfer, null);

  const query = parseFinanceQuery({ from: '2026-10-01', to: '2026-10-31' }, true);
  const dashboard = buildDashboard(rows, query);
  assert.equal(dashboard.income, 5_000_000);
  assert.equal(dashboard.expense, 5_000_000);
});

test('T7: Người dùng sửa vế được ghép sang danh mục khác (K7 & R2) -> Sửa tay thắng, ứng viên không mang internalTransfer', async () => {
  const vcbRow = [
    '29/10/2026 10:00', 'VCB', 'CHI', 5_000_000, 10_000_000,
    'NGUYEN VAN A', 'MBVCB.TEST.CT...', 'Chuyển nội bộ',
    'a'.repeat(64), 'BANK_NOTIFICATION', 'VCB ••8999',
  ];
  const identityRow = ['a'.repeat(64), `ACCOUNT:${bidvDigest}`, 'NGUYEN VAN A ••0999', '2026-10-29T10:00:00.000Z'];

  const bidvRow = [
    '29/10/2026 10:05', 'BIDV', 'THU', 5_000_000, 15_000_000,
    '', 'Nội dung', 'Chưa phân loại',
    'b'.repeat(64), 'BANK_NOTIFICATION', 'BIDV ••0999',
  ];

  // User edited BIDV row to "Thu nhập khác" (valid category in CATEGORY_REGISTRY per K7)
  const editRow = [
    '00000000-0000-4000-8000-000000000001', `bank:${'b'.repeat(64)}`,
    'Thu nhập khác', 'Sửa thành thu nhập khác', false, 'THU', '0', '2026-10-29T10:15:00.000Z',
  ];

  const rows = fromFinanceSnapshot({
    bank: [TRANSACTION_HEADERS_V2, vcbRow, bidvRow],
    history: [HISTORY_HEADERS],
    identities: [IDENTITY_HEADERS, identityRow],
    edits: [EDIT_HEADERS, editRow],
  }, OWN_CONFIG);

  const bidvTx = rows.find((r) => r.id === `bank:${'b'.repeat(64)}`)!;
  const vcbTx = rows.find((r) => r.id === `bank:${'a'.repeat(64)}`)!;

  // R2 check: candidate edited by user must NOT have internalTransfer
  assert.equal(bidvTx.category, 'Thu nhập khác');
  assert.equal(bidvTx.excludedFromReport, false);
  assert.equal(bidvTx.internalTransfer, null);

  // Anchor has no valid candidate, so it also has no internalTransfer
  assert.equal(vcbTx.internalTransfer, null);

  const query = parseFinanceQuery({ from: '2026-10-01', to: '2026-10-31' }, true);
  const dashboard = buildDashboard(rows, query);
  assert.equal(dashboard.income, 5_000_000);
  assert.equal(dashboard.expense, 0);
  assert.equal(dashboard.net, 5_000_000);
});

test('R1 regression: VCB CHI 5tr (anchor) bị người dùng sửa sang Gia đình (CHI) -> Không kéo BIDV thành Chuyển nội bộ, không lệch 5tr', async () => {
  const vcbRow = [
    '29/10/2026 10:00', 'VCB', 'CHI', 5_000_000, 10_000_000,
    'NGUYEN VAN A', 'MBVCB.TEST.CT...', 'Chuyển nội bộ',
    'a'.repeat(64), 'BANK_NOTIFICATION', 'VCB ••8999',
  ];
  const identityRow = ['a'.repeat(64), `ACCOUNT:${bidvDigest}`, 'NGUYEN VAN A ••0999', '2026-10-29T10:00:00.000Z'];

  const bidvRow = [
    '29/10/2026 10:05', 'BIDV', 'THU', 5_000_000, 15_000_000,
    '', 'Nội dung', 'Chưa phân loại',
    'b'.repeat(64), 'BANK_NOTIFICATION', 'BIDV ••0999',
  ];

  // User edited VCB anchor to "Gia đình" (CHI)
  const editRow = [
    '00000000-0000-4000-8000-000000000002', `bank:${'a'.repeat(64)}`,
    'Gia đình', 'Chi tiêu gia đình', false, 'CHI', '0', '2026-10-29T10:15:00.000Z',
  ];

  const rows = fromFinanceSnapshot({
    bank: [TRANSACTION_HEADERS_V2, vcbRow, bidvRow],
    history: [HISTORY_HEADERS],
    identities: [IDENTITY_HEADERS, identityRow],
    edits: [EDIT_HEADERS, editRow],
  }, OWN_CONFIG);

  const vcbTx = rows.find((r) => r.id === `bank:${'a'.repeat(64)}`)!;
  const bidvTx = rows.find((r) => r.id === `bank:${'b'.repeat(64)}`)!;

  // VCB CHI is now an expense
  assert.equal(vcbTx.category, 'Gia đình');
  assert.equal(vcbTx.excludedFromReport, false);
  assert.equal(vcbTx.internalTransfer, null);

  // BIDV THU is NOT dragged into Chuyển nội bộ
  assert.equal(bidvTx.category, 'Chưa phân loại');
  assert.equal(bidvTx.excludedFromReport, false);
  assert.equal(bidvTx.internalTransfer, null);

  // Totals: Chi 5M, Thu 5M, Net 0 (no 5M discrepancy!)
  const query = parseFinanceQuery({ from: '2026-10-01', to: '2026-10-31' }, true);
  const dashboard = buildDashboard(rows, query);
  assert.equal(dashboard.expense, 5_000_000);
  assert.equal(dashboard.income, 5_000_000);
  assert.equal(dashboard.net, 0);
});

test('R3: Dòng cũ (cột K trống) và dòng mới (cột K có label) được gom chung 1 nhóm trong accountSummary', () => {
  const oldRow = [
    '29/10/2026 09:00', 'VCB', 'CHI', 100_000, 9_900_000,
    'Cửa hàng', 'Mua cafe', 'Ăn uống',
    '1'.repeat(64), 'BANK_NOTIFICATION', '', // Empty Column K
  ];
  const newRow = [
    '29/10/2026 10:00', 'VCB', 'CHI', 200_000, 9_700_000,
    'Cửa hàng', 'Mua cơm', 'Ăn uống',
    '2'.repeat(64), 'BANK_NOTIFICATION', 'VCB ••8999', // Populated Column K
  ];

  const rows = fromFinanceSnapshot({
    bank: [TRANSACTION_HEADERS_V2, oldRow, newRow],
    history: [HISTORY_HEADERS],
  }, OWN_CONFIG);

  assert.equal(rows.length, 2);
  assert.equal(rows[0].accountOrWallet, 'VCB ••8999');
  assert.equal(rows[1].accountOrWallet, 'VCB ••8999');

  const query = parseFinanceQuery({ from: '2026-10-01', to: '2026-10-31' }, true);
  const dashboard = buildDashboard(rows, query);
  const accountGroups = dashboard.accountSummary;
  assert.equal(accountGroups.length, 1);
  assert.equal(accountGroups[0].name, 'VCB ••8999');
  assert.equal(accountGroups[0].transactionCount, 2);
  assert.equal(accountGroups[0].expense, 300_000);
});

test('K2: Ghép cặp qua ranh giới tháng (23:55 ngày cuối tháng ↔ 00:10 ngày đầu tháng sau) và giữ internalTransfer khi phân trang', () => {
  const vcbRow = [
    '31/10/2026 23:55', 'VCB', 'CHI', 5_000_000, 10_000_000,
    'NGUYEN VAN A', 'MBVCB.TEST.CT...', 'Chuyển nội bộ',
    'e'.repeat(64), 'BANK_NOTIFICATION', 'VCB ••8999',
  ];
  const identityRow = ['e'.repeat(64), `ACCOUNT:${bidvDigest}`, 'NGUYEN VAN A ••0999', '2026-10-31T23:55:00.000Z'];

  const bidvRow = [
    '01/11/2026 00:10', 'BIDV', 'THU', 5_000_000, 15_000_000,
    '', 'Nội dung nhận tiền', 'Chưa phân loại',
    'f'.repeat(64), 'BANK_NOTIFICATION', 'BIDV ••0999',
  ];

  const rows = fromFinanceSnapshot({
    bank: [TRANSACTION_HEADERS_V2, vcbRow, bidvRow],
    history: [HISTORY_HEADERS],
    identities: [IDENTITY_HEADERS, identityRow],
  }, OWN_CONFIG);

  assert.equal(rows.length, 2);
  const vcbTx = rows.find((r) => r.id === `bank:${'e'.repeat(64)}`)!;
  const bidvTx = rows.find((r) => r.id === `bank:${'f'.repeat(64)}`)!;

  assert.ok(vcbTx.internalTransfer);
  assert.ok(bidvTx.internalTransfer);
  assert.equal(vcbTx.internalTransfer.pairId, bidvTx.internalTransfer.pairId);
  assert.equal(vcbTx.internalTransfer.role, 'OUT');
  assert.equal(bidvTx.internalTransfer.role, 'IN');

  // Query October:
  const queryOct = parseFinanceQuery({ from: '2026-10-01', to: '2026-10-31' }, true);
  const dashOct = buildDashboard(rows.filter((r) => r.date >= '2026-10-01' && r.date <= '2026-10-31'), queryOct);
  assert.equal(dashOct.expense, 0); // VCB row is excluded from expense

  // Query November:
  const queryNov = parseFinanceQuery({ from: '2026-11-01', to: '2026-11-30' }, true);
  const dashNov = buildDashboard(rows.filter((r) => r.date >= '2026-11-01' && r.date <= '2026-11-30'), queryNov);
  assert.equal(dashNov.income, 0); // BIDV row is excluded from income

  // Pagination test: simulate /api/transactions page 2 (slice)
  // Even if a page contains only 1 of the 2 transactions, its internalTransfer metadata is fully preserved
  const page1 = rows.slice(0, 1);
  const page2 = rows.slice(1, 2);
  assert.ok(page1[0].internalTransfer);
  assert.ok(page2[0].internalTransfer);
  assert.equal(page1[0].internalTransfer.pairId, page2[0].internalTransfer.pairId);
});

test('K5: Tác động phụ của fromFinanceSnapshot: Sửa vế đã ghép không lỗi revision, memory không nhận observation suy diễn, Money Lover overlap bỏ qua vế đã ghép', async () => {
  const vcbRow = [
    '29/10/2026 10:00', 'VCB', 'CHI', 5_000_000, 10_000_000,
    'NGUYEN VAN A', 'MBVCB.TEST.CT...', 'Chuyển nội bộ',
    'a'.repeat(64), 'BANK_NOTIFICATION', 'VCB ••8999',
  ];
  const identityRow = ['a'.repeat(64), `ACCOUNT:${bidvDigest}`, 'NGUYEN VAN A ••0999', '2026-10-29T10:00:00.000Z'];

  const bidvRow = [
    '29/10/2026 10:05', 'BIDV', 'THU', 5_000_000, 15_000_000,
    '', 'Nội dung', 'Chưa phân loại',
    'b'.repeat(64), 'BANK_NOTIFICATION', 'BIDV ••0999',
  ];

  const snapshot: FinanceSnapshot = {
    bank: [TRANSACTION_HEADERS_V2, vcbRow, bidvRow],
    history: [HISTORY_HEADERS],
    identities: [IDENTITY_HEADERS, identityRow],
    edits: [EDIT_HEADERS],
  };

  // 1. Revision conflict check: edit paired transaction with baseRevision '0' succeeds
  const editState = { snapshot: { ...snapshot, edits: [...(snapshot.edits ?? [])] }, appended: [] as unknown[][] };
  const editPortFactory = async () => ({
    read: async () => editState.snapshot,
    append: async (row: unknown[]) => {
      editState.appended.push(row);
      editState.snapshot.edits.push(row);
    },
  });

  const editRes = await writeTransactionEdit(
    {
      method: 'POST',
      headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' },
      body: {
        requestId: '11111111-1111-4111-8111-111111111111',
        transactionId: `bank:${'b'.repeat(64)}`,
        category: 'Thu nhập khác',
        note: 'Sửa tay',
        excluded: false,
        type: 'THU',
        baseRevision: '0',
      },
    },
    envWithOwnAccounts({ TRANSACTION_EDITS_ENABLED: '1' }),
    editPortFactory
  );
  assert.equal(editRes.httpStatus, 200);
  assert.equal(editRes.body.status, 'SAVED_TRANSACTION_EDIT');

  // 2. Memory observation check: unedited bank rows do not create observations
  const obs = memoryObservations(snapshot);
  assert.equal(obs.length, 0);

  // Even if an observation had the owner's digest, buildMemoryIndex filters it out
  const ownObs: Observation = {
    transactionId: `bank:${'a'.repeat(64)}`,
    identity: { type: 'ACCOUNT', key: `ACCOUNT:${bidvDigest}`, label: 'NGUYEN VAN A ••0999' },
    type: 'CHI',
    category: 'Chuyển nội bộ',
    at: '2026-10-29T10:00:00.000Z',
  };
  const rules = buildMemoryIndex([ownObs], [], new Set([vcbDigest, bidvDigest]));
  assert.equal(rules.length, 0);

  // 3. Money Lover overlap check: paired bank rows (excludedFromReport) are ignored by overlap
  const parsedML = parseMoneyLover(sourceTable(
    sourceRow({ 1: '2026-10-29', 2: 'Ăn uống', 3: -5000000, 4: 'VND' })
  ));
  const candidates = overlapCandidates(parsedML, [TRANSACTION_HEADERS_V2, vcbRow, bidvRow]);
  assert.equal(candidates.length, 0);

  // In fromFinanceSnapshot, possibleDuplicate is false for paired transactions
  const rowsWithML = fromFinanceSnapshot({
    bank: [TRANSACTION_HEADERS_V2, vcbRow, bidvRow],
    history: [HISTORY_HEADERS, historyRow({
      legacyId: 'ml1_' + '9'.repeat(64),
      date: '2026-10-29',
      type: 'CHI',
      amount: 5000000,
      currency: 'VND',
      amountVnd: 5000000,
      category: 'Ăn uống',
      wallet: 'Wallet A',
      note: '',
      counterparty: '',
      event: '',
      excludedFromReport: false,
      source: 'MONEYLOVER',
      originalNo: '1',
      member: '',
    })],
    identities: [IDENTITY_HEADERS, identityRow],
  }, OWN_CONFIG);

  const pairedVcb = rowsWithML.find((r) => r.id === `bank:${'a'.repeat(64)}`)!;
  assert.equal(pairedVcb.possibleDuplicate, false);
});

test('T13 (K6): Chiều ngược: BIDV CHI -> VCB THU (vế VCB THU là mỏ neo nhận diện được)', async () => {
  const bidvPostedAt = 1793268000000;
  const bidvText = `BIDV xin thông báo tới Quý khách Thời gian giao dịch: 10:00 29/10/2026 Tài khoản thanh toán: ${BIDV_ACCT} Số tiền GD: -5,000,000 VND Số dư cuối: 10,000,000 VND Nội dung giao dịch: NGUYEN VAN A chuyen tien Mã giao dịch: BIDV12345`;
  const bidvEventId = createEventId('BIDV', bidvPostedAt, bidvText);

  const vcbPostedAt = 1793268300000;
  const vcbText = `Số dư TK VCB ${VCB_ACCT} +5,000,000 VND lúc 29-10-2026 10:05:00. Số dư 15,000,000 VND. Ref MBVCB.TEST.CT tu ${BIDV_ACCT} NGUYEN VAN A toi ${VCB_ACCT} NGUYEN VAN A tai VCB`;
  const vcbEventId = createEventId('VCB', vcbPostedAt, vcbText);

  const appState = { transactions: [] as unknown[][], raw: [] as unknown[][], identities: [] as unknown[][] };
  const port = mockSheetsPort(appState);
  const env = envWithOwnAccounts();

  await validateAndProcessTransaction(
    { method: 'POST', headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' },
      body: { eventId: bidvEventId, text: bidvText, postedAt: bidvPostedAt, bank: 'BIDV', packageName: 'com.vnpay.bidv', title: 'BIDV' } },
    env, port
  );
  await validateAndProcessTransaction(
    { method: 'POST', headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' },
      body: { eventId: vcbEventId, text: vcbText, postedAt: vcbPostedAt, bank: 'VCB', packageName: 'com.VCB', title: 'VCB' } },
    env, port
  );

  const snapshot = {
    bank: [TRANSACTION_HEADERS_V2, ...appState.transactions],
    history: [HISTORY_HEADERS],
    identities: [IDENTITY_HEADERS, ...appState.identities],
  };

  const rows = fromFinanceSnapshot(snapshot, OWN_CONFIG);
  assert.equal(rows.length, 2);

  const bidvTx = rows.find((r) => r.bank === 'BIDV')!;
  const vcbTx = rows.find((r) => r.bank === 'VCB')!;

  assert.ok(bidvTx.internalTransfer);
  assert.ok(vcbTx.internalTransfer);
  assert.equal(bidvTx.internalTransfer.role, 'OUT');
  assert.equal(vcbTx.internalTransfer.role, 'IN');
  assert.equal(bidvTx.internalTransfer.pairId, vcbTx.internalTransfer.pairId);

  assert.equal(bidvTx.category, 'Chuyển nội bộ');
  assert.equal(bidvTx.excludedFromReport, true);
  assert.equal(vcbTx.category, 'Chuyển nội bộ');
  assert.equal(vcbTx.excludedFromReport, true);

  const query = parseFinanceQuery({ from: '2026-10-01', to: '2026-10-31' }, true);
  const dashboard = buildDashboard(rows, query);
  assert.equal(dashboard.income, 0);
  assert.equal(dashboard.expense, 0);
  assert.equal(dashboard.net, 0);
});

test('T14 (K6): Cả hai vế không có mẫu CT tu ... toi ... (memo tự do) -> Không ghép tự động', () => {
  const vcbRow = [
    '29/10/2026 10:00', 'VCB', 'CHI', 5_000_000, 10_000_000,
    '', 'Nop tien tai quay', 'Chưa phân loại',
    'c'.repeat(64), 'BANK_NOTIFICATION', 'VCB ••8999',
  ];
  const bidvRow = [
    '29/10/2026 10:05', 'BIDV', 'THU', 5_000_000, 15_000_000,
    '', 'Nhan tien tu dong', 'Chưa phân loại',
    'd'.repeat(64), 'BANK_NOTIFICATION', 'BIDV ••0999',
  ];

  const rows = fromFinanceSnapshot({
    bank: [TRANSACTION_HEADERS_V2, vcbRow, bidvRow],
    history: [HISTORY_HEADERS],
  }, OWN_CONFIG);

  assert.equal(rows.length, 2);
  const vcbTx = rows.find((r) => r.id === `bank:${'c'.repeat(64)}`)!;
  const bidvTx = rows.find((r) => r.id === `bank:${'d'.repeat(64)}`)!;

  assert.equal(vcbTx.internalTransfer, null);
  assert.equal(bidvTx.internalTransfer, null);
  assert.equal(vcbTx.category, 'Chưa phân loại');
  assert.equal(bidvTx.category, 'Chưa phân loại');

  const query = parseFinanceQuery({ from: '2026-10-01', to: '2026-10-31' }, true);
  const dashboard = buildDashboard(rows, query);
  assert.equal(dashboard.expense, 5_000_000);
  assert.equal(dashboard.income, 5_000_000);
  assert.equal(dashboard.net, 0);
});

test('T8: Thiếu/sai OWN_ACCOUNT_DIGESTS -> Hành vi giống hiện tại, không 5xx', async () => {
  assert.deepEqual(readOwnAccounts({}), {});
  assert.deepEqual(readOwnAccounts({ OWN_ACCOUNT_DIGESTS: '' }), {});
  assert.deepEqual(readOwnAccounts({ OWN_ACCOUNT_DIGESTS: '{bad json' }), {});
  assert.deepEqual(readOwnAccounts({ OWN_ACCOUNT_DIGESTS: '[]' }), {});
  assert.deepEqual(readOwnAccounts({ OWN_ACCOUNT_DIGESTS: JSON.stringify({ VCB: { digest: 'short', label: '' } }) }), {});

  // Ingest with missing OWN_ACCOUNT_DIGESTS works normally
  const postedAt = 1793268000000;
  const vcbText = `Số dư TK VCB ${VCB_ACCT} -50,000 VND lúc 29-10-2026 10:00:00. Số dư 10,000,000 VND. Ref MBVCB.TEST.NORMAL`;
  const eventId = createEventId('VCB', postedAt, vcbText);
  const appState = { transactions: [] as unknown[][], raw: [] as unknown[][] };
  const port = mockSheetsPort(appState);

  const res = await validateAndProcessTransaction(
    { method: 'POST', headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' },
      body: { eventId, text: vcbText, postedAt, bank: 'VCB', packageName: 'com.VCB', title: 'VCB' } },
    testEnvironment(), // No OWN_ACCOUNT_DIGESTS
    port
  );
  assert.equal(res.httpStatus, 200);
  assert.equal(res.body.status, 'SAVED_TRANSACTION');
});

test('T9: TK chủ có trong observation -> Không xuất hiện trong memory index', () => {
  const ownObs: Observation = {
    transactionId: 'bank:1',
    identity: { type: 'ACCOUNT', key: `ACCOUNT:${bidvDigest}`, label: 'BIDV ••0999' },
    type: 'CHI',
    category: 'Ăn uống',
    at: '2026-10-29T10:00:00.000Z',
  };

  const merchantObs: Observation = {
    transactionId: 'bank:2',
    identity: { type: 'ACCOUNT', key: `ACCOUNT:${otherDigest}`, label: 'Cửa hàng tiện lợi' },
    type: 'CHI',
    category: 'Mua sắm',
    at: '2026-10-29T10:00:00.000Z',
  };

  const ownDigests = new Set([vcbDigest, bidvDigest]);
  const rules = buildMemoryIndex([ownObs, merchantObs], [], ownDigests);

  assert.equal(rules.some((r) => r.key === `ACCOUNT:${bidvDigest}`), false);
  assert.equal(rules.some((r) => r.key === `ACCOUNT:${otherDigest}`), true);
});

test('T10: Merchant ngoài vẫn học bình thường cả VCB lẫn BIDV -> Không hồi quy Category Memory', () => {
  const ownDigests = new Set([vcbDigest, bidvDigest]);
  const obsVCB: Observation = {
    transactionId: 'bank:vcb1',
    identity: { type: 'ACCOUNT', key: `ACCOUNT:${otherDigest}`, label: 'Phở Thìn' },
    type: 'CHI',
    category: 'Ăn uống',
    at: '2026-10-28T10:00:00.000Z',
  };
  const obsBIDV: Observation = {
    transactionId: 'bank:bidv1',
    identity: { type: 'ACCOUNT', key: `ACCOUNT:${otherDigest}`, label: 'Phở Thìn' },
    type: 'CHI',
    category: 'Ăn uống',
    at: '2026-10-29T10:00:00.000Z',
  };

  const rules = buildMemoryIndex([obsVCB, obsBIDV], [], ownDigests);
  const rule = rules.find((r) => r.key === `ACCOUNT:${otherDigest}`);
  assert.ok(rule);
  assert.equal(rule.category, 'Ăn uống');
  assert.equal(rule.count, 2);
});

test('T11: Log/Sheet/Raw_Capture/response không có số TK thô; script digest chỉ in digest', async () => {
  // 1. Script execution test: verifies normalization and no raw account echoing
  const scriptPath = resolve(import.meta.dirname, '../scripts/own-account-digest.ts');
  const res = spawnSync(process.execPath, ['--import', 'tsx', scriptPath], {
    cwd: resolve(import.meta.dirname, '..'),
    input: `  ${BIDV_ACCT}  \n`,
    encoding: 'utf8',
    env: { ...process.env, CATEGORY_MEMORY_HMAC_KEY: HMAC_SECRET },
  });

  assert.equal(res.status, 0);
  const output = res.stdout.trim();
  assert.equal(output, bidvDigest);
  assert.equal(output.includes(BIDV_ACCT), false);
  assert.equal(res.stderr, '');

  // 2. Ingest transaction and verify Sheet row, Raw_Capture, and response do NOT contain raw accounts
  const postedAt = 1793268000000;
  const vcbText = `Số dư TK VCB ${VCB_ACCT} -5,000,000 VND lúc 29-10-2026 10:00:00. Số dư 10,000,000 VND. Ref MBVCB.TEST.CT tu ${VCB_ACCT} NGUYEN VAN A toi ${BIDV_ACCT} NGUYEN VAN A tai BIDV`;
  const eventId = createEventId('VCB', postedAt, vcbText);
  const appState = { transactions: [] as unknown[][], raw: [] as unknown[][], identities: [] as unknown[][] };
  const port = mockSheetsPort(appState);

  const apiRes = await validateAndProcessTransaction(
    {
      method: 'POST',
      headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' },
      body: { eventId, text: vcbText, postedAt, bank: 'VCB', packageName: 'com.VCB', title: 'VCB' },
    },
    envWithOwnAccounts(),
    port
  );

  // Response contains no raw account
  assert.equal(JSON.stringify(apiRes).includes(VCB_ACCT), false);
  assert.equal(JSON.stringify(apiRes).includes(BIDV_ACCT), false);

  // Sheet row contains no raw account
  for (const row of appState.transactions) {
    for (const cell of row) {
      if (typeof cell === 'string') {
        assert.equal(cell.includes(VCB_ACCT), false, `Transactions row contains raw account: ${cell}`);
        assert.equal(cell.includes(BIDV_ACCT), false, `Transactions row contains raw account: ${cell}`);
      }
    }
  }

  // Identity row contains no raw account
  for (const row of appState.identities) {
    for (const cell of row) {
      if (typeof cell === 'string') {
        assert.equal(cell.includes(VCB_ACCT), false, `Identity row contains raw account: ${cell}`);
        assert.equal(cell.includes(BIDV_ACCT), false, `Identity row contains raw account: ${cell}`);
      }
    }
  }

  // Also test raw capture for unparseable bank notification
  const unparsedText = `Thong bao la tu VCB ${VCB_ACCT} chuyen toi ${BIDV_ACCT}`;
  const unparsedEventId = createEventId('VCB', postedAt + 1000, unparsedText);
  await validateAndProcessTransaction(
    {
      method: 'POST',
      headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' },
      body: { eventId: unparsedEventId, text: unparsedText, postedAt: postedAt + 1000, bank: 'VCB', packageName: 'com.VCB', title: 'VCB' },
    },
    envWithOwnAccounts(),
    port
  );

  // Raw_Capture row contains masked text, no raw account
  for (const row of appState.raw) {
    for (const cell of row) {
      if (typeof cell === 'string') {
        assert.equal(cell.includes(VCB_ACCT), false, `Raw_Capture row contains raw account: ${cell}`);
        assert.equal(cell.includes(BIDV_ACCT), false, `Raw_Capture row contains raw account: ${cell}`);
      }
    }
  }
});

test('T12: Money Lover import dry-run với synthetic fixture -> Đúng luồng, không đổi dữ liệu, không đọc file thật', async () => {
  const parsed = parseMoneyLover(sourceTable(
    sourceRow({ 0: 1, 2: 'Cafe', 3: -45000, 4: 'VND' }),
    sourceRow({ 0: 2, 2: 'Lương', 3: 15000000, 4: 'VND' }),
    sourceRow({ 0: 3, 2: 'Mua sắm', 3: 50000, 4: 'JPY' }), // Non-VND, excluded
    sourceRow({ 0: 4, 2: 'Cho vay', 3: -2000000, 4: 'VND' }) // Non-consumption, excluded
  ));

  assert.equal(parsed.transactions.length, 2);
  assert.equal(parsed.excluded.length, 2);

  const initialHistoryRows: unknown[][] = [];
  const port = {
    readHistory: async () => ({ exists: true, headers: [...HISTORY_HEADERS], rows: initialHistoryRows }),
    readTransactions: async () => [[...TRANSACTION_HEADERS_V2]],
    appendHistory: async (_rows: unknown[][]) => {
      throw new Error('appendHistory should not be called in dry run');
    },
  };

  const res = await importMoneyLover(parsed, port, false);
  assert.equal(res.status, 'DRY_RUN');
  assert.equal(res.inserted, 0);
  assert.equal(res.pending, 2);
  assert.equal(res.rejected, 0);
  assert.equal(res.skipped, 0);
  assert.equal(res.overlap.status, 'OVERLAP_REVIEWED');
  assert.equal(res.overlap.candidateHistoryCount, 0);
  // Port state was not modified
  assert.equal(initialHistoryRows.length, 0);
});
