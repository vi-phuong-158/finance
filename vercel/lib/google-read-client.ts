import { createHash } from 'node:crypto';
import type { GoogleConfiguration } from './configuration.js';

type Scope = 'readonly' | 'write';

// Include a fingerprint of the key so credential rotation cannot reuse an old client.
export function createClientCache<T>(create: (config: GoogleConfiguration, scope: Scope) => Promise<T>) {
  const clients = new Map<string, Promise<T>>();
  return (config: GoogleConfiguration, scope: Scope): Promise<T> => {
    const key = createHash('sha256').update(JSON.stringify([config.clientEmail, config.spreadsheetId, config.privateKey, scope])).digest('hex');
    const existing = clients.get(key);
    if (existing) return existing;
    const pending = create(config, scope);
    clients.set(key, pending);
    pending.catch(() => { if (clients.get(key) === pending) clients.delete(key); });
    return pending;
  };
}

export const getGoogleSheetsClient = createClientCache(async (config, scope) => {
  const { google } = await import('googleapis');
  const auth = new google.auth.GoogleAuth({ credentials: { client_email: config.clientEmail, private_key: config.privateKey },
    scopes: [scope === 'write' ? 'https://www.googleapis.com/auth/spreadsheets' : 'https://www.googleapis.com/auth/spreadsheets.readonly'] });
  return google.sheets({ version: 'v4', auth });
});

export function logReadTiming(step: string, started: number): void {
  if (process.env.FINANCE_READ_TIMING === '1') console.info(`${step} ${Math.round(performance.now() - started)}ms`);
}
