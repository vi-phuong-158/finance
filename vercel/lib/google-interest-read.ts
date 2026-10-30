import { getGoogleSheetsClient, logReadTiming } from './google-read-client.js';
import type { GoogleConfiguration } from './configuration.js';
import type { InterestReadPort } from './interest-read-service.js';

export function interestReadRanges(tabs: { title: string; rows: number; columns: number; type: string }[]) {
  return [{ title: 'Khoản vay', width: 20, last: 'T', max: 300 },
    { title: 'Giao dịch', width: 6, last: 'F', max: 50001 },
    { title: 'Đợt giải ngân', width: 12, last: 'L', max: 300 },
    { title: 'Cấu hình', width: 2, last: 'B', max: 100 }].map((target) => {
    const tab = tabs.find((t) => t.title === target.title);
    if (!tab || tab.type !== 'GRID' || !Number.isSafeInteger(tab.rows) || tab.rows < 1 || tab.columns < target.width) throw new Error('INTEREST_SCHEMA_MISMATCH');
    if (tab.rows > target.max) throw new Error('INTEREST_SOURCE_LIMIT_EXCEEDED');
    return `'${target.title}'!A1:${target.last}${tab.rows}`;
  });
}
export async function makeGoogleInterestReadPort(config: GoogleConfiguration): Promise<InterestReadPort> {
  const authStarted = performance.now();
  const sheets = await getGoogleSheetsClient(config, 'readonly');
  logReadTiming('client', authStarted);
  return { async readInterest() {
    // GoogleAuth acquires OAuth tokens lazily: the first metadata call includes token acquisition.
    const metadataStarted = performance.now();
    const metadata = await sheets.spreadsheets.get({ spreadsheetId: config.spreadsheetId,
      fields: 'sheets(properties(title,sheetType,gridProperties(rowCount,columnCount)))' });
    logReadTiming('metadata', metadataStarted);
    const processingStarted = performance.now();
    const ranges = interestReadRanges((metadata.data.sheets ?? []).map(({ properties: p }) => ({
      title: p?.title ?? '', rows: p?.gridProperties?.rowCount ?? 0, columns: p?.gridProperties?.columnCount ?? 0, type: p?.sheetType ?? '',
    })));
    logReadTiming('processing', processingStarted);
    const batchStarted = performance.now();
    const result = await sheets.spreadsheets.values.batchGet({ spreadsheetId: config.spreadsheetId, ranges,
      valueRenderOption: 'UNFORMATTED_VALUE', dateTimeRenderOption: 'SERIAL_NUMBER' });
    logReadTiming('batchGet', batchStarted);
    const [loans, payments, disbursements, configRows] = (result.data.valueRanges ?? []).map((r) => r.values ?? []);
    if (!loans || !payments || !disbursements || !configRows) throw new Error('INTEREST_SCHEMA_MISMATCH');
    return { loans, payments, disbursements, config: configRows };
  } };
}
