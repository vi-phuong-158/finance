import { createHash, timingSafeEqual } from 'node:crypto';
import { readGoogleConfiguration, validWebhookSecret } from './configuration.js';
import type { Environment, GoogleConfiguration } from './configuration.js';
import { buildInterest, dateOnly, parseInterestSnapshot } from './interest.js';
import type { InterestSnapshot } from './interest.js';
import { loanFormulas, loanPeriodFormulas, trancheFormulas } from './interest-sheet-formulas.js';
import { atomicAppendRequests } from './atomic-append.js';
import { maskSensitiveIdentifiers } from '../sensitive-identifiers.js';
import type { ManualRequest } from './manual-transaction.js';
import { hasExpectedHeaders } from './sheet-schema.js';
import { LOAN_AUDIT_HEADERS } from './loan-audit-schema.js';
import { formulaSeparator, localizeFormula } from './sheet-formula-locale.js';

export type LoanWriteSnapshot = { interest: InterestSnapshot; audit: unknown[][]; ids: Record<string, number>; locale: string };
export type LoanWritePort = { read: () => Promise<LoanWriteSnapshot>; write: (requests: Record<string, unknown>[]) => Promise<void> };
const hash = (s: string) => createHash('sha256').update(s).digest('hex');
const serial = (s: string) => Date.parse(dateOnly(s) + 'T00:00:00Z') / 86400000 + 25569;
const uuid = /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/;
const kinds = ['CREATE_LOAN', 'DISBURSEMENT', 'PRINCIPAL_PAYMENT', 'INTEREST_PAYMENT', 'UPDATE_INFO'];

export function loanCommand(body: unknown): Record<string, string | number> {
  if (!body || typeof body !== 'object' || Array.isArray(body)) throw new Error('INVALID_LOAN_PAYLOAD');
  const p = body as Record<string, unknown>;
  if (typeof p.operation !== 'string' || !kinds.includes(p.operation) || typeof p.requestId !== 'string' || !uuid.test(p.requestId)) throw new Error('INVALID_LOAN_PAYLOAD');
  const allowed = ['requestId', 'operation', 'note', ...(p.operation !== 'CREATE_LOAN' ? ['loanId'] : []),
    ...(p.operation === 'CREATE_LOAN' || p.operation === 'UPDATE_INFO' ? ['borrowerName'] : []),
    ...(p.operation !== 'UPDATE_INFO' ? ['date', 'amount'] : []), ...(p.operation === 'CREATE_LOAN' || p.operation === 'DISBURSEMENT' ? ['annualRate', 'maturity'] : [])];
  if (Object.keys(p).some((k) => !allowed.includes(k))) throw new Error('INVALID_LOAN_PAYLOAD');
  const result: Record<string, string | number> = { requestId: p.requestId, operation: p.operation };
  if (p.operation !== 'CREATE_LOAN') {
    if (typeof p.loanId !== 'string' || !p.loanId.trim() || p.loanId.length > 200) throw new Error('INVALID_LOAN_PAYLOAD');
    result.loanId = p.loanId;
  }
  if (typeof p.note !== 'string' || Buffer.byteLength(p.note) > 1024) throw new Error('INVALID_LOAN_PAYLOAD');
  result.note = maskSensitiveIdentifiers(p.note.trim());
  if (p.operation === 'CREATE_LOAN' || p.operation === 'UPDATE_INFO') {
    if (typeof p.borrowerName !== 'string' || !p.borrowerName.trim() || p.borrowerName.length > 200) throw new Error('INVALID_LOAN_PAYLOAD');
    result.borrowerName = p.borrowerName.trim();
  }
  if (p.operation !== 'UPDATE_INFO') {
    result.date = dateOnly(p.date);
    if (typeof p.amount !== 'number' || !Number.isSafeInteger(p.amount) || p.amount <= 0 || p.amount > 1e12) throw new Error('INVALID_LOAN_PAYLOAD');
    result.amount = p.amount;
  }
  if (p.operation === 'CREATE_LOAN' || p.operation === 'DISBURSEMENT') {
    if (typeof p.annualRate !== 'number' || !Number.isFinite(p.annualRate) || p.annualRate < 0 || p.annualRate > 1) throw new Error('INVALID_LOAN_PAYLOAD');
    result.annualRate = p.annualRate;
    result.maturity = p.maturity === '' ? '' : dateOnly(p.maturity);
    if (result.maturity && result.maturity < result.date) throw new Error('INVALID_LOAN_PAYLOAD');
  }
  return result;
}

