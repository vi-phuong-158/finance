import { makeManualPort, writeManual } from '../lib/manual-transaction.js';
import type { ManualRequest } from '../lib/manual-transaction.js';
import type { ReadResponse } from '../lib/finance-read-service.js';

export default async function handler(request: ManualRequest, response: ReadResponse) {
  response.setHeader('Cache-Control', 'private, no-store, max-age=0');
  response.setHeader('CDN-Cache-Control', 'no-store');
  response.setHeader('Vercel-CDN-Cache-Control', 'no-store');
  response.setHeader('Vary', 'X-Webhook-Secret');
  response.setHeader('X-Content-Type-Options', 'nosniff');
  if (request.method !== 'POST') response.setHeader('Allow', 'POST');
  const result = await writeManual(request, process.env, makeManualPort);
  return response.status(result.httpStatus).json(result.body);
}
