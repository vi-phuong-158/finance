import { makeLoanWritePort, writeLoan } from '../lib/loan-write.js';
import type { ManualRequest } from '../lib/manual-transaction.js';
import type { ReadResponse } from '../lib/finance-read-service.js';
export default async function handler(request: ManualRequest, response: ReadResponse) {
  for (const name of ['Cache-Control', 'CDN-Cache-Control', 'Vercel-CDN-Cache-Control']) response.setHeader(name, 'no-store');
  response.setHeader('Vary', 'X-Webhook-Secret');
  response.setHeader('X-Content-Type-Options', 'nosniff');
  if (request.method !== 'POST') response.setHeader('Allow', 'POST');
  const result = await writeLoan(request, process.env, makeLoanWritePort);
  return response.status(result.httpStatus).json(result.body);
}
