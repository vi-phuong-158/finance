import assert from 'node:assert/strict';
import test from 'node:test';
import { checkDeployment } from '../lib/deployment-check.js';
import { SYNTHETIC_SECRET } from './support.js';

test('deployment check calls health and only a synthetic dry-run without following redirects', async () => {
  const calls: { url: string; options?: RequestInit }[] = [];
  const fetcher = async (input: string | URL | Request, options?: RequestInit) => {
    calls.push({ url: String(input), options });
    return Response.json(calls.length === 1 ? { alive: true, environmentReady: true } : { status: 'VALIDATED_TRANSACTION' });
  };
  assert.deepEqual(await checkDeployment('https://finance.example.invalid/api/transaction', SYNTHETIC_SECRET, fetcher as typeof fetch), { health: 'READY', dryRun: 'VALIDATED_TRANSACTION', writes: 0 });
  assert.equal(calls[0].options?.headers, undefined);
  assert.equal(JSON.parse(String(calls[1].options?.body)).dryRun, true);
  assert.ok(calls.every((call) => call.options?.redirect === 'error'));
});

test('deployment check stops at failed health or dry-run and rejects HTTP before sending secrets', async () => {
  let calls = 0;
  const fetcher = (async () => { calls++; return Response.json({ alive: true, environmentReady: false }, { status: 503 }); }) as typeof fetch;
  await assert.rejects(checkDeployment('http://finance.example.invalid/api/transaction', SYNTHETIC_SECRET, fetcher));
  assert.equal(calls, 0);
  await assert.rejects(checkDeployment('https://finance.example.invalid/api/transaction', SYNTHETIC_SECRET, fetcher));
  assert.equal(calls, 1);
  const badDryRun = (async () => Response.json({ alive: true, environmentReady: true, status: 'SAVED_TRANSACTION' })) as typeof fetch;
  await assert.rejects(checkDeployment('https://finance.example.invalid/api/transaction', SYNTHETIC_SECRET, badDryRun), /DRY_RUN_FAILED/);
});
