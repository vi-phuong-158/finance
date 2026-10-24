import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { createEventId } from '../event-identity.js';

test('same bank, text and postedAt always produce the same eventId', () => {
  assert.equal(createEventId('VCB', 1792815300000, 'GD TK +100,000 VND'), createEventId('VCB', 1792815300000, 'GD TK +100,000 VND'));
});

test('identical content at a different postedAt produces a different eventId', () => {
  assert.notEqual(createEventId('VCB', 1792815300000, 'GD TK +100,000 VND'), createEventId('VCB', 1792815300001, 'GD TK +100,000 VND'));
});

test('Android and backend share fixed UTF-8, Unicode and timestamp hash vectors', () => {
  const rows = readFileSync(new URL('../../shared/event-id-vectors.tsv', import.meta.url), 'utf8').trim().split(/\r?\n/);
  assert.ok(rows.length >= 5);
  for (const row of rows) {
    const [bank, timestamp, base64, expected] = row.split('\t');
    assert.equal(createEventId(bank, Number(timestamp), Buffer.from(base64, 'base64').toString('utf8')), expected);
  }
});
