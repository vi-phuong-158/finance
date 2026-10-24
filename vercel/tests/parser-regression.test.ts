import assert from 'node:assert/strict';
import test from 'node:test';
import { matchCategory } from '../categories.js';
import { parseVcb } from '../parsers/vcb.js';

const BASE = 'Số dư TK VCB ACCOUNT-TEST -10,000 VND lúc 01-01-2026 12:00:00. Số dư 50,000 VND. Ref MBVCB.TESTREF.TEST SUPERMARKET';

test('parses a synthetic VCB expense and redacts account numbers from descriptions', () => {
  const parsed = parseVcb(BASE);

  assert.ok(parsed);
  assert.equal(parsed.bank, 'VCB');
  assert.equal(parsed.type, 'CHI');
  assert.equal(parsed.amount, 10_000);
  assert.equal(parsed.balance, 50_000);
  assert.equal(parsed.time, '01/01/2026 12:00');
  assert.equal(parsed.category, 'Siêu thị');
  assert.equal(/\b\d{8,}\b/.test(parsed.description), false);
});

test('parses an income and matches the owner transfer keyword', () => {
  const parsed = parseVcb(BASE.replace('-10,000', '+5,000').replace('50,000', '55,000').replace('MBVCB.TESTREF.TEST SUPERMARKET', 'IBVCB.TESTREF.TEST ACCOUNT chuyen tien noi bo'));

  assert.ok(parsed);
  assert.equal(parsed.type, 'THU');
  assert.equal(parsed.amount, 5_000);
  assert.equal(parsed.category, 'Chuyển nội bộ');
});

test('rejects invalid calendar dates, invalid times, malformed grouped money, and unsafe integer amounts', () => {
  assert.equal(parseVcb(BASE.replace('01-01-2026', '31-02-2026')), null);
  assert.equal(parseVcb(BASE.replace('12:00:00', '25:15:33')), null);
  assert.equal(parseVcb(BASE.replace('-10,000', '-1,01,200')), null);
  assert.equal(parseVcb(BASE.replace('-10,000', '-9,007,199,254,741,000')), null);
});

test('omits an unsafe balance without discarding an otherwise valid transaction', () => {
  const parsed = parseVcb(BASE.replace('50,000', '9,007,199,254,741,000'));

  assert.ok(parsed);
  assert.equal(parsed.amount, 10_000);
  assert.equal(parsed.balance, '');
});

test('masks long account numbers even when the VCB reference is missing', () => {
  const missingRefText = BASE.replace(' Ref MBVCB.TESTREF.TEST SUPERMARKET', '');
  const parsed = parseVcb(missingRefText);

  assert.ok(parsed);
  assert.equal(/\b\d{8,}\b/.test(parsed.description), false);

  const longAccount = parseVcb(missingRefText.replace('ACCOUNT-TEST', '9'.repeat(13)));
  assert.ok(longAccount);
  assert.equal(/\b\d{8,}\b/.test(longAccount.description), false);
});
test('keeps merchant classification specific and removes Vietnamese accents', () => {
  assert.equal(matchCategory('LOTTERIA', 'CHI'), 'Ăn uống');
  assert.equal(matchCategory('NHÀ THUỐC LONG CHÂU', 'CHI'), 'Y tế');
  assert.equal(matchCategory('LOTTE MART', 'CHI'), 'Siêu thị');
  assert.equal(matchCategory('nhận lương', 'THU'), 'Thu nhập / Nhận CK');
});

