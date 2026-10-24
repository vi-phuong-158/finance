import { configurationIsValid } from '../lib/configuration.js';

export default function handler(
  request: { method?: string },
  response: { setHeader: (name: string, value: string) => unknown; status: (code: number) => { json: (body: unknown) => unknown } },
) {
  response.setHeader('Cache-Control', 'no-store');
  if (request.method !== 'GET') return response.status(405).json({ error: 'METHOD_NOT_ALLOWED' });
  const environmentReady = configurationIsValid(process.env);
  return response.status(environmentReady ? 200 : 503).json({
    alive: true, version: '3.1.0', environmentReady,
  });
}
