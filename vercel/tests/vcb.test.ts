import assert from 'assert';
import { parseVcb } from '../parsers/vcb.js';

const sampleExpense =
  'Số dư TK VCB ACCOUNT-TEST -10,000 VND lúc 01-01-2026 12:00:00. Số dư 50,000 VND. Ref MBVCB.TESTREF.TEST SUPERMARKET';

const res1 = parseVcb(sampleExpense);
assert(res1 !== null, 'Phải parse được mẫu VCB giả lập');
assert.strictEqual(res1.time, '01/01/2026 12:00');
assert.strictEqual(res1.bank, 'VCB');
assert.strictEqual(res1.type, 'CHI');
assert.strictEqual(res1.amount, 10000);
assert.strictEqual(res1.balance, 50000);
assert.strictEqual(res1.counterparty, 'TEST SUPERMARKET');
assert.strictEqual(res1.category, 'Siêu thị');

const sampleIncome =
  'Số dư TK VCB ACCOUNT-TEST +5,000 VND lúc 01-01-2026 13:00:00. Số dư 55,000 VND. Ref IBVCB.TESTREF.TEST ACCOUNT chuyen tien noi bo';

const res2 = parseVcb(sampleIncome);
assert(res2 !== null, 'Phải parse được giao dịch tiền vào / chuyển nội bộ');
assert.strictEqual(res2.type, 'THU');
assert.strictEqual(res2.amount, 5000);
assert.strictEqual(res2.category, 'Chuyển nội bộ');

console.log('✅ Existing VCB parser fixtures passed.');

