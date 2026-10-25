import { handleFinanceRead } from '../lib/finance-read-service.js';
import type { ReadRequest, ReadResponse } from '../lib/finance-read-service.js';
export default async function handler(request: ReadRequest, response: ReadResponse) {
  return handleFinanceRead(request, response, 'dashboard');
}
