import { writeFile } from 'node:fs/promises';
import { resolve, relative, isAbsolute } from 'node:path';
import { readGoogleConfiguration } from '../lib/configuration.js';
import { migrateTransactions } from '../lib/transactions-migration.js';

// Owner-operated only. Dry-run by default. Do not run --apply against a live ledger without a maintenance window.
async function main() {
  const args = process.argv.slice(2);
  const known = new Set(['--apply', '--rollback', '--backup']);
  for (let i = 0; i < args.length; i++) {
    if (!known.has(args[i])) throw new Error('INVALID_ARGUMENT');
    if (args[i] === '--backup') { if (!args[++i] || args[i].startsWith('--')) throw new Error('INVALID_ARGUMENT'); }
  }
  const apply = args.includes('--apply');
  const backupArg = args.indexOf('--backup');
  const backupPath = backupArg >= 0 ? resolve(args[backupArg + 1]) : null;
  const repository = resolve(import.meta.dirname, '../..');
  const location = backupPath ? relative(repository, backupPath) : '';
  if (backupPath && location !== '..' && !location.startsWith('..\\') && !location.startsWith('../') && !isAbsolute(location)) throw new Error('BACKUP_MUST_BE_OUTSIDE_REPOSITORY');
  if (apply && !backupPath) throw new Error('BACKUP_REQUIRED');
  const config = readGoogleConfiguration(process.env);
  if (!config) throw new Error('SERVER_CONFIGURATION_ERROR');
  const { google } = await import('googleapis');
  const auth = new google.auth.GoogleAuth({ credentials: { client_email: config.clientEmail, private_key: config.privateKey },
    scopes: [apply ? 'https://www.googleapis.com/auth/spreadsheets' : 'https://www.googleapis.com/auth/spreadsheets.readonly'] });
  const sheets = google.sheets({ version: 'v4', auth });
  const port = {
    async read() {
      const meta = await sheets.spreadsheets.get({ spreadsheetId: config.spreadsheetId, fields: 'sheets(properties(sheetId,title,gridProperties(rowCount,columnCount)))' });
      const p = meta.data.sheets?.find((s) => s.properties?.title === 'Transactions')?.properties;
      const count = p?.gridProperties?.rowCount, columns = p?.gridProperties?.columnCount;
      if (p?.sheetId == null || !count || !columns || columns < 9 || count > 50001) throw new Error('SCHEMA_MISMATCH');
      const last = columns >= 12 ? 'L' : columns === 11 ? 'K' : columns === 10 ? 'J' : 'I';
      const data = await sheets.spreadsheets.values.get({ spreadsheetId: config.spreadsheetId, range: `Transactions!A1:${last}${count}`, valueRenderOption: 'UNFORMATTED_VALUE' });
      return { sheetId: p.sheetId, columns, rows: data.data.values ?? [] };
    },
    async write(requests: Record<string, unknown>[]) {
      await sheets.spreadsheets.batchUpdate({ spreadsheetId: config.spreadsheetId, requestBody: { requests } });
    },
  };
  const result = await migrateTransactions(port, { apply, rollback: args.includes('--rollback'),
    backup: backupPath ? async (table) => { await writeFile(backupPath, JSON.stringify({ format: 'FINANCE_TRANSACTIONS_BACKUP_V1', createdAt: new Date().toISOString(), table }), { flag: 'wx', mode: 0o600 }); } : undefined });
  console.log(JSON.stringify(result)); // Counts/status only; never rows, IDs, paths or credentials.
}
main().catch(() => { console.error('MIGRATION_BLOCKED: inspect configuration, backup location, schema and maintenance-window requirements.'); process.exitCode = 1; });
