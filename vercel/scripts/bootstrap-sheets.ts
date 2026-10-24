import { readGoogleConfiguration } from '../lib/configuration.js';
import { bootstrapSheets } from '../lib/sheets-bootstrap.js';
import type { BootstrapPort } from '../lib/sheets-bootstrap.js';
import { SHEET_SCHEMAS } from '../lib/sheet-schema.js';

async function main() {
  const args = process.argv.slice(2);
  if (args.some((arg) => arg !== '--apply')) throw new Error('INVALID_ARGUMENTS');
  const config = readGoogleConfiguration(process.env);
  if (!config) throw new Error('SERVER_CONFIGURATION_ERROR');
  const { google } = await import('googleapis');
  const auth = new google.auth.GoogleAuth({
    credentials: { client_email: config.clientEmail, private_key: config.privateKey },
    scopes: ['https://www.googleapis.com/auth/spreadsheets'],
  });
  const sheets = google.sheets({ version: 'v4', auth });
  const port: BootstrapPort = {
    async readSnapshot() {
      // This also verifies existence and access before planning any writes.
      const metadata = await sheets.spreadsheets.get({ spreadsheetId: config.spreadsheetId, fields: 'sheets(properties(sheetId,title,sheetType))' });
      return Promise.all((metadata.data.sheets ?? []).map(async ({ properties }) => {
        if (properties?.sheetId == null || !properties.title) throw new Error('SHEET_SCHEMA_MISMATCH');
        const title = properties.title;
        const response = Object.hasOwn(SHEET_SCHEMAS, title) && properties.sheetType === 'GRID'
          ? await sheets.spreadsheets.values.get({ spreadsheetId: config.spreadsheetId, range: `'${title}'`, valueRenderOption: 'FORMULA' })
          : undefined;
        return { id: properties.sheetId, title, type: properties.sheetType ?? '', rows: response?.data.values ?? [] };
      }));
    },
    async apply(requests) {
      await sheets.spreadsheets.batchUpdate({ spreadsheetId: config.spreadsheetId, requestBody: { requests } });
    },
  };
  console.log(JSON.stringify(await bootstrapSheets(port, args.includes('--apply'))));
}

main().catch((error: unknown) => {
  const known = ['INVALID_ARGUMENTS', 'SERVER_CONFIGURATION_ERROR', 'SHEET_SCHEMA_MISMATCH'];
  console.error(known.includes((error as Error)?.message) ? (error as Error).message : 'SHEETS_BOOTSTRAP_FAILED');
  process.exitCode = 1;
});
