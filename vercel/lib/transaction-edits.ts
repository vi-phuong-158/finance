import { createHash, timingSafeEqual } from 'node:crypto';
import { configurationIsValid, readGoogleConfiguration } from './configuration.js';
import type { Environment, GoogleConfiguration } from './configuration.js';
import type { ManualRequest } from './manual-transaction.js';
import { CATEGORY_REGISTRY } from './categories.js';
import { atomicAppendRequests } from './atomic-append.js';
import { maskSensitiveIdentifiers } from '../sensitive-identifiers.js';
import { fromFinanceSnapshot } from './unified-transactions.js';
import type { FinanceSnapshot } from './unified-transactions.js';
import { hasExpectedHeaders } from './sheet-schema.js';
import { EDIT_HEADERS } from './edit-schema.js';

export type EditPort = { read: () => Promise<FinanceSnapshot>; append: (row: unknown[], revisionGuard: string) => Promise<void> };
const digest = (text: string) => createHash('sha256').update(text).digest('hex');

export async function writeTransactionEdit(request: ManualRequest, env: Environment, factory: (config: GoogleConfiguration) => Promise<EditPort>, now = new Date()) {
  const fail = (httpStatus: number, error: string) => ({ httpStatus, body: { error } as Record<string, string> });
  if (request.method !== 'POST') return fail(405, 'METHOD_NOT_ALLOWED');
  if (!configurationIsValid(env)) return fail(503, 'SERVER_CONFIGURATION_ERROR');
  const secret = request.headers?.['x-webhook-secret'];
  if (typeof secret !== 'string' || Buffer.byteLength(secret) !== Buffer.byteLength(env.WEBHOOK_SECRET!)
    || !timingSafeEqual(Buffer.from(secret), Buffer.from(env.WEBHOOK_SECRET!))) return fail(401, 'UNAUTHORIZED');
  if (env.TRANSACTION_EDITS_ENABLED !== '1') return fail(503, 'TRANSACTION_EDITS_DISABLED');
  if (typeof request.headers?.['content-type'] !== 'string' || !/^application\/json(?:\s*;|$)/i.test(request.headers['content-type'])) return fail(415, 'UNSUPPORTED_MEDIA_TYPE');
  const p = request.body as Record<string, unknown> | null;
  if (!p || typeof p !== 'object' || Array.isArray(p) || Object.keys(p).some((key) => !['requestId', 'transactionId', 'category', 'note', 'excluded', 'type', 'baseRevision'].includes(key))
    || typeof p.requestId !== 'string' || !/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/.test(p.requestId)
    || typeof p.transactionId !== 'string' || p.transactionId.length > 200 || typeof p.category !== 'string'
    || (p.type !== 'THU' && p.type !== 'CHI') || !CATEGORY_REGISTRY.some((c) => c.name === p.category && c.active && (c.type === 'BOTH' || c.type === p.type))
    || typeof p.note !== 'string' || Buffer.byteLength(p.note) > 1024 || typeof p.excluded !== 'boolean'
    || typeof p.baseRevision !== 'string' || !/^(?:0|[a-f0-9-]{36})$/.test(p.baseRevision)) return fail(400, 'INVALID_EDIT');
  const row = [p.requestId, p.transactionId, p.category, maskSensitiveIdentifiers(p.note.trim()), p.excluded, p.type, p.baseRevision];
  try {
    const port = await factory(readGoogleConfiguration(env)!);
    const snapshot = await port.read();
    if (!hasExpectedHeaders(snapshot.edits?.[0] ?? [], EDIT_HEADERS)) return fail(503, 'EDIT_SCHEMA_MIGRATION_REQUIRED');
    // Validate the entire audit chain before acknowledging any replay.
    const transactions = fromFinanceSnapshot(snapshot);
    const prior = snapshot.edits!.slice(1).filter((r) => r[0] === p.requestId);
    if (prior.length) return JSON.stringify(prior[0].slice(0, 7)) === JSON.stringify(row)
      ? { httpStatus: 200, body: { status: 'DUPLICATE_IGNORED' } } : fail(409, 'IDEMPOTENCY_CONFLICT');
    const transaction = transactions.find((t) => t.id === p.transactionId);
    if (!transaction) return fail(404, 'TRANSACTION_NOT_FOUND');
    if (transaction.revision !== p.baseRevision) return fail(409, 'EDIT_CONFLICT');
    if (snapshot.edits!.length >= 50001) return fail(503, 'EDIT_SOURCE_FULL');
    try { await port.append([...row, now.toISOString()], digest(`EDIT_REV:${p.transactionId}:${p.baseRevision}`)); }
    catch {
      const refreshed = await port.read();
      const refreshedTransactions = fromFinanceSnapshot(refreshed);
      const retry = refreshed.edits?.slice(1).find((r) => r[0] === p.requestId);
      if (!retry) return fail(refreshedTransactions.find((t) => t.id === p.transactionId)?.revision !== p.baseRevision ? 409 : 503,
        refreshedTransactions.find((t) => t.id === p.transactionId)?.revision !== p.baseRevision ? 'EDIT_CONFLICT' : 'EDIT_RETRY_REQUIRED');
      if (JSON.stringify(retry.slice(0, 7)) !== JSON.stringify(row)) return fail(409, 'IDEMPOTENCY_CONFLICT');
      return { httpStatus: 200, body: { status: 'DUPLICATE_IGNORED' } };
    }
    return { httpStatus: 200, body: { status: 'SAVED_TRANSACTION_EDIT' } };
  } catch { return fail(503, 'EDIT_UNAVAILABLE'); }
}

export async function makeEditPort(config: GoogleConfiguration): Promise<EditPort> {
  const { makeGoogleFinanceReadPort } = await import('./google-finance-read.js');
  const reader = await makeGoogleFinanceReadPort(config);
  const { google } = await import('googleapis');
  const auth = new google.auth.GoogleAuth({ credentials: { client_email: config.clientEmail, private_key: config.privateKey }, scopes: ['https://www.googleapis.com/auth/spreadsheets'] });
  const sheets = google.sheets({ version: 'v4', auth });
  return { read: reader.readFinance, async append(row, revisionGuard) {
    const meta = await sheets.spreadsheets.get({ spreadsheetId: config.spreadsheetId, fields: 'sheets(properties(sheetId,title))' });
    const id = meta.data.sheets?.find((s) => s.properties?.title === 'Transaction_Edits')?.properties?.sheetId;
    if (id == null) throw new Error('EDIT_SCHEMA_MIGRATION_REQUIRED');
    const requests = atomicAppendRequests(id, digest(`EDIT:${row[0]}`), row);
    requests.unshift(atomicAppendRequests(id, revisionGuard, [])[0]);
    await sheets.spreadsheets.batchUpdate({ spreadsheetId: config.spreadsheetId, requestBody: { requests } });
  } };
}
