import { createHash, timingSafeEqual } from 'node:crypto';
import { configurationIsValid, readGoogleConfiguration } from './configuration.js';
import type { Environment, GoogleConfiguration } from './configuration.js';
import type { FinanceSnapshot } from './unified-transactions.js';
import { atomicAppendRequests, guardedAppend } from './atomic-append.js';
import { buildMemoryIndex, memoryObservations, MEMORY_HEADERS, MEMORY_WINDOW, readMemoryCommands, validCommand } from './category-memory.js';
import type { MemoryCommand } from './category-memory.js';
import { hasExpectedHeaders } from './sheet-schema.js';
import { CATEGORY_REGISTRY } from './categories.js';

export type MemoryPort = {
  readFinance: () => Promise<FinanceSnapshot>;
  // null = Category_Memory tab not prepared yet.
  readCommands: () => Promise<unknown[][] | null>;
  append: (row: unknown[], marker: string) => Promise<'APPENDED' | 'DUPLICATE'>;
};
export type MemoryRequest = { method?: string; headers?: Record<string, unknown>; query?: Record<string, unknown>; body?: unknown };
const marker = (requestId: string) => createHash('sha256').update(`MEMORY:${requestId}`).digest('hex');

export async function handleCategoryMemoryRequest(request: MemoryRequest, env: Environment, makePort: (config: GoogleConfiguration, write: boolean) => Promise<MemoryPort>, now = new Date()) {
  const fail = (httpStatus: number, error: string) => ({ httpStatus, body: { error } as Record<string, unknown> });
  if (request.method !== 'GET' && request.method !== 'POST') return fail(405, 'METHOD_NOT_ALLOWED');
  if (!configurationIsValid(env)) return fail(503, 'SERVER_CONFIGURATION_ERROR');
  const secret = request.headers?.['x-webhook-secret'];
  if (typeof secret !== 'string' || Buffer.byteLength(secret) !== Buffer.byteLength(env.WEBHOOK_SECRET!)
    || !timingSafeEqual(Buffer.from(secret), Buffer.from(env.WEBHOOK_SECRET!))) return fail(401, 'UNAUTHORIZED');
  const enabled = env.CATEGORY_MEMORY_ENABLED === '1', writesEnabled = enabled && env.CATEGORY_MEMORY_WRITES_ENABLED === '1';
  if (request.method === 'GET') {
    if (Object.keys(request.query ?? {}).length) return fail(400, 'INVALID_QUERY');
    // Disabled returns no data so the app can clear its cache; it never falls back to stale rules.
    if (!enabled) return { httpStatus: 200, body: { version: 1, enabled: false } as Record<string, unknown> };
    try {
      const port = await makePort(readGoogleConfiguration(env)!, false);
      const [snapshot, rows] = await Promise.all([port.readFinance(), port.readCommands()]);
      const rules = buildMemoryIndex(memoryObservations(snapshot), readMemoryCommands(rows));
      return { httpStatus: 200, body: { version: 1, enabled: true, writesEnabled: writesEnabled && rows !== null,
        generatedAt: now.toISOString(), window: MEMORY_WINDOW, rules,
        categories: CATEGORY_REGISTRY.filter((c) => c.active && c.name !== 'Chưa phân loại').map((c) => ({ name: c.name, type: c.type })) } as Record<string, unknown> };
    } catch (error) {
      const code = error instanceof Error ? error.message : '';
      return fail(503, ['CATEGORY_MEMORY_SCHEMA_MISMATCH', 'FINANCE_SCHEMA_MISMATCH', 'INVALID_FINANCE_ROW', 'DUPLICATE_FINANCE_ID', 'FINANCE_SOURCE_LIMIT_EXCEEDED'].includes(code)
        ? code : 'CATEGORY_MEMORY_UNAVAILABLE');
    }
  }
  if (!writesEnabled) return fail(503, 'CATEGORY_MEMORY_WRITES_DISABLED');
  if (typeof request.headers?.['content-type'] !== 'string' || !/^application\/json(?:\s*;|$)/i.test(request.headers['content-type'])) return fail(415, 'UNSUPPORTED_MEDIA_TYPE');
  const p = request.body as Record<string, unknown> | null;
  if (!p || typeof p !== 'object' || Array.isArray(p)) return fail(400, 'INVALID_MEMORY_COMMAND');
  if (Buffer.byteLength(JSON.stringify(p)) > 2048) return fail(413, 'PAYLOAD_TOO_LARGE');
  if (Object.keys(p).some((key) => !['requestId', 'action', 'identityKey', 'type', 'category'].includes(key))
    || [p.requestId, p.action, p.identityKey ?? '', p.type ?? '', p.category ?? ''].some((v) => typeof v !== 'string')) return fail(400, 'INVALID_MEMORY_COMMAND');
  const command = { requestId: p.requestId, action: p.action, identityKey: p.identityKey ?? '', type: p.type ?? '', category: p.category ?? '', createdAt: now.toISOString() } as MemoryCommand;
  if (!['SET', 'DELETE', 'RESET_ALL'].includes(command.action) || !validCommand(command)) return fail(400, 'INVALID_MEMORY_COMMAND');
  const row = [command.requestId, command.action, command.identityKey, command.type, command.category];
  try {
    const port = await makePort(readGoogleConfiguration(env)!, true);
    const rows = await port.readCommands();
    if (!rows || !hasExpectedHeaders(rows[0] ?? [], MEMORY_HEADERS)) return fail(503, 'CATEGORY_MEMORY_MIGRATION_REQUIRED');
    const replay = (stored: unknown[][]) => stored.slice(1).find((r) => r[0] === command.requestId);
    const prior = replay(rows);
    if (prior) return JSON.stringify(prior.slice(0, 5).map((v) => v ?? '')) === JSON.stringify(row)
      ? { httpStatus: 200, body: { status: 'DUPLICATE_IGNORED' } as Record<string, unknown> } : fail(409, 'IDEMPOTENCY_CONFLICT');
    if (rows.length >= 50001) return fail(503, 'CATEGORY_MEMORY_SOURCE_FULL');
    const saved = await port.append([...row, command.createdAt], marker(command.requestId));
    return { httpStatus: 200, body: { status: saved === 'DUPLICATE' ? 'DUPLICATE_IGNORED' : 'SAVED_CATEGORY_MEMORY' } as Record<string, unknown> };
  } catch { return fail(503, 'CATEGORY_MEMORY_UNAVAILABLE'); }
}