function auditRows(rows: unknown[][]): unknown[][] {
  if (!hasExpectedHeaders(rows[0] ?? [], LOAN_AUDIT_HEADERS)) throw new Error('LOAN_AUDIT_MIGRATION_REQUIRED');
  const values = rows.slice(1).filter((r) => r.some((v) => v !== '' && v != null)), seen = new Set<string>();
  for (const row of values) {
    if (row.length !== 7 || typeof row[0] !== 'string' || !uuid.test(row[0]) || seen.has(row[0]) || typeof row[1] !== 'string'
      || !kinds.includes(String(row[2])) || typeof row[3] !== 'string' || !/^[a-f0-9]{64}$/.test(row[3])
      || typeof row[4] !== 'string' || !Number.isFinite(Date.parse(row[4])) || typeof row[5] !== 'string' || typeof row[6] !== 'string') throw new Error('INVALID_LOAN_AUDIT');
    const command = loanCommand(JSON.parse(row[5]));
    if (command.requestId !== row[0] || command.operation !== row[2] || hash(JSON.stringify(command)) !== row[3]
      || (command.operation === 'CREATE_LOAN' ? row[1] !== `LN-${hash(String(command.requestId)).slice(0, 24)}` : command.loanId !== row[1])
      || !Array.isArray(JSON.parse(row[6]))) throw new Error('INVALID_LOAN_AUDIT');
    seen.add(row[0]);
  }
  return values;
}
function freeRow(rows: unknown[][], noteColumn?: number): number {
  for (let index = 1; index < 300; index++) {
    const row = rows[index] ?? [];
    if (!row.slice(0, 6).some((v) => v !== '' && v != null) && (noteColumn === undefined || !row[noteColumn])) return index;
  }
  throw new Error('LOAN_SOURCE_FULL');
}
function cells(sheetId: number, row: number, column: number, values: unknown[]) {
  return { updateCells: { start: { sheetId, rowIndex: row, columnIndex: column }, rows: [{ values: values.map((value) => ({ userEnteredValue:
    typeof value === 'number' ? { numberValue: value } : { stringValue: String(value) } })) }], fields: 'userEnteredValue' } };
}
function formulas(sheetId: number, row: number, column: number, values: string[], separator: ',' | ';') {
  return { updateCells: { start: { sheetId, rowIndex: row, columnIndex: column }, rows: [{ values: values.map((value) => ({ userEnteredValue: { formulaValue: localizeFormula(value, separator) } })) }], fields: 'userEnteredValue' } };
}

