import { logReadTiming } from '../lib/google-read-client.js';
import { handleCategoryMemoryRequest, makeCategoryMemoryPort } from '../lib/category-memory-service.js';
import type { MemoryRequest } from '../lib/category-memory-service.js';
import type { ReadResponse } from '../lib/finance-read-service.js';
export default async function handler(request: MemoryRequest, response: ReadResponse) {
  const started = performance.now();
  try {
  for (const name of ['Cache-Control', 'CDN-Cache-Control', 'Vercel-CDN-Cache-Control']) response.setHeader(name, 'no-store');
  response.setHeader('Vary', 'X-Webhook-Secret');
  response.setHeader('X-Content-Type-Options', 'nosniff');
  if (request.method !== 'GET' && request.method !== 'POST') response.setHeader('Allow', 'GET, POST');
  const result = await handleCategoryMemoryRequest(request, process.env, makeCategoryMemoryPort);
  return response.status(result.httpStatus).json(result.body);
  } finally { if (request.method === 'GET') logReadTiming('total', started); }
}
