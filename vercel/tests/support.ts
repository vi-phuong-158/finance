import { generateKeyPairSync } from 'node:crypto';

// Ephemeral test-only RSA material. No usable account credentials are committed.
const privateKey = generateKeyPairSync('rsa', { modulusLength: 2048 }).privateKey
  .export({ type: 'pkcs8', format: 'pem' }).toString();
export const SYNTHETIC_SECRET = 'a'.repeat(64);
export function testEnvironment(overrides: Record<string, string | undefined> = {}) {
  return {
    SPREADSHEET_ID: 'synthetic-spreadsheet-id-for-tests',
    WEBHOOK_SECRET: SYNTHETIC_SECRET,
    GOOGLE_CLIENT_EMAIL: 'fixture@finance-fixture.iam.gserviceaccount.com',
    GOOGLE_PRIVATE_KEY: privateKey,
    ...overrides,
  };
}
