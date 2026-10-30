import assert from 'node:assert/strict';
import test from 'node:test';
import { createClientCache } from '../lib/google-read-client.js';

test('Google client cache reuses matching configuration and separates credentials and scopes', async () => {
  let created = 0;
  const get = createClientCache(async () => ({ id: ++created }));
  const config = { clientEmail: 'synthetic', spreadsheetId: 'synthetic', privateKey: 'synthetic' };
  const [first, concurrent] = await Promise.all([get(config, 'readonly'), get({ ...config }, 'readonly')]);
  assert.strictEqual(first, concurrent);
  for (const other of [{ ...config, spreadsheetId: 'other' }, { ...config, clientEmail: 'other' }, { ...config, privateKey: 'rotated' }]) {
    assert.notStrictEqual(await get(other, 'readonly'), first);
  }
  assert.notStrictEqual(await get(config, 'write'), first);
});

test('failed client creation can be retried', async () => {
  let calls = 0;
  const get = createClientCache(async () => { if (++calls === 1) throw new Error('failure'); return {}; });
  const config = { clientEmail: 'synthetic', spreadsheetId: 'synthetic', privateKey: 'synthetic' };
  await assert.rejects(get(config, 'readonly'));
  await get(config, 'readonly');
  assert.equal(calls, 2);
});