export function planLoanCommand(snapshot: LoanWriteSnapshot, command: ReturnType<typeof loanCommand>, now: Date) {
  const separator = formulaSeparator(snapshot.locale);
  const audit = auditRows(snapshot.audit), { data, asOf } = parseInterestSnapshot(snapshot.interest);
  buildInterest(data, asOf);
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
  if (asOf !== today || snapshot.interest.config[2]?.[1]) throw new Error('LOAN_SIMULATION_ACTIVE');
  if (command.date && command.date > today) throw new Error('INVALID_LOAN_PAYLOAD');
  for (const title of ['Khoản vay', 'Đợt giải ngân', 'Giao dịch', 'Finance_App_Audit']) if (!Number.isInteger(snapshot.ids[title]) || snapshot.ids[title] < 0) throw new Error('LOAN_AUDIT_MIGRATION_REQUIRED');
  const fingerprint = hash(JSON.stringify(command));
  const previous = audit.find((r) => r[0] === command.requestId);
  if (previous) {
    if (previous[3] !== fingerprint) throw new Error('IDEMPOTENCY_CONFLICT');
    return { requests: [] as Record<string, unknown>[], loanId: String(previous[1]) };
  }
  if (snapshot.audit.length >= 50001 || ((command.operation === 'PRINCIPAL_PAYMENT' || command.operation === 'INTEREST_PAYMENT') && snapshot.interest.payments.length >= 50001)) throw new Error('LOAN_SOURCE_FULL');
  const creating = command.operation === 'CREATE_LOAN';
  const loanId = creating ? `LN-${hash(String(command.requestId)).slice(0, 24)}` : String(command.loanId);
  const loan = data.loans.find((l) => l.id === loanId);
  if (!creating && !loan) throw new Error('LOAN_NOT_FOUND');
  if (creating && loan) throw new Error('LOAN_ID_CONFLICT');
  const loanIndex = creating ? freeRow(snapshot.interest.loans, 14) : snapshot.interest.loans.findIndex((r) => r[0] === loanId);
  const before = creating ? [] : snapshot.interest.loans[loanIndex].slice(0, 6).concat(snapshot.interest.loans[loanIndex][14] ?? '');
  const auditRow = [command.requestId, loanId, command.operation, fingerprint, now.toISOString(), JSON.stringify(command), JSON.stringify(before)];
  const requests: Record<string, unknown>[] = atomicAppendRequests(snapshot.ids.Finance_App_Audit, hash(`LOAN:${command.requestId}`), auditRow);
  // Consume a persistent revision in the SAME financial batch, so two repayments
  // validated against one principal balance cannot both commit.
  const revision = { loan, tranches: data.disbursements.filter((t) => t.loanId === loanId), payments: data.payments.filter((p) => p.loanId === loanId), audit: audit.filter((r) => r[1] === loanId) };
  requests.push(atomicAppendRequests(snapshot.ids.Finance_App_Audit, hash(`LOAN_REV:${loanId}:${JSON.stringify(revision)}`), [])[0]);
  if (creating) {
    requests.push(atomicAppendRequests(snapshot.ids['Khoản vay'], hash(`LOAN_SLOT:${loanIndex}`), [])[0]);
    data.loans.push({ id: loanId, name: String(command.borrowerName), principal: Number(command.amount) });
    requests.push(cells(snapshot.ids['Khoản vay'], loanIndex, 0, [loanId, command.borrowerName, command.amount, command.annualRate, serial(String(command.date)), command.maturity ? serial(String(command.maturity)) : '']));
    requests.push(formulas(snapshot.ids['Khoản vay'], loanIndex, 6, loanFormulas(loanIndex + 1), separator));
    requests.push(cells(snapshot.ids['Khoản vay'], loanIndex, 14, [command.note]));
    requests.push(formulas(snapshot.ids['Khoản vay'], loanIndex, 15, loanPeriodFormulas(loanIndex + 1), separator));
  }
  if (creating || command.operation === 'DISBURSEMENT') {
    const index = freeRow(snapshot.interest.disbursements);
    const tranche = { id: `D-${hash(String(command.requestId)).slice(0, 24)}`, loanId, principal: Number(command.amount), annualRate: Number(command.annualRate), date: String(command.date), maturity: command.maturity ? String(command.maturity) : null };
    data.disbursements.push(tranche);
    requests.push(atomicAppendRequests(snapshot.ids['Đợt giải ngân'], hash(`TRANCHE_SLOT:${index}`), [])[0]);
    requests.push(cells(snapshot.ids['Đợt giải ngân'], index, 0, [tranche.id, loanId, tranche.principal, tranche.annualRate, serial(tranche.date), tranche.maturity ? serial(tranche.maturity) : '']));
    requests.push(formulas(snapshot.ids['Đợt giải ngân'], index, 6, trancheFormulas(index + 1), separator));
    if (!creating) {
      if (loan!.principal + tranche.principal > 1e12) throw new Error('INVALID_LOAN_PAYLOAD');
      loan!.principal += tranche.principal;
      const tranches = data.disbursements.filter((t) => t.loanId === loanId), dates = tranches.map((t) => t.date).sort();
      const maturity = tranches.every((t) => t.maturity) ? tranches.map((t) => t.maturity!).sort().at(-1)! : '';
      requests.push(cells(snapshot.ids['Khoản vay'], loanIndex, 2, [loan!.principal]));
      requests.push(cells(snapshot.ids['Khoản vay'], loanIndex, 4, [serial(dates[0]), maturity ? serial(maturity) : '']));
    }
  } else if (command.operation === 'UPDATE_INFO') {
    requests.push(cells(snapshot.ids['Khoản vay'], loanIndex, 1, [command.borrowerName]));
    requests.push(cells(snapshot.ids['Khoản vay'], loanIndex, 14, [command.note]));
  } else {
    const type = command.operation === 'PRINCIPAL_PAYMENT' ? 'Thu gốc' : 'Thu lãi';
    data.payments.push({ row: snapshot.interest.payments.length + 1, loanId, type, amount: Number(command.amount), date: String(command.date) });
    requests.push({ appendCells: { sheetId: snapshot.ids['Giao dịch'], rows: [{ values: [serial(String(command.date)), loanId, type, '', command.amount, command.note].map((v) => ({ userEnteredValue: typeof v === 'number' ? { numberValue: v } : { stringValue: String(v) } })) }], fields: 'userEnteredValue' } });
  }
  const lastEvent = [asOf, ...data.payments.map((p) => p.date), ...data.disbursements.map((t) => t.date)].sort().at(-1)!;
  buildInterest(data, lastEvent); // Includes validation of backdated overpayments.
  return { requests, loanId };
}

