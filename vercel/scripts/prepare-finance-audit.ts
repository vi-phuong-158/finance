import { writeFile } from 'node:fs/promises';
import { resolve, relative, isAbsolute } from 'node:path';
import { readGoogleConfiguration } from '../lib/configuration.js';
import { auditSchema, prepareAudit } from '../lib/audit-migration.js';
import type { AuditScope, AuditState } from '../lib/audit-migration.js';

async function main() {
  const args = process.argv.slice(2), values: Record<string, string> = {};
  for (let i = 0; i < args.length; i++) {
    const key = args[i];
    if (key === '--apply' || key === '--rollback') { values[key] = '1'; continue; }
    if (!['--scope', '--backup'].includes(key) || !args[i + 1] || args[i + 1].startsWith('--') || values[key]) throw Error('INVALID_ARGUMENT');
    values[key] = args[++i];
  }
  const scope = values['--scope'] as AuditScope, { title, headers } = auditSchema(scope), apply = values['--apply'] === '1';
  const backupPath = values['--backup'] ? resolve(values['--backup']) : null;
  if (apply && !backupPath) throw Error('BACKUP_REQUIRED');
  if (backupPath) {
    const location = relative(resolve(import.meta.dirname, '../..'), backupPath);
    if (!location.startsWith('../') && !location.startsWith('..\\') && !isAbsolute(location)) throw Error('BACKUP_MUST_BE_OUTSIDE_REPOSITORY');
  }
  const config = readGoogleConfiguration({ ...process.env, SPREADSHEET_ID: scope === 'loans' ? process.env.INTEREST_SPREADSHEET_ID : process.env.SPREADSHEET_ID });
  if (!config) throw Error('SERVER_CONFIGURATION_ERROR');
  const { google } = await import('googleapis');
  const auth = new google.auth.GoogleAuth({ credentials: { client_email: config.clientEmail, private_key: config.privateKey },
    scopes: [apply ? 'https://www.googleapis.com/auth/spreadsheets' : 'https://www.googleapis.com/auth/spreadsheets.readonly'] });
  const sheets = google.sheets({ version: 'v4', auth });
  const port = {
    async read(): Promise<AuditState> {
      const meta = (await sheets.spreadsheets.get({ spreadsheetId: config.spreadsheetId, fields: 'sheets(properties(sheetId,title,sheetType,gridProperties(rowCount,columnCount))),namedRanges(range(sheetId))' })).data;
      const ids = (meta.sheets ?? []).map((s) => s.properties?.sheetId).filter((id): id is number => id != null);
      const properties = meta.sheets?.find((s) => s.properties?.title === title)?.properties;
      if (!properties) return { tables: [], existingIds: ids };
      const rows = properties.gridProperties?.rowCount, columns = properties.gridProperties?.columnCount;
      if (properties.sheetType !== 'GRID' || properties.sheetId == null || !rows || rows > 50001 || !columns || columns < headers.length) throw Error('AUDIT_SCHEMA_MISMATCH');
      const lastColumn = String.fromCharCode(64 + Math.min(columns, headers.length + 1));
      const data = await sheets.spreadsheets.values.get({ spreadsheetId: config.spreadsheetId, range: `'${title}'!A1:${lastColumn}${rows}`, valueRenderOption: 'UNFORMATTED_VALUE' });
      return { tables: [{ id: properties.sheetId, title, columns, rows: data.data.values ?? [], hasMarkers: !!meta.namedRanges?.some((n) => n.range?.sheetId === properties.sheetId) }], existingIds: ids };
    },
    async backup() {
      if (!backupPath) throw Error('BACKUP_REQUIRED');
      // Owner-operated full cell/formula/format/metadata snapshot. Never print or commit it.
      const snapshot = (await sheets.spreadsheets.get({ spreadsheetId: config.spreadsheetId, includeGridData: true })).data;
      await writeFile(backupPath, JSON.stringify({ format: 'FINANCE_FULL_SHEETS_BACKUP_V1', createdAt: new Date().toISOString(), spreadsheet: snapshot }), { flag: 'wx', mode: 0o600 });
    },
    async write(requests: Record<string, unknown>[]) { await sheets.spreadsheets.batchUpdate({ spreadsheetId: config.spreadsheetId, requestBody: { requests } }); },
  };
  console.log(JSON.stringify(await prepareAudit(port, scope, { apply, rollback: values['--rollback'] === '1' })));
}
main().catch(() => { console.error('AUDIT_PREPARATION_BLOCKED: verify scope, configuration, backup path and existing audit data.'); process.exitCode = 1; });
