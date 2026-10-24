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
    async appendTransaction(row: unknown[]) {
      await sheets.spreadsheets.values.append({
        spreadsheetId,
        range: 'Transactions!A:I',
        valueInputOption: 'RAW',
        requestBody: { values: [row] },
      });
    },
    async appendRaw(row: unknown[]) {
      await sheets.spreadsheets.values.append({
        spreadsheetId,
        range: 'Raw_Capture!A:D',
        valueInputOption: 'RAW',
        requestBody: { values: [row] },
      });
    },
  };
}
