import assert from 'node:assert/strict';
import test from 'node:test';
import { createEventId, normalizeTransactionText } from '../event-identity.js';
import { validateAndProcessTransaction } from '../lib/transaction-service.js';
import { TRANSACTION_HEADERS, RAW_CAPTURE_HEADERS } from '../lib/sheet-schema.js';
import { testEnvironment, SYNTHETIC_SECRET } from './support.js';
import { syntheticAccount, unknownVcbFixtures, vcbFixtures } from './vcb-fixtures.js';

function pipeline() {
  const transactions: unknown[][] = [], raw: unknown[][] = [];
  let loseResponse = false, factoryCalls = 0;
  const sheets = {
    readHeaders: async () => ({ transaction: TRANSACTION_HEADERS, raw: RAW_CAPTURE_HEADERS }),
    readEventIds: async () => ({ transactionIds: transactions.map((row) => row[8]), rawIds: raw.map((row) => row[3]) }),
    appendTransaction: async (row: unknown[]) => { transactions.push(row); if (loseResponse) { loseResponse = false; throw new Error('synthetic response timeout'); } },
    appendRaw: async (row: unknown[]) => { raw.push(row); },
  };
  const env = testEnvironment();
  return {
    transactions, raw, sheets,
    get factoryCalls() { return factoryCalls; },
    loseNextResponse() { loseResponse = true; },
    send(text: string, postedAt = 1792815300000, title = 'VCB') {
      return validateAndProcessTransaction({ method: 'POST', headers: { 'x-webhook-secret': SYNTHETIC_SECRET, 'content-type': 'application/json' },
        body: { bank: 'VCB', packageName: 'com.VCB', title, text, postedAt, eventId: createEventId('VCB', postedAt, text) },
      }, env, async () => { factoryCalls++; return sheets; });
    },
  };
}

for (const fixture of vcbFixtures) {
  test(`notification → normalize/hash/parser/category/masking → Sheets: ${fixture.name}`, async () => {
    const app = pipeline();
    const response = await app.send(fixture.text);
    assert.equal(response.body.status, 'SAVED_TRANSACTION');
    assert.equal(app.transactions.length, 1);
    const row = app.transactions[0];
    assert.equal(row.length, 9);
    assert.equal(row[2], fixture.type);
    assert.equal(row[3], fixture.amount);
    assert.equal(row[4], fixture.balance);
    assert.equal(row[0], '01/01/2026 12:00');
    assert.ok(String(row[7]).length > 0);
    assert.equal(String(row[5]).includes(syntheticAccount), false);
    assert.equal(String(row[6]).includes('9999-999-999'), false);
    assert.equal(row[8], createEventId('VCB', 1792815300000, normalizeTransactionText(fixture.text)));
    assert.deepEqual(Object.keys(response.body).sort(), ['eventId', 'status']);
  });
}

test('deduplicates normalized replay but preserves equal text at another timestamp', async () => {
  const app = pipeline(), text = vcbFixtures[0].text;
  await app.send(text);
  assert.equal((await app.send(text.replaceAll(' ', '\n\t'))).body.status, 'DUPLICATE_IGNORED');
  assert.equal(app.transactions.length, 1);
  assert.equal((await app.send(text, 1792815300001)).body.status, 'SAVED_TRANSACTION');
  assert.equal(app.transactions.length, 2);
});

test('uncertain VCB formats go only to masked Raw_Capture', async () => {
  const app = pipeline();
  for (const text of unknownVcbFixtures) assert.equal((await app.send(text)).body.status, 'SAVED_RAW_CAPTURE');
  assert.equal(app.transactions.length, 0);
  assert.equal(app.raw.length, unknownVcbFixtures.length);
  assert.ok(app.raw.every((row) => row.length === 4 && !String(row[2]).includes(syntheticAccount)));
});

test('sensitive fixtures never instantiate Sheets or persist raw/parsed rows', async () => {
  const app = pipeline();
  assert.equal((await app.send(vcbFixtures[0].text, 1792815300000, 'Smart OTP')).body.status, 'DROPPED_SENSITIVE');
  assert.equal(app.factoryCalls, 0);
  assert.deepEqual([app.transactions.length, app.raw.length], [0, 0]);
});

test('lost response after successful append is safe on sequential retry', async () => {
  const app = pipeline();
  app.loseNextResponse();
  assert.equal((await app.send(vcbFixtures[0].text)).httpStatus, 503);
  assert.equal((await app.send(vcbFixtures[0].text)).body.status, 'DUPLICATE_IGNORED');
  assert.equal(app.transactions.length, 1);
});

test('schema mismatch and permission failure block all normal writes', async () => {
  const app = pipeline();
  app.sheets.readHeaders = async () => ({ transaction: [], raw: [] });
  assert.equal((await app.send(vcbFixtures[0].text)).body.error, 'SHEET_SCHEMA_MISMATCH');
  app.sheets.readHeaders = async () => { throw { response: { status: 403 } }; };
  assert.equal((await app.send(vcbFixtures[0].text)).body.error, 'SHEETS_PERMISSION_DENIED');
  assert.deepEqual([app.transactions.length, app.raw.length], [0, 0]);
});