export async function writeLoan(request: ManualRequest, env: Environment, factory: (config: GoogleConfiguration) => Promise<LoanWritePort>, now = new Date()) {
  const fail = (httpStatus: number, error: string) => ({ httpStatus, body: { error } as Record<string, unknown> });
  if (request.method !== 'POST') return fail(405, 'METHOD_NOT_ALLOWED');
  const config = readGoogleConfiguration({ ...env, SPREADSHEET_ID: env.INTEREST_SPREADSHEET_ID });
  if (!config || !validWebhookSecret(env.WEBHOOK_SECRET ?? '')) return fail(503, 'INTEREST_CONFIGURATION_REQUIRED');
  const secret = request.headers?.['x-webhook-secret'];
  if (typeof secret !== 'string' || Buffer.byteLength(secret) !== Buffer.byteLength(env.WEBHOOK_SECRET!) || !timingSafeEqual(Buffer.from(secret), Buffer.from(env.WEBHOOK_SECRET!))) return fail(401, 'UNAUTHORIZED');
  if (env.LOAN_WRITES_ENABLED !== '1') return fail(503, 'LOAN_WRITES_DISABLED');
  if (typeof request.headers?.['content-type'] !== 'string' || !/^application\/json(?:\s*;|$)/i.test(request.headers['content-type'])) return fail(415, 'UNSUPPORTED_MEDIA_TYPE');
  let command: ReturnType<typeof loanCommand>;
  try { command = loanCommand(request.body); } catch { return fail(400, 'INVALID_LOAN_PAYLOAD'); }
  try {
    const port = await factory(config), plan = planLoanCommand(await port.read(), command, now);
    if (!plan.requests.length) return { httpStatus: 200, body: { status: 'DUPLICATE_IGNORED', loanId: plan.loanId } };
    try { await port.write(plan.requests); }
    catch {
      const retried = planLoanCommand(await port.read(), command, now);
      if (retried.requests.length) return fail(503, 'LOAN_RETRY_REQUIRED');
      return { httpStatus: 200, body: { status: 'DUPLICATE_IGNORED', loanId: plan.loanId } };
    }
    return { httpStatus: 200, body: { status: 'SAVED_LOAN_EVENT', loanId: plan.loanId } };
  } catch (error) {
    const code = error instanceof Error ? error.message : '';
    if (code === 'IDEMPOTENCY_CONFLICT') return fail(409, code);
    if (['INTEREST_OVERPAYMENT', 'LOAN_NOT_FOUND', 'INVALID_LOAN_PAYLOAD'].includes(code)) return fail(400, code);
    return fail(503, ['LOAN_AUDIT_MIGRATION_REQUIRED', 'LOAN_SIMULATION_ACTIVE', 'LOAN_SOURCE_FULL', 'LOAN_LOCALE_UNSUPPORTED'].includes(code) ? code : 'LOAN_WRITE_UNAVAILABLE');
  }
}

export async function makeLoanWritePort(config: GoogleConfiguration): Promise<LoanWritePort> {
  const { makeGoogleInterestReadPort } = await import('./google-interest-read.js');
  const reader = await makeGoogleInterestReadPort(config);
  const { google } = await import('googleapis');
  const auth = new google.auth.GoogleAuth({ credentials: { client_email: config.clientEmail, private_key: config.privateKey }, scopes: ['https://www.googleapis.com/auth/spreadsheets'] });
  const sheets = google.sheets({ version: 'v4', auth });
  return {
    async read() {
      const metadata = await sheets.spreadsheets.get({ spreadsheetId: config.spreadsheetId, fields: 'properties(locale),sheets(properties(sheetId,title,gridProperties(rowCount,columnCount)))' });
      const ids: Record<string, number> = {};
      let auditCount = 0;
      for (const sheet of metadata.data.sheets ?? []) {
        if (sheet.properties?.sheetId != null && sheet.properties.title) ids[sheet.properties.title] = sheet.properties.sheetId;
        if (sheet.properties?.title === 'Finance_App_Audit') {
          if ((sheet.properties.gridProperties?.columnCount ?? 0) < 8) throw new Error('LOAN_AUDIT_MIGRATION_REQUIRED');
          auditCount = sheet.properties.gridProperties?.rowCount ?? 0;
        }
        if (['Khoản vay', 'Đợt giải ngân'].includes(sheet.properties?.title ?? '') && sheet.properties?.gridProperties?.rowCount !== 300) throw new Error('LOAN_AUDIT_MIGRATION_REQUIRED');
      }
      if (!auditCount || auditCount > 50001) throw new Error('LOAN_AUDIT_MIGRATION_REQUIRED');
      const readAudit = async () => (await sheets.spreadsheets.values.get({ spreadsheetId: config.spreadsheetId, range: `Finance_App_Audit!A1:H${auditCount}`, valueRenderOption: 'UNFORMATTED_VALUE' })).data.values ?? [];
      const before = await readAudit(), interest = await reader.readInterest(), after = await readAudit();
      if (JSON.stringify(before) !== JSON.stringify(after)) throw new Error('LOAN_RETRY_REQUIRED');
      return { interest, audit: after, ids, locale: metadata.data.properties?.locale ?? '' };
    },
    async write(requests) { await sheets.spreadsheets.batchUpdate({ spreadsheetId: config.spreadsheetId, requestBody: { requests } }); },
  };
}
