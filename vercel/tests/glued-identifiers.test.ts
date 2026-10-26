import assert from 'node:assert/strict';
import test from 'node:test';
import { maskSensitiveIdentifiers } from '../sensitive-identifiers.js';
import { parseVcb } from '../parsers/vcb.js';
import { parseBidv } from '../parsers/bidv.js';
import { parseVietinbank } from '../parsers/vietinbank.js';
import { createEventId } from '../event-identity.js';
import { validateAndProcessTransaction } from '../lib/transaction-service.js';
import { manualRow } from '../lib/manual-transaction.js';
import { RAW_CAPTURE_HEADERS, TRANSACTION_HEADERS } from '../lib/sheet-schema.js';
import { SYNTHETIC_SECRET, testEnvironment } from './support.js';

// Digit runs of 8+ glued to letters (virtual accounts, bank refs) used to reach the Sheet unmasked.
// Every name, account, virtual account and reference below is synthetic.
const LONG = /\d{8,}/;
const VIRTUAL = 'XY00Z00000000000001';

test('glued 8+ digit runs are masked while short codes, amounts, dates and times stay', () => {
  assert.equal(maskSensitiveIdentifiers(`toi ${VIRTUAL} TRAN THI B`), 'toi XY00Z*** TRAN THI B');
  assert.equal(maskSensitiveIdentifiers('Ref a999999999b'), 'Ref a***b');
  const ref = maskSensitiveIdentifiers('LE THI D Chuyen tien#SP#000000000000000000012026abc1111111.1111.1111.111111');
  assert.equal(LONG.test(ref), false); assert.ok(ref.startsWith('LE THI D Chuyen tien#SP#***abc'));
  const kept = 'FT26059ABC123 A1234567B 6299BFTVGL8J2B7C 01-03-2026 18:50:44 -25,000 VND SD 999999999 VND +100000000';
  assert.equal(maskSensitiveIdentifiers(kept), kept);
});

test('VCB: owner-shaped outgoing (virtual account) and incoming (#SP# ref) notifications store no long digit run', () => {
  const out = parseVcb(`Số dư TK VCB 9999999999 -25,000 VND lúc 01-03-2026 18:50:44. Số dư 147,322 VND. Ref MBVCB.11111111111.1111AAAAAA1A1A1A.NGUYEN VAN CHU chuyen tien.CT tu 9999999999 NGUYEN VAN CHU toi ${VIRTUAL} TRAN THI B tai TECHCOMBANK`)!;
  assert.deepEqual([out.type, out.amount, out.balance, out.time], ['CHI', 25000, 147322, '01/03/2026 18:50']);
  // The Ref split that derives counterparty is unchanged; only the glued account digits disappear.
  assert.equal(out.counterparty, '1111AAAAAA1A1A1A.NGUYEN VAN CHU chuyen tien.CT tu *** NGUYEN VAN CHU toi XY00Z*** TRAN THI B tai TECHCOMBANK');
  const incoming = parseVcb('Số dư TK VCB 9999999999 +3,000,000 VND lúc 01-03-2026 09:56:37. Số dư 3,172,322 VND. Ref LE THI D Chuyen tien#SP#000000000000000000012026abc1111111.1111.1111.111111')!;
  assert.deepEqual([incoming.type, incoming.amount, incoming.balance], ['THU', 3000000, 3172322]);
  for (const parsed of [out, incoming]) assert.equal(LONG.test(`${parsed.counterparty} ${parsed.description}`), false);
});

test('BIDV and VietinBank contents with glued identifiers are masked', () => {
  const bidv = parseBidv(`BIDV xin thông báo tới Quý khách Thời gian giao dịch: 09:41 28/02/2026 Tài khoản thanh toán: 1234567890 Số tiền GD: -12,500 VND Số dư cuối: 87,500 VND Nội dung giao dịch: Thanh toan TK${VIRTUAL} hoa don HD20260228123456 Mã giao dịch: FT26059ABC123`)!;
  assert.equal(bidv.amount, 12500);
  assert.equal(LONG.test(bidv.description), false);
  assert.ok(bidv.description.endsWith('Mã giao dịch: FT26059ABC123'));
  const ctg = parseVietinbank(`Thời gian: 28/02/2026 09:42 Tài khoản: 123456789012 Giao dịch: -25,000 VND Số dư hiện tại: 125,000 VND Nội dung: CT tu 2345678901 NGUOI GUI toi ${VIRTUAL} NGUOI NHAN tai TECHCOMBANK REFAB12345678901`)!;
  assert.equal(ctg.amount, 25000);
  assert.equal(LONG.test(ctg.description), false);
  assert.ok(ctg.description.includes('toi XY00Z*** NGUOI NHAN'));
});

test('Raw_Capture and manual notes also mask glued runs; eventId still hashes the original text', async () => {
  const text = `GD TK 9999999999 -100,000 VND POS FORMAT UNKNOWN REF${VIRTUAL}`;
  const raws: unknown[][] = [];
  const port = async () => ({ readHeaders: async () => ({ transaction: TRANSACTION_HEADERS, raw: RAW_CAPTURE_HEADERS }),
    readEventIds: async () => ({ transactionIds: [], rawIds: raws.map((r) => r[3]) }),
    appendTransaction: async () => { throw new Error('unexpected transaction'); }, appendRaw: async (row: unknown[]) => { raws.push(row); } });
  const postedAt = Date.UTC(2026, 1, 28, 3);
  const eventId = createEventId('VCB', postedAt, text);
  const request = { method: 'POST', headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' },
    body: { eventId, bank: 'VCB', packageName: 'com.VCB', title: '', text, postedAt } };
  assert.equal((await validateAndProcessTransaction(request, testEnvironment(), port)).body.status, 'SAVED_RAW_CAPTURE');
  assert.equal(raws.length, 1); assert.equal(LONG.test(String(raws[0][2])), false); assert.equal(raws[0][3], eventId);
  const manual = manualRow({ requestId: '11111111-1111-4111-8111-111111111111', dateTime: '2026-02-28T09:00', type: 'CHI', amount: 1000,
    category: 'Ăn uống', account: 'CASH', note: `Chuyen TK${VIRTUAL}` });
  assert.equal(manual[6], 'Chuyen TKXY00Z***');
});
