import assert from 'node:assert/strict';
import test from 'node:test';
import { parseBidv } from '../parsers/bidv.js';
import { parseVietinbank } from '../parsers/vietinbank.js';
import { createEventId } from '../event-identity.js';
import { validateAndProcessTransaction } from '../lib/transaction-service.js';
import { RAW_CAPTURE_HEADERS, TRANSACTION_HEADERS } from '../lib/sheet-schema.js';
import { testEnvironment, SYNTHETIC_SECRET } from './support.js';

// Envelope structure supplied by the owner on 26/10/2026. Every identifier, name,
// reference, timestamp, amount and balance below is a synthetic replacement.
const BIDV = 'BIDV xin thông báo tới Quý khách Thời gian giao dịch: 09:41 28/02/2026 Tài khoản thanh toán: 1234567890 Số tiền GD: -12,500 VND Số dư cuối: 87,500 VND Nội dung giao dịch: Thu no thau chi TK 2345678901 Mã giao dịch: TEST-REF-A';
const CTG = 'Thời gian: 28/02/2026 09:42 Tài khoản: 123456789012 Giao dịch: +25,000 VND Số dư hiện tại: 125,000 VND Nội dung: CT DEN:TESTREF1 MBVCB.TESTREF2.NGUOI GUI THU NGHIEM chuyen tien.CT tu 2345678901 NGUOI GUI THU NGHIEM toi 0912345678 NGUOI NHAN THU NGHIEM_123456789012 tai VIETINBANK (0912345678)';

test('confirmed BIDV and VietinBank envelopes parse both directions and mask identifiers', () => {
  for (const [parse, sample, bank] of [[parseBidv, BIDV, 'BIDV'], [parseVietinbank, CTG, 'VIETINBANK']] as const) {
    for (const sign of ['+', '-']) {
      const parsed = parse(sample.replace(/([+-])([\d,]+) VND/, `${sign}15,000 VND`));
      assert.ok(parsed);
      assert.equal(parsed.type, sign === '+' ? 'THU' : 'CHI');
      assert.equal(parsed.amount, 15000);
      assert.equal(parsed.bank, bank);
      assert.equal(parsed.time.slice(0, 10), '28/02/2026');
      assert.equal(/1234567890|2345678901|0912345678/.test(parsed.description), false);
      assert.equal(parsed.counterparty, '');
    }
  }
});

test('known bank envelopes reject malformed money, invalid calendars, multiple notifications and unknown formats', () => {
  for (const [parse, sample] of [[parseBidv, BIDV], [parseVietinbank, CTG]] as const) {
    for (const corrupt of [sample.replace('28/02/2026', '30/02/2026'), sample.replace(/09:4[12]/, '24:00'),
      sample.replace(/[+-][\d,]+ VND/, '-1,20,000 VND'), sample.replace(/[+-][\d,]+ VND/, '+0 VND'),
      sample.replace(/[+-][\d,]+ VND/, '-9,007,199,254,741,000 VND'), `${sample} ${sample}`,
      sample.replace(' VND', ' USD'), sample.replace(/[+-][\d,]+ VND/, '15,000 VND')]) assert.equal(parse(corrupt), null);
    const normalized = sample.normalize('NFD').replace(/ /g, '\u00a0');
    assert.ok(parse(normalized));
    const zero = parse(sample.replace(/(Số dư (?:cuối|hiện tại):) [\d,]+/, '$1 0'));
    assert.equal(zero?.balance, 0);
  }
});

test('both bank payloads pass the full authenticated pipeline and sequential retry dedup', async () => {
  for (const [bank, packageName, text] of [['BIDV', 'com.vnpay.bidv', BIDV], ['VIETINBANK', 'com.vietinbank.ipay', CTG]] as const) {
    const rows: unknown[][] = [];
    const port = async () => ({ readHeaders: async () => ({ transaction: TRANSACTION_HEADERS, raw: RAW_CAPTURE_HEADERS }),
      readEventIds: async () => ({ transactionIds: rows.map((r) => r[8]), rawIds: [] }),
      appendTransaction: async (row: unknown[]) => { rows.push(row); }, appendRaw: async () => { throw new Error('unexpected raw'); } });
    const postedAt = Date.UTC(2026, 1, 28, 3);
    const body = { eventId: createEventId(bank, postedAt, text), bank, packageName, title: '', text, postedAt };
    const request = { method: 'POST', headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' }, body };
    assert.equal((await validateAndProcessTransaction(request, testEnvironment(), port)).body.status, 'SAVED_TRANSACTION');
    assert.equal((await validateAndProcessTransaction(request, testEnvironment(), port)).body.status, 'DUPLICATE_IGNORED');
    assert.equal(rows.length, 1);
    assert.equal(rows[0][1], bank);
    assert.equal(/1234567890|0912345678/.test(JSON.stringify(rows[0].slice(5, 8))), false);
    const dropped = await validateAndProcessTransaction({ ...request, body: { ...body, title: 'OTP ma xac thuc' } }, testEnvironment(), port);
    assert.equal(dropped.body.status, 'DROPPED_SENSITIVE');
    assert.equal(rows.length, 1);
  }
});
