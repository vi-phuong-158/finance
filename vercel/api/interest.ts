import { handleInterestRead } from '../lib/interest-read-service.js';
import { logReadTiming } from '../lib/google-read-client.js';
import type { ReadRequest, ReadResponse } from '../lib/finance-read-service.js';
export default async function handler(request: ReadRequest, response: ReadResponse) {
  const started = performance.now();
  try { return await handleInterestRead(request, response); }
  finally { logReadTiming('total', started); }
}
