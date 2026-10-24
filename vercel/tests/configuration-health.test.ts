import assert from 'node:assert/strict';
import test from 'node:test';
import health from '../api/health.js';
import { configurationIsValid, readGoogleConfiguration, validWebhookSecret, validWebhookUrl } from '../lib/configuration.js';
import { testEnvironment } from './support.js';

test('validates all configuration fields and accepts escaped PEM newlines', () => {
  const env = testEnvironment();
  assert.equal(configurationIsValid(env), true);
  assert.ok(readGoogleConfiguration({ ...env, GOOGLE_PRIVATE_KEY: env.GOOGLE_PRIVATE_KEY!.replace(/\n/g, '\\n') }));
  for (const key of Object.keys(env)) assert.equal(configurationIsValid({ ...env, [key]: undefined }), false, key);
  for (const overrides of [{ WEBHOOK_SECRET: 'replace-with-a-private-secret-value' }, { GOOGLE_PRIVATE_KEY: 'invalid' }, { GOOGLE_CLIENT_EMAIL: 'person@example.invalid' }, { SPREADSHEET_ID: 'replace-with-your-spreadsheet-id' }]) {
    assert.equal(configurationIsValid(testEnvironment(overrides)), false);
  }
});

test('validates HTTPS endpoint and private URL-safe secret policy', () => {
  assert.equal(validWebhookUrl('https://finance.example.invalid/api/transaction'), true);
  for (const url of ['', 'http://finance.example.invalid/api/transaction', 'https://user:pass@finance.example.invalid/api/transaction', 'https://finance.example.invalid/api/transaction?secret=x']) assert.equal(validWebhookUrl(url), false);
  for (const secret of ['', 'short', 'default'.repeat(8), 'placeholder'.repeat(5), 'changeme'.repeat(6)]) assert.equal(validWebhookSecret(secret), false);
});

test('health returns only technical booleans/version, never invokes Sheets, and forbids caching', () => {
  const saved = { ...process.env };
  try {
    for (const ready of [false, true]) {
      Object.assign(process.env, testEnvironment());
      if (!ready) delete process.env.WEBHOOK_SECRET;
      let code = 0;
      let body: unknown;
      const headers: Record<string, string> = {};
      const response = { setHeader: (name: string, value: string) => { headers[name] = value; }, status: (value: number) => { code = value; return { json: (value: unknown) => { body = value; } }; } };
      health({ method: 'GET' }, response);
      assert.equal(code, ready ? 200 : 503);
      assert.deepEqual(body, { alive: true, version: '3.1.0', environmentReady: ready });
      assert.equal(headers['Cache-Control'], 'no-store');
      health({ method: 'POST' }, response);
      assert.equal(code, 405);
    }
  } finally {
    for (const name of Object.keys(process.env)) if (!(name in saved)) delete process.env[name];
    Object.assign(process.env, saved);
  }
});
