import { timingSafeEqual } from 'node:crypto';
import { readGoogleConfiguration, validWebhookSecret } from './configuration.js';
import type { Environment, GoogleConfiguration } from './configuration.js';
import type { ReadRequest, ReadResponse } from './finance-read-service.js';
import { buildInterest, dateOnly, parseInterestSnapshot } from './interest.js';
import type { InterestSnapshot } from './interest.js';
import { maskSensitiveIdentifiers } from '../sensitive-identifiers.js';

export type InterestReadPort = { readInterest(): Promise<InterestSnapshot> };
export async function readInterest(request: ReadRequest, env: Environment,
  makePort: (config: GoogleConfiguration) => Promise<InterestReadPort>) {
  const fail = (httpStatus: number, error: string) => ({ httpStatus, body: { error } as Record<string, unknown> });
  if (request.method !== 'GET') return fail(405, 'METHOD_NOT_ALLOWED');
  const config = readGoogleConfiguration({ ...env, SPREADSHEET_ID: env.INTEREST_SPREADSHEET_ID });
  if (!config || !validWebhookSecret(env.WEBHOOK_SECRET ?? '')) return fail(503, 'INTEREST_CONFIGURATION_REQUIRED');
  const supplied = request.headers?.['x-webhook-secret'];
  if (typeof supplied !== 'string') return fail(401, 'UNAUTHORIZED');
  const bytes = Buffer.from(supplied), expected = Buffer.from(env.WEBHOOK_SECRET!);
  if (bytes.length !== expected.length || !timingSafeEqual(bytes, expected)) return fail(401, 'UNAUTHORIZED');
  let requestedDate: string | undefined;
  try {
    const query = request.query ?? {};
    if (Object.keys(query).some((key) => key !== 'asOf')) return fail(400, 'INVALID_QUERY');
    if (query.asOf !== undefined) requestedDate = dateOnly(query.asOf);
  } catch { return fail(400, 'INVALID_QUERY'); }
  try {
    const port = await makePort(config);
    const snapshot = await port.readInterest();
    const { data, asOf } = parseInterestSnapshot(snapshot);
    const body = buildInterest(data, requestedDate ?? asOf);
    // Fail closed if Sheet formulas drift from the same monthly engine used by the app.
    if (!requestedDate) for (const loan of body.loans) {
      const row = snapshot.loans.find((r) => r[0] === loan.id)!;
      const fields = [[6, loan.currentInterest], [8, loan.accruedInterest], [9, loan.paidInterest],
        [10, loan.interestDue], [11, loan.repaidPrincipal], [12, loan.outstandingPrincipal],
        [15, loan.collectedThisPeriod], [16, loan.advanceInterest], [17, loan.currentRemaining]];
      if (fields.some(([column, value]) => typeof row[column] !== 'number' || row[column] !== value)) throw new Error('INTEREST_RECONCILIATION_REQUIRED');
    }
    return { httpStatus: 200, body: { ...body, writesEnabled: env.LOAN_WRITES_ENABLED === '1',
      loans: body.loans.map((loan) => ({ ...loan, note: maskSensitiveIdentifiers(String(snapshot.loans.find((r) => r[0] === loan.id)?.[14] ?? '')) })) } as unknown as Record<string, unknown> };
  } catch (error) {
    const code = error instanceof Error ? error.message : '';
    const known = ['INTEREST_SCHEMA_MISMATCH', 'INTEREST_SOURCE_LIMIT_EXCEEDED', 'INTEREST_RULE_MISMATCH',
      'INVALID_INTEREST_DATE', 'INVALID_INTEREST_ROW', 'DUPLICATE_INTEREST_ID', 'INTEREST_PRINCIPAL_MISMATCH',
      'INTEREST_OVERPAYMENT', 'INTEREST_RECONCILIATION_REQUIRED'];
    return fail(503, known.includes(code) ? code : 'INTEREST_READ_UNAVAILABLE');
  }
}

export async function handleInterestRead(request: ReadRequest, response: ReadResponse) {
  for (const name of ['Cache-Control', 'CDN-Cache-Control', 'Vercel-CDN-Cache-Control']) response.setHeader(name, 'no-store');
  response.setHeader('Vary', 'X-Webhook-Secret');
  response.setHeader('X-Content-Type-Options', 'nosniff');
  if (request.method !== 'GET') response.setHeader('Allow', 'GET');
  const { makeGoogleInterestReadPort } = await import('./google-interest-read.js');
  const result = await readInterest(request, process.env, makeGoogleInterestReadPort);
  return response.status(result.httpStatus).json(result.body);
}
