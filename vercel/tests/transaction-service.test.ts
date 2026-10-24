import assert from 'node:assert/strict';
import { createEventId } from '../event-identity.js';
import test from 'node:test';
import {
  RAW_CAPTURE_HEADERS,
  TRANSACTION_HEADERS,
  validateAndProcessTransaction,
} from '../api/transaction-service.js';

const secret = 'test-only-webhook-secret-value';
const spreadsheetId = 'test-spreadsheet';
const time = Date.UTC(2026, 9, 24, 4, 15);
const expenseText = 'Số dư TK VCB ACCOUNT-TEST -10,000 VND lúc 01-01-2026 12:00:00. Số dư 50,000 VND. Ref MBVCB.TESTREF.TEST SUPERMARKET';

function eventId(bank: string, text: string, postedAt = time): string {
  return createEventId(bank, postedAt, text);
}

function request(overrides: Record<string, unknown> = {}) {
  const body = {
    eventId: eventId('VCB', expenseText),
    bank: 'VCB',
    packageName: 'com.VCB',
    title: 'Thông báo VCB',
    text: expenseText,
    postedAt: time,
    ...overrides,
  };

  return {
    method: 'POST',
    headers: { 'x-webhook-secret': secret, 'content-type': 'application/json' },
    body,
  };
}

function environment(overrides: Record<string, string | undefined> = {}) {
  return {
    WEBHOOK_SECRET: secret,
    SPREADSHEET_ID: spreadsheetId,
    GOOGLE_CLIENT_EMAIL: 'finance-bridge-test@example.invalid',
    GOOGLE_PRIVATE_KEY: 'test-only-key',
    ...overrides,
  };
}

function mockSheets(options: { transactionIds?: unknown[]; rawIds?: unknown[]; headersValid?: boolean; fail?: boolean } = {}) {
  const writes: { transaction?: unknown[]; raw?: unknown[] } = {};
  let headerReads = 0;

  return {
    writes,
    get headerReads() { return headerReads; },
    async readHeaders() {
      headerReads++;
      if (options.fail) throw new Error('test-only downstream failure');
      return options.headersValid === false
        ? { transaction: ['invalid'], raw: [] }
        : { transaction: [...TRANSACTION_HEADERS], raw: [...RAW_CAPTURE_HEADERS] };
    },
    async readEventIds() {
      if (options.fail) throw new Error('test-only downstream failure');
      return { transactionIds: options.transactionIds ?? [], rawIds: options.rawIds ?? [] };
    },
    async appendTransaction(row: unknown[]) {
      if (options.fail) throw new Error('test-only downstream failure');
      writes.transaction = row;
    },
    async appendRaw(row: unknown[]) {
      if (options.fail) throw new Error('test-only downstream failure');
      writes.raw = row;
    },
  };
}

async function run(
  incoming = request(),
  options: { environment?: Record<string, string | undefined>; sheets?: ReturnType<typeof mockSheets> } = {},
) {
  const sheets = options.sheets ?? mockSheets();
  let factoryCalls = 0;
  const result = await validateAndProcessTransaction(
    incoming,
    options.environment ?? environment(),
    async () => {
      factoryCalls++;
      return sheets;
    },
  );
  return { ...result, sheets, factoryCalls };
}

test('rejects unsupported method and fails closed when server configuration is absent', async () => {
  assert.equal((await run({ ...request(), method: 'GET' })).httpStatus, 405);
  const unconfigured = await run(request(), { environment: environment({ WEBHOOK_SECRET: undefined }) });
  assert.equal(unconfigured.httpStatus, 503);
  assert.equal(unconfigured.factoryCalls, 0);
});

test('rejects an incorrect secret before creating a Sheets client', async () => {
  const badSecretRequest = request();
  badSecretRequest.headers['x-webhook-secret'] = 'incorrect-test-secret';

  const result = await run(badSecretRequest);
  assert.equal(result.httpStatus, 401);
  assert.equal(result.factoryCalls, 0);
});

