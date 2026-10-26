import type { GoogleConfiguration } from './configuration.js';
import type { FinanceReadPort } from './unified-transactions.js';

export function financeReadRanges(tabs: { title: string; rows: number; columns: number; type: string }[]): string[] {
  return [{ title: 'Transactions', width: 9, last: 'L' }, { title: 'MoneyLover_History', width: 15, last: 'P' }].map((target) => {
    const tab = tabs.find((t) => t.title === target.title);
    if (!tab || tab.type !== 'GRID' || !Number.isSafeInteger(tab.rows) || tab.rows < 1 || tab.columns < target.width) throw new Error('FINANCE_SCHEMA_MISMATCH');
    // ponytail: bound the grid rather than silently omit rows beyond a fixed read window.
    if (tab.rows > 50001) throw new Error('FINANCE_SOURCE_LIMIT_EXCEEDED');
    const last = target.title === 'Transactions' ? (tab.columns >= 12 ? 'L' : tab.columns === 11 ? 'K' : tab.columns === 10 ? 'J' : 'I')
      : tab.columns > target.width ? target.last : 'O';
    return `'${target.title}'!A1:${last}${tab.rows}`;
  });
}

export async function makeGoogleFinanceReadPort(config: GoogleConfiguration): Promise<FinanceReadPort> {
  const { google } = await import('googleapis');
  const auth = new google.auth.GoogleAuth({ credentials: { client_email: config.clientEmail, private_key: config.privateKey },
    scopes: ['https://www.googleapis.com/auth/spreadsheets.readonly'] });
  const sheets = google.sheets({ version: 'v4', auth });
  return { async readFinance() {
    // Fixed allowlist: Raw_Capture and client-provided ranges are never read.
    const metadata = await sheets.spreadsheets.get({ spreadsheetId: config.spreadsheetId,
      fields: 'sheets(properties(title,sheetType,gridProperties(rowCount,columnCount)))' });
    const ranges = financeReadRanges((metadata.data.sheets ?? []).map(({ properties: p }) => ({
      title: p?.title ?? '', rows: p?.gridProperties?.rowCount ?? 0, columns: p?.gridProperties?.columnCount ?? 0, type: p?.sheetType ?? '',
    })));
    const editSheet = metadata.data.sheets?.find((s) => s.properties?.title === 'Transaction_Edits')?.properties;
    if (editSheet) {
      if (editSheet.sheetType !== 'GRID' || (editSheet.gridProperties?.columnCount ?? 0) < 8 || !editSheet.gridProperties?.rowCount
        || editSheet.gridProperties.rowCount > 50001) throw new Error('FINANCE_SCHEMA_MISMATCH');
      const last = editSheet.gridProperties.columnCount === 8 ? 'H' : 'I';
      ranges.push(`'Transaction_Edits'!A1:${last}${editSheet.gridProperties.rowCount}`);
    }
    const response = await sheets.spreadsheets.values.batchGet({ spreadsheetId: config.spreadsheetId,
      ranges, valueRenderOption: 'UNFORMATTED_VALUE' });
    const [bank, history, edits] = (response.data.valueRanges ?? []).map((range) => range.values ?? []);
    if (!bank || !history) throw new Error('FINANCE_SCHEMA_MISMATCH');
    if (bank.length > 50001 || history.length > 50001) throw new Error('FINANCE_SOURCE_LIMIT_EXCEEDED');
    return { bank, history, ...(editSheet ? { edits } : {}) };
  } };
}
