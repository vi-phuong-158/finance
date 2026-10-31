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

export type OwnAccount = { digest: string; label: string };
export type OwnAccounts = Partial<Record<'VCB' | 'BIDV' | 'VIETINBANK', OwnAccount>>;

export function readOwnAccounts(env: Environment): OwnAccounts {
  const raw = env.OWN_ACCOUNT_DIGESTS;
  if (!raw || typeof raw !== 'string') return {};
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    const result: OwnAccounts = {};
    for (const [bank, config] of Object.entries(parsed)) {
      if (!['VCB', 'BIDV', 'VIETINBANK'].includes(bank)) continue;
      if (!config || typeof config !== 'object') continue;
      const { digest, label } = config as Record<string, unknown>;
      if (typeof digest === 'string' && /^[a-f0-9]{32}$/.test(digest)
        && typeof label === 'string' && label.trim().length > 0 && label.length <= 120 && !/[\t\r\n|]/.test(label)) {
        result[bank as 'VCB' | 'BIDV' | 'VIETINBANK'] = { digest, label: label.trim() };
      }
    }
    return result;
  } catch {
    return {};
  }
}
