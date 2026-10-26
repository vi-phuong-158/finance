import assert from 'node:assert/strict';
import test from 'node:test';
import { maskSensitiveIdentifiers } from '../sensitive-identifiers.js';

// Deliberately repeated digits, never a real account or card number.
test('masks continuous identifiers', () => {
  assert.equal(maskSensitiveIdentifiers(`TK ${'9'.repeat(10)}`), 'TK ***');
});

test('masks identifiers separated by spaces and non-breaking spaces', () => {
  assert.equal(maskSensitiveIdentifiers('TK 9999 999 999'), 'TK ***');
  assert.equal(maskSensitiveIdentifiers('TK 9999\u00a0999\u00a0999'), 'TK ***');
});

test('masks identifiers separated by hyphens', () => {
  assert.equal(maskSensitiveIdentifiers('TK 9999-999-999'), 'TK ***');
});

test('masks other common financial identifier separators', () => {
  for (const identifier of ['9999.999.999', '9999/999/999', '9999_999_999']) {
    assert.equal(maskSensitiveIdentifiers(`TK ${identifier}`), 'TK ***');
  }
});

test('masks card-length identifiers and multiple identifiers', () => {
  assert.equal(maskSensitiveIdentifiers('9999 9999 9999 9999 -> 8888-888-888'), '*** -> ***');
});

test('preserves transaction amounts and balance while masking the account', () => {
  assert.equal(maskSensitiveIdentifiers('TK 9999 999 999 +100,000 VND SD 999999999 VND'), 'TK *** +100,000 VND SD 999999999 VND');
  assert.equal(maskSensitiveIdentifiers('GD TK 9999-999-999 +100000000'), 'GD TK *** +100000000');
});

test('preserves 100,000 VND and common currency formats', () => {
  for (const amount of ['100,000 VND', '100.000 VNĐ', '100 000 ₫', '1000000000 VND']) {
    assert.equal(maskSensitiveIdentifiers(amount), amount);
  }
});

test('preserves date/time tokens and short alphanumeric codes; masks long digit runs inside codes', () => {
  const text = '01-01-2026 12:00:00 / 2026-01-01 / 01/01/2026 Ref a9999999b';
  assert.equal(maskSensitiveIdentifiers(text), text);
  assert.equal(maskSensitiveIdentifiers('Ref a999999999b'), 'Ref a***b');
});