test('rejects missing JSON content type, malformed payload, and an incorrect bank/package pair', async () => {
  const missingContentType = request();
  missingContentType.headers['content-type'] = 'text/plain';
  assert.equal((await run(missingContentType)).httpStatus, 415);

  const malformed = request({ postedAt: 'yesterday' });
  assert.equal((await run(malformed)).httpStatus, 400);

  const mismatchedBank = request({ packageName: 'com.vnpay.bidv' });
  assert.equal((await run(mismatchedBank)).httpStatus, 400);

  const mismatchedEventId = request({ eventId: 'f'.repeat(64) });
  assert.equal((await run(mismatchedEventId)).body.error, 'EVENT_ID_MISMATCH');
});

test('rejects oversized text without creating a Sheets client', async () => {
  const oversized = request({ text: 'x'.repeat(8193) });
  assert.equal((await run(oversized)).httpStatus, 413);

  const escapedText = '"'.repeat(8192);
  const escapedPayload = request({ eventId: eventId('VCB', escapedText), text: escapedText });
  assert.equal((await run(escapedPayload)).httpStatus, 413);
});

test('drops sensitive and non-transaction notifications before touching Sheets', async () => {
  const otpText = 'Số dư TK VCB ACCOUNT-TEST +10,000 VND lúc 01-01-2026 12:00. Mã OTP không chia sẻ mã';
  const otp = await run(request({ eventId: eventId('VCB', otpText), text: otpText }));
  assert.equal(otp.body.status, 'DROPPED_SENSITIVE');
  assert.equal(otp.factoryCalls, 0);

  const advertisementText = 'Ưu đãi đặc biệt hôm nay';
  const advertisement = await run(request({ eventId: eventId('VCB', advertisementText), text: advertisementText }));
  assert.equal(advertisement.body.status, 'DROPPED_SENSITIVE');
  assert.equal(advertisement.factoryCalls, 0);

  const unqualifiedText = 'Số dư tài khoản hiện tại của bạn';
  const unqualified = await run(request({ eventId: eventId('VCB', unqualifiedText), text: unqualifiedText }));
  assert.equal(unqualified.body.status, 'DROPPED_NON_TRANSACTION');
  assert.equal(unqualified.factoryCalls, 0);
});

test('records exactly nine typed transaction fields and does not include parsed notification content in the response', async () => {
  const result = await run();
  assert.equal(result.httpStatus, 200);
  assert.deepEqual(result.body, { status: 'SAVED_TRANSACTION', eventId: eventId('VCB', expenseText) });
  assert.deepEqual(result.sheets.writes.transaction, [
    '01/01/2026 12:00', 'VCB', 'CHI', 10_000, 50_000,
    'TEST SUPERMARKET',
    'MBVCB.TESTREF.TEST SUPERMARKET', 'Siêu thị', eventId('VCB', expenseText),
  ]);
});

test('deduplicates identifiers found in either sheet tab', async () => {
  const id = eventId('VCB', expenseText);
  const inTransactions = await run(request(), { sheets: mockSheets({ transactionIds: [id] }) });
  const inRawCapture = await run(request(), { sheets: mockSheets({ rawIds: [id] }) });

  assert.equal(inTransactions.body.status, 'DUPLICATE_IGNORED');
  assert.equal(inTransactions.sheets.writes.transaction, undefined);
  assert.equal(inRawCapture.body.status, 'DUPLICATE_IGNORED');
  assert.equal(inRawCapture.sheets.writes.transaction, undefined);
});

test('saves unsupported-bank notifications as a masked four-field raw capture row', async () => {
  const text = 'Số dư TK BIDV ' + '8'.repeat(13) + ' +100,000 VND lúc 01-01-2026 12:00. GD chuyen khoan';
  const result = await run(request({
    eventId: eventId('BIDV', text),
    bank: 'BIDV',
    packageName: 'com.vnpay.bidv',
    text,
  }));

  assert.equal(result.body.status, 'SAVED_RAW_CAPTURE');
  assert.equal(result.sheets.writes.raw?.length, 4);
  assert.equal(/\b\d{8,}\b/.test(result.sheets.writes.raw?.[2]?.toString() ?? ''), false);
});

