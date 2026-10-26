import { createHash, timingSafeEqual } from 'node:crypto';
import { configurationIsValid, readGoogleConfiguration } from './configuration.js';
import type { Environment, GoogleConfiguration } from './configuration.js';
import { CATEGORY_REGISTRY } from './categories.js';
import { hasExpectedHeaders, TRANSACTION_HEADERS_V2 } from './sheet-schema.js';
import { maskSensitiveIdentifiers } from '../sensitive-identifiers.js';
import { validDate } from './moneylover/schema.js';
import { atomicAppendRequests, guardedAppend } from './atomic-append.js';

export type ManualPort = { readTransactions: () => Promise<unknown[][]>; append: (row: unknown[]) => Promise<void | 'APPENDED' | 'DUPLICATE'> };
export type ManualRequest = { method?: string; headers?: Record<string, unknown>; body?: unknown };

export function manualRow(body: unknown): unknown[] {
  if (!body || typeof body !== 'object' || Array.isArray(body)) throw new Error('INVALID_PAYLOAD');
  const p = body as Record<string, unknown>;
  if (Object.keys(p).some((k) => !['requestId', 'dateTime', 'type', 'amount', 'category', 'account', 'note'].includes(k))) throw new Error('INVALID_PAYLOAD');
  if (typeof p.requestId !== 'string' || !/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/.test(p.requestId)
    || typeof p.dateTime !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(p.dateTime)
    || !validDate(p.dateTime.slice(0, 10)) || Number(p.dateTime.slice(11, 13)) > 23 || Number(p.dateTime.slice(14)) > 59
    || (p.type !== 'THU' && p.type !== 'CHI') || typeof p.amount !== 'number' || !Number.isSafeInteger(p.amount) || p.amount <= 0 || p.amount > 1e12
    || typeof p.category !== 'string' || !CATEGORY_REGISTRY.some((c) => c.name === p.category && c.active && (c.type === p.type || c.type === 'BOTH'))
    || typeof p.account !== 'string' || !['VCB', 'VIETINBANK', 'BIDV', 'CASH'].includes(p.account)
    || typeof p.note !== 'string' || Buffer.byteLength(p.note) > 1024) throw new Error('INVALID_PAYLOAD');
  const date = p.dateTime;
  return [`${date.slice(8, 10)}/${date.slice(5, 7)}/${date.slice(0, 4)} ${date.slice(11)}`, p.account, p.type,
    p.amount, '', '', maskSensitiveIdentifiers(p.note.trim()), p.category,
    createHash('sha256').update(`MANUAL:${p.requestId}`).digest('hex'), 'MANUAL', p.account === 'CASH' ? 'Tiền mặt' : p.account];
}

export async function writeManual(request: ManualRequest, env: Environment, makePort: (config: GoogleConfiguration) => Promise<ManualPort>) {
  const result = (httpStatus: number, body: Record<string, string>) => ({ httpStatus, body });
  if (request.method !== 'POST') return result(405, { error: 'METHOD_NOT_ALLOWED' });
  if (!configurationIsValid(env)) return result(503, { error: 'SERVER_CONFIGURATION_ERROR' });
  const supplied = request.headers?.['x-webhook-secret'];
  if (typeof supplied !== 'string' || Buffer.byteLength(supplied) !== Buffer.byteLength(env.WEBHOOK_SECRET!)
    || !timingSafeEqual(Buffer.from(supplied), Buffer.from(env.WEBHOOK_SECRET!))) return result(401, { error: 'UNAUTHORIZED' });
  if (typeof request.headers?.['content-type'] !== 'string' || !/^application\/json(?:\s*;|$)/i.test(request.headers['content-type'])) return result(415, { error: 'UNSUPPORTED_MEDIA_TYPE' });
  let row: unknown[];
  try { row = manualRow(request.body); } catch { return result(400, { error: 'INVALID_PAYLOAD' }); }
  try {
    const port = await makePort(readGoogleConfiguration(env)!);
    const rows = await port.readTransactions();
    if (!hasExpectedHeaders(rows[0] ?? [], TRANSACTION_HEADERS_V2)) return result(503, { error: 'MANUAL_SCHEMA_MIGRATION_REQUIRED' });
    const existing = rows.slice(1).filter((r) => r[8] === row[8]);
    if (existing.length > 1) return result(503, { error: 'DUPLICATE_FINANCE_ID' });
    if (existing.length) {
      return JSON.stringify(existing[0]) === JSON.stringify(row) ? result(200, { status: 'DUPLICATE_IGNORED' })
        : result(409, { error: 'IDEMPOTENCY_CONFLICT' });
    }
    if (rows.length >= 50001) return result(503, { error: 'LEDGER_SOURCE_FULL' });
    const saved = await port.append(row);
    return result(200, { status: saved === 'DUPLICATE' ? 'DUPLICATE_IGNORED' : 'SAVED_TRANSACTION' });
  } catch { return result(503, { error: 'UPSTREAM_UNAVAILABLE' }); }
}

export async function makeManualPort(config: GoogleConfiguration): Promise<ManualPort> {
  const { google } = await import('googleapis');
  const auth = new google.auth.GoogleAuth({ credentials: { client_email: config.clientEmail, private_key: config.privateKey }, scopes: ['https://www.googleapis.com/auth/spreadsheets'] });
  const sheets = google.sheets({ version: 'v4', auth });
  const port: ManualPort = {
    async readTransactions() {
      const meta = await sheets.spreadsheets.get({ spreadsheetId: config.spreadsheetId, fields: 'sheets(properties(title,gridProperties(rowCount)))' });
      const count = meta.data.sheets?.find((s) => s.properties?.title === 'Transactions')?.properties?.gridProperties?.rowCount;
      if (!count || count > 50001) throw new Error('SOURCE_LIMIT');
      const response = await sheets.spreadsheets.values.get({ spreadsheetId: config.spreadsheetId, range: `Transactions!A1:L${count}`, valueRenderOption: 'UNFORMATTED_VALUE' });
      return response.data.values ?? [];
    },
    async append(row) {
      const meta = await sheets.spreadsheets.get({ spreadsheetId: config.spreadsheetId, fields: 'sheets(properties(sheetId,title))' });
      const id = meta.data.sheets?.find((s) => s.properties?.title === 'Transactions')?.properties?.sheetId;
      if (id == null) throw new Error('SCHEMA_MISMATCH');
      return guardedAppend(() => sheets.spreadsheets.batchUpdate({ spreadsheetId: config.spreadsheetId,
        requestBody: { requests: atomicAppendRequests(id, String(row[8]), row) } }), async () => {
        const existing = (await port.readTransactions()).filter((r) => r[8] === row[8]);
        return existing.length === 1 && JSON.stringify(existing[0]) === JSON.stringify(row);
      });
    },
  };
  return port;
}
