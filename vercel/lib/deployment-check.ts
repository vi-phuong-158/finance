import { createEventId } from '../event-identity.js';
import { validWebhookSecret, validWebhookUrl } from './configuration.js';

export async function checkDeployment(endpoint: string, secret: string, fetcher: typeof fetch = fetch) {
  if (!validWebhookUrl(endpoint) || !validWebhookSecret(secret)) throw new Error('CLIENT_CONFIGURATION_ERROR');
  const health = await fetcher(new URL('/api/health', endpoint), { redirect: 'error', signal: AbortSignal.timeout(20_000) });
  const healthBody = await health.json() as { alive?: unknown; environmentReady?: unknown };
  if (!health.ok || healthBody.alive !== true || healthBody.environmentReady !== true) throw new Error('HEALTH_NOT_READY');
  const postedAt = Date.now();
  const text = 'Số dư TK VCB ACCOUNT-TEST +1,000 VND lúc 01-01-2026 12:00:00. Số dư 2,000 VND. Ref TEST.CHECK';
  const response = await fetcher(endpoint, {
    method: 'POST', redirect: 'error', signal: AbortSignal.timeout(20_000),
    headers: { 'Content-Type': 'application/json', 'X-Webhook-Secret': secret },
    body: JSON.stringify({ bank: 'VCB', packageName: 'com.VCB', title: 'Connection check', text, postedAt, eventId: createEventId('VCB', postedAt, text), dryRun: true }),
  });
  const body = await response.json() as { status?: unknown };
  if (!response.ok || body.status !== 'VALIDATED_TRANSACTION') throw new Error('DRY_RUN_FAILED');
  return { health: 'READY', dryRun: 'VALIDATED_TRANSACTION', writes: 0 };
}
