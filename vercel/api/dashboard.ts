import { logReadTiming } from '../lib/google-read-client.js';
import { handleFinanceRead } from '../lib/finance-read-service.js';
import type { ReadRequest, ReadResponse } from '../lib/finance-read-service.js';
export default async function handler(request: ReadRequest, response: ReadResponse) {
  const started = performance.now();
  try { return await handleFinanceRead(request, response, 'dashboard'); }
  finally { logReadTiming('total', started); }
}
