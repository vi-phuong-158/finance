import { createHash } from 'node:crypto';
import { hasExpectedHeaders, TRANSACTION_HEADERS, TRANSACTION_HEADERS_V2 } from './sheet-schema.js';

export type TransactionTable = { sheetId: number; columns: number; rows: unknown[][] };
export function tableFingerprint(table: TransactionTable) {
  return createHash('sha256').update(JSON.stringify(table)).digest('hex');
}
export function planTransactionsMigration(table: TransactionTable, rollback = false) {
  if (!Number.isSafeInteger(table.sheetId) || table.sheetId < 0 || table.columns < 9) throw new Error('INVALID_TABLE');
  const legacy = hasExpectedHeaders(table.rows[0] ?? [], TRANSACTION_HEADERS);
  const upgraded = hasExpectedHeaders(table.rows[0] ?? [], TRANSACTION_HEADERS_V2);
  if (!legacy && !upgraded) throw new Error('SCHEMA_MISMATCH');
  if (table.rows.slice(1).some((row) => row.length > (upgraded ? 11 : 9))) throw new Error('SCHEMA_MISMATCH');
  if ((rollback && legacy) || (!rollback && upgraded)) return [];
  if (rollback && table.rows.slice(1).some((row) => row.slice(9).some((cell) => cell != null && cell !== ''))) throw new Error('ROLLBACK_HAS_V2_DATA');
  const requests: Record<string, unknown>[] = [];
  if (table.columns < 12) requests.push({ appendDimension: { sheetId: table.sheetId, dimension: 'COLUMNS', length: 12 - table.columns } });
  requests.push({ updateCells: {
    range: { sheetId: table.sheetId, startRowIndex: 0, endRowIndex: 1, startColumnIndex: 9, endColumnIndex: 11 },
    rows: [{ values: rollback ? [{}, {}] : [{ userEnteredValue: { stringValue: 'source' } }, { userEnteredValue: { stringValue: 'account' } }] }],
    fields: 'userEnteredValue',
  } });
  return requests;
}

export async function migrateTransactions(port: { read: () => Promise<TransactionTable>; write: (requests: Record<string, unknown>[]) => Promise<void> },
  options: { apply?: boolean; rollback?: boolean; backup?: (table: TransactionTable) => Promise<void> } = {}) {
  const before = await port.read();
  const requests = planTransactionsMigration(before, options.rollback);
  if (!options.apply || !requests.length) return { status: requests.length ? 'CHANGES_REQUIRED' : 'ALREADY_READY', writes: 0, rows: before.rows.length - 1 };
  if (!options.backup) throw new Error('BACKUP_REQUIRED');
  await options.backup(before);
  if (tableFingerprint(await port.read()) !== tableFingerprint(before)) throw new Error('SOURCE_CHANGED');
  await port.write(requests);
  const after = await port.read();
  if (!hasExpectedHeaders(after.rows[0] ?? [], options.rollback ? TRANSACTION_HEADERS : TRANSACTION_HEADERS_V2)
    || JSON.stringify(after.rows.slice(1).map((row) => row.slice(0, 9))) !== JSON.stringify(before.rows.slice(1).map((row) => row.slice(0, 9)))) throw new Error('RECONCILIATION_FAILED');
  return { status: 'MIGRATED', writes: 1, rows: after.rows.length - 1 };
}