test('dry run validates sheet headers without reading duplicate ids or appending data', async () => {
  const sheets = mockSheets();
  const valid = await run(request({ dryRun: true }), { sheets });
  assert.equal(valid.body.status, 'VALIDATED_TRANSACTION');
  assert.equal(sheets.headerReads, 1);
  assert.equal(sheets.writes.transaction, undefined);

  const wrongSchema = await run(request({ dryRun: true }), { sheets: mockSheets({ headersValid: false }) });
  assert.equal(wrongSchema.httpStatus, 503);
  assert.equal(wrongSchema.body.error, 'SHEET_SCHEMA_MISMATCH');
});

test('maps Sheets failures to a retryable response without leaking error details', async () => {
  const result = await run(request(), { sheets: mockSheets({ fail: true }) });

  assert.equal(result.httpStatus, 503);
  assert.deepEqual(result.body, { error: 'UPSTREAM_UNAVAILABLE' });
});

test('rejects a modified postedAt as EVENT_ID_MISMATCH before touching Sheets', async () => {
  const result = await run(request({ postedAt: time + 1 }));
  assert.equal(result.httpStatus, 400);
  assert.equal(result.body.error, 'EVENT_ID_MISMATCH');
  assert.equal(result.factoryCalls, 0);
});

test('same content at different postedAt values saves two distinct events', async () => {
  const firstId = eventId('VCB', expenseText);
  const secondId = eventId('VCB', expenseText, time + 1);
  const result = await run(request({ postedAt: time + 1, eventId: secondId }), {
    sheets: mockSheets({ transactionIds: [firstId] }),
  });
  assert.notEqual(firstId, secondId);
  assert.equal(result.body.status, 'SAVED_TRANSACTION');
  assert.equal(result.sheets.writes.transaction?.[8], secondId);
});

test('detects duplicates older than the last 1000 rows in both tabs', async () => {
  const id = eventId('VCB', expenseText);
  const ids = [id, ...Array.from({ length: 1500 }, (_, index) => `synthetic-${index}`)];
  for (const options of [{ transactionIds: ids }, { rawIds: ids }]) {
    const result = await run(request(), { sheets: mockSheets(options) });
    assert.equal(result.body.status, 'DUPLICATE_IGNORED');
    assert.deepEqual(result.sheets.writes, {});
  }
});

test('masks separated identifiers in Raw_Capture and preserves money, time and eventId', async () => {
  const identifier = '9'.repeat(4) + ' ' + '9'.repeat(3) + ' ' + '9'.repeat(3);
  const text = `GD TK ${identifier} +100,000 VND. SD 900,000 VND 01-01-2026 12:00:00`;
  const id = eventId('BIDV', text);
  const result = await run(request({ bank: 'BIDV', packageName: 'com.vnpay.bidv', text, eventId: id }));
  assert.equal(result.body.status, 'SAVED_RAW_CAPTURE');
  assert.equal(result.sheets.writes.raw?.[2], 'GD TK *** +100,000 VND. SD 900,000 VND 01-01-2026 12:00:00');
  assert.equal(result.sheets.writes.raw?.[3], id);
});

test('masks identifiers in both parsed description and counterparty, including short Ref', async () => {
  for (const ref of ['9'.repeat(12), '9999 999 999', '9999-999-999', 'MBVCB.TEST.9999-999-999']) {
    const text = expenseText.replace('MBVCB.TESTREF.TEST SUPERMARKET', ref);
    const result = await run(request({ text, eventId: eventId('VCB', text) }));
    assert.equal(result.body.status, 'SAVED_TRANSACTION');
    const row = result.sheets.writes.transaction!;
    assert.equal(row[3], 10_000);
    assert.equal(row[4], 50_000);
    assert.equal(row[5], '***');
    assert.ok(String(row[6]).endsWith('***'));
  }
});

test('security blacklist stays fail-closed even with positive transaction signals', async () => {
  for (const title of ['OTP', 'Smart OTP', 'verification code', 'Mã xác thực', 'Mật khẩu', 'Đăng nhập', 'Thiết bị mới', 'Sinh trắc học', 'Khóa thẻ', 'Mở khóa thẻ', 'CVV', 'Khuyến mại', 'Ưu đãi']) {
    const result = await run(request({ title }));
    assert.equal(result.body.status, 'DROPPED_SENSITIVE', title);
    assert.equal(result.factoryCalls, 0);
  }
});