export async function makeCategoryMemoryPort(config: GoogleConfiguration, write: boolean): Promise<MemoryPort> {
  const { makeGoogleFinanceReadPort } = await import('./google-finance-read.js');
  const reader = await makeGoogleFinanceReadPort(config);
  const { google } = await import('googleapis');
  const auth = new google.auth.GoogleAuth({ credentials: { client_email: config.clientEmail, private_key: config.privateKey },
    scopes: [write ? 'https://www.googleapis.com/auth/spreadsheets' : 'https://www.googleapis.com/auth/spreadsheets.readonly'] });
  const sheets = google.sheets({ version: 'v4', auth });
  const tab = async () => (await sheets.spreadsheets.get({ spreadsheetId: config.spreadsheetId,
    fields: 'sheets(properties(sheetId,title,sheetType,gridProperties(rowCount,columnCount)))' })).data.sheets?.find((s) => s.properties?.title === 'Category_Memory')?.properties;
  const port: MemoryPort = {
    readFinance: reader.readFinance,
    async readCommands() {
      const p = await tab();
      if (!p) return null;
      const rows = p.gridProperties?.rowCount ?? 0, columns = p.gridProperties?.columnCount ?? 0;
      if (p.sheetType !== 'GRID' || !rows || rows > 50001 || columns < MEMORY_HEADERS.length) throw new Error('CATEGORY_MEMORY_SCHEMA_MISMATCH');
      // Read one extra column when present so stray data beside the log fails closed.
      const response = await sheets.spreadsheets.values.get({ spreadsheetId: config.spreadsheetId,
        range: `'Category_Memory'!A1:${columns > MEMORY_HEADERS.length ? 'G' : 'F'}${rows}`, valueRenderOption: 'UNFORMATTED_VALUE' });
      return response.data.values ?? [];
    },
    async append(row, id) {
      const sheetId = (await tab())?.sheetId;
      if (sheetId == null) throw new Error('CATEGORY_MEMORY_MIGRATION_REQUIRED');
      return guardedAppend(() => sheets.spreadsheets.batchUpdate({ spreadsheetId: config.spreadsheetId,
        requestBody: { requests: atomicAppendRequests(sheetId, id, row) } }), async () => {
        const stored = (await port.readCommands())?.slice(1).filter((r) => r[0] === row[0]) ?? [];
        return stored.length === 1 && JSON.stringify(stored[0].slice(0, 5)) === JSON.stringify(row.slice(0, 5));
      });
    },
  };
  return port;
}
