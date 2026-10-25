import type { GoogleConfiguration } from '../configuration.js';
import type { ImportPort } from './importer.js';
import { HISTORY_HEADERS, HISTORY_TAB } from './schema.js';

export function historyAppendRequests(rows: unknown[][], sheetId: number, create: boolean) {
  const values = (create ? [HISTORY_HEADERS, ...rows] : rows).map((row) => ({ values: row.map((v) => ({
    userEnteredValue: typeof v === 'number' ? { numberValue: v } : typeof v === 'boolean' ? { boolValue: v } : { stringValue: String(v ?? '') },
  })) }));
  return [
    ...(create ? [{ addSheet: { properties: { sheetId, title: HISTORY_TAB,
      gridProperties: { rowCount: Math.max(2000, rows.length + 1), columnCount: HISTORY_HEADERS.length, frozenRowCount: 1 } } } }] : []),
    { appendCells: { sheetId, rows: values, fields: 'userEnteredValue' } },
  ];
}

export async function makeHistoryImportPort(config: GoogleConfiguration): Promise<ImportPort> {
  const { google } = await import('googleapis');
  const auth = new google.auth.GoogleAuth({ credentials: { client_email: config.clientEmail, private_key: config.privateKey },
    scopes: ['https://www.googleapis.com/auth/spreadsheets'] });
  const sheets = google.sheets({ version: 'v4', auth }), spreadsheetId = config.spreadsheetId;
  let sheetId: number | undefined;
  let availableSheetId = 1;
  return {
    async readHistory() {
      const meta = await sheets.spreadsheets.get({ spreadsheetId,
        fields: 'sheets(properties(sheetId,title,sheetType,gridProperties(rowCount,columnCount)))' });
      const usedIds = new Set(meta.data.sheets?.map((s) => s.properties?.sheetId));
      while (usedIds.has(availableSheetId)) availableSheetId++;
      const tab = meta.data.sheets?.find((s) => s.properties?.title === HISTORY_TAB)?.properties;
      if (!tab) return { exists: false, headers: [], rows: [] };
      if (tab.sheetType !== 'GRID' || tab.sheetId == null) throw new Error('HISTORY_SCHEMA_MISMATCH');
      const gridRows = tab.gridProperties?.rowCount ?? 0, gridColumns = tab.gridProperties?.columnCount ?? 0;
      if (gridRows < 1 || gridRows > 50001 || gridColumns < HISTORY_HEADERS.length) throw new Error('HISTORY_SCHEMA_MISMATCH');
      sheetId = tab.sheetId;
      const data = await sheets.spreadsheets.values.get({ spreadsheetId,
        range: `'${HISTORY_TAB}'!A1:${gridColumns > HISTORY_HEADERS.length ? 'P' : 'O'}${gridRows}`, valueRenderOption: 'UNFORMATTED_VALUE' });
      const rows = data.data.values ?? [];
      if (rows.length > 50001 || rows.some((r) => r.length > HISTORY_HEADERS.length)) throw new Error('HISTORY_SCHEMA_MISMATCH');
      return { exists: true, headers: rows[0] ?? [], rows: rows.slice(1) };
    },
    async appendHistory(rows, create) {
      if (create) sheetId = availableSheetId;
      if (sheetId === undefined) throw new Error('HISTORY_SCHEMA_MISMATCH');
      await sheets.spreadsheets.batchUpdate({ spreadsheetId,
        requestBody: { requests: historyAppendRequests(rows, sheetId, create) } }, { retry: false });
    },
    async replaceHistory(rows) {
      if (sheetId === undefined) throw new Error('HISTORY_SCHEMA_MISMATCH');
      await sheets.spreadsheets.values.clear({
        spreadsheetId,
        range: `'${HISTORY_TAB}'!A2:P50001`,
      });
      const values = rows.map((row) => ({
        values: row.map((v) => ({
          userEnteredValue: typeof v === 'number' ? { numberValue: v }
            : typeof v === 'boolean' ? { boolValue: v }
            : { stringValue: String(v ?? '') },
        })),
      }));
      await sheets.spreadsheets.batchUpdate({
        spreadsheetId,
        requestBody: {
          requests: [
            { appendCells: { sheetId, rows: values, fields: 'userEnteredValue' } },
          ],
        },
      }, { retry: false });
    },
  };
}
