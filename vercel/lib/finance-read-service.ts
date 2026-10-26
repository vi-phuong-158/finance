import { timingSafeEqual } from 'node:crypto';
import { configurationIsValid, readGoogleConfiguration } from './configuration.js';
import type { Environment, GoogleConfiguration } from './configuration.js';
import { fromFinanceSnapshot } from './unified-transactions.js';
import type { FinanceReadPort } from './unified-transactions.js';
import { filterTransactions, paginateTransactions, parseFinanceQuery } from './finance-query.js';
import { buildDashboard } from './dashboard-service.js';
import { CATEGORY_REGISTRY } from './categories.js';
import { memoryIdentityKey, originalIdentities } from './category-memory.js';

export type ReadRequest = { method?: string; headers?: Record<string, string | string[] | undefined>; query?: Record<string, unknown> };
export async function readFinance(request: ReadRequest, env: Environment,
  makePort: (config: GoogleConfiguration) => Promise<FinanceReadPort>, endpoint: 'dashboard' | 'transactions', now = new Date()) {
  const fail = (httpStatus: number, error: string) => ({ httpStatus, body: { error } as Record<string, unknown> });
  if (request.method !== 'GET') return fail(405, 'METHOD_NOT_ALLOWED');
  if (!configurationIsValid(env)) return fail(503, 'SERVER_CONFIGURATION_ERROR');
  const supplied = request.headers?.['x-webhook-secret'];
  if (typeof supplied !== 'string') return fail(401, 'UNAUTHORIZED');
  const bytes = Buffer.from(supplied), expected = Buffer.from(env.WEBHOOK_SECRET!);
  if (bytes.length !== expected.length || !timingSafeEqual(bytes, expected)) return fail(401, 'UNAUTHORIZED');
  try {
    const query = parseFinanceQuery(request.query ?? {}, endpoint === 'dashboard', now);
    const port = await makePort(readGoogleConfiguration(env)!);
    const snapshot = await port.readFinance();
    const rows = filterTransactions(fromFinanceSnapshot(snapshot), query);
    if (endpoint === 'dashboard') return { httpStatus: 200, body: buildDashboard(rows, query, now) };
    const page = paginateTransactions(rows, query, env.WEBHOOK_SECRET!);
    if (env.CATEGORY_MEMORY_ENABLED === '1') {
      // Lets the app look up its offline cache without re-deriving identity from edited fields.
      const identities = originalIdentities(snapshot, memoryIdentityKey(env.WEBHOOK_SECRET!));
      page.transactions = page.transactions.map((t) => ({ ...t, memoryKey: identities.get(t.id)?.key ?? null }));
    }
    return { httpStatus: 200, body: { ...page, lastUpdatedAt: now.toISOString(),
        editingEnabled: env.TRANSACTION_EDITS_ENABLED === '1' && !!snapshot.edits,
        categories: CATEGORY_REGISTRY.filter((c) => c.active).map((c) => ({ name: c.name, type: c.type })) } as Record<string, unknown> };
  } catch (error) {
    const code = error instanceof Error ? error.message : '';
    if (['INVALID_QUERY', 'INVALID_CURSOR'].includes(code)) return fail(400, code);
    if (code === 'CURSOR_STALE') return fail(409, code);
    if (['FINANCE_SCHEMA_MISMATCH', 'INVALID_FINANCE_ROW', 'INVALID_HISTORY_ROW', 'DUPLICATE_FINANCE_ID', 'FINANCE_SOURCE_LIMIT_EXCEEDED'].includes(code)) return fail(503, code);
    return fail(503, 'FINANCE_READ_UNAVAILABLE');
  }
}

export type ReadResponse = { setHeader: (name: string, value: string) => unknown;
  status: (code: number) => { json: (body: Record<string, unknown>) => unknown } };
export async function handleFinanceRead(request: ReadRequest, response: ReadResponse, endpoint: 'dashboard' | 'transactions') {
  response.setHeader('Cache-Control', 'private, no-store, max-age=0');
  response.setHeader('CDN-Cache-Control', 'no-store');
  response.setHeader('Vercel-CDN-Cache-Control', 'no-store');
  response.setHeader('Vary', 'X-Webhook-Secret');
  response.setHeader('X-Content-Type-Options', 'nosniff');
  if (request.method !== 'GET') response.setHeader('Allow', 'GET');
  const { makeGoogleFinanceReadPort } = await import('./google-finance-read.js');
  const result = await readFinance(request, process.env, makeGoogleFinanceReadPort, endpoint);
  return response.status(result.httpStatus).json(result.body);
}
