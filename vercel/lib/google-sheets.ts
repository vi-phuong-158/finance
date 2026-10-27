import { atomicAppendRequests, guardedAppend } from './atomic-append.js';

export async function makeGoogleSheetsPort(config: {
  spreadsheetId: string;
  clientEmail: string;
  privateKey: string;
}) {
  const { google } = await import('googleapis');
  const auth = new google.auth.GoogleAuth({
    credentials: {
      client_email: config.clientEmail,
      private_key: config.privateKey.replace(/\\n/g, '\n'),
    },
    scopes: ['https://www.googleapis.com/auth/spreadsheets'],
  });

  const sheets = google.sheets({ version: 'v4', auth });
  const spreadsheetId = config.spreadsheetId;
  async function appendOnce(title: string, row: unknown[], idIndex: number, identity?: unknown[] | null) {
    const eventId = String(row[idIndex]);
    const meta = await sheets.spreadsheets.get({ spreadsheetId, fields: 'sheets(properties(sheetId,title))' });
    const id = (name: string) => meta.data.sheets?.find((s) => s.properties?.title === name)?.properties?.sheetId;
    const sheetId = id(title);
    if (sheetId == null) throw new Error('SHEET_NOT_FOUND');
    // Without a prepared Transaction_Identity tab the transaction is still saved; only Tier 1 memory is skipped.
    const identitySheetId = identity ? id('Transaction_Identity') : null;
    const extra = identity && identitySheetId != null ? { sheetId: identitySheetId, row: identity } : undefined;
    return guardedAppend(() => sheets.spreadsheets.batchUpdate({ spreadsheetId,
      requestBody: { requests: atomicAppendRequests(sheetId, eventId, row, extra) } }), async () => {
      const range = title === 'Transactions' ? 'Transactions!I:I' : 'Raw_Capture!D:D';
      const ids = await sheets.spreadsheets.values.get({ spreadsheetId, range, majorDimension: 'COLUMNS' });
      return (ids.data.values?.[0] ?? []).includes(eventId);
    });
  }

  return {
    async readHeaders() {
      const response = await sheets.spreadsheets.values.batchGet({
        spreadsheetId,
        ranges: ['Transactions!1:1', 'Raw_Capture!1:1'],
      });

      return {
        transaction: response.data.valueRanges?.[0]?.values?.[0] ?? [],
        raw: response.data.valueRanges?.[1]?.values?.[0] ?? [],
      };
    },
    async readEventIds() {
      const response = await sheets.spreadsheets.values.batchGet({
        spreadsheetId,
        ranges: ['Transactions!I:I', 'Raw_Capture!D:D'],
        majorDimension: 'COLUMNS',
      });

      return {
        transactionIds: response.data.valueRanges?.[0]?.values?.[0] ?? [],
        rawIds: response.data.valueRanges?.[1]?.values?.[0] ?? [],
      };
    },
    async appendTransaction(row: unknown[], identity?: unknown[] | null) {
      return appendOnce('Transactions', row, 8, identity);
    },
    async appendRaw(row: unknown[]) {
      return appendOnce('Raw_Capture', row, 3);
    },
  };
}
