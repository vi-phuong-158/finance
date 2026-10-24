import { createPrivateKey } from 'node:crypto';

export type Environment = Record<string, string | undefined>;
export type GoogleConfiguration = { spreadsheetId: string; clientEmail: string; privateKey: string };
const PLACEHOLDER = /replace|placeholder|change-?me|default|your[-_]|example|test[-_]|sample|mat-khau/i;

export function validWebhookSecret(value: string): boolean {
  return /^[A-Za-z0-9_-]{32,128}$/.test(value) && !PLACEHOLDER.test(value);
}

export function readGoogleConfiguration(env: Environment): GoogleConfiguration | null {
  const spreadsheetId = env.SPREADSHEET_ID ?? '';
  const clientEmail = env.GOOGLE_CLIENT_EMAIL ?? '';
  const privateKey = (env.GOOGLE_PRIVATE_KEY ?? '').replace(/\\n/g, '\n');
  if (!/^[A-Za-z0-9_-]{20,}$/.test(spreadsheetId) || PLACEHOLDER.test(spreadsheetId)
    || !/^[a-z0-9._-]+@[a-z0-9-]+\.iam\.gserviceaccount\.com$/i.test(clientEmail)) return null;
  try {
    const key = createPrivateKey(privateKey);
    if (key.asymmetricKeyType !== 'rsa' || (key.asymmetricKeyDetails?.modulusLength ?? 0) < 2048) return null;
  } catch { return null; }
  return { spreadsheetId, clientEmail, privateKey };
}

export function configurationIsValid(env: Environment): boolean {
  return validWebhookSecret(env.WEBHOOK_SECRET ?? '') && readGoogleConfiguration(env) !== null;
}

export function validWebhookUrl(value: string): boolean {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && Boolean(url.hostname) && !url.username && !url.password
      && !url.search && !url.hash && url.pathname === '/api/transaction';
  } catch { return false; }
}
