import { hasExpectedHeaders } from './sheet-schema.js';
import { EDIT_HEADERS } from './edit-schema.js';
import { LOAN_AUDIT_HEADERS } from './loan-audit-schema.js';

export type AuditScope = 'transactions' | 'loans';
export type AuditTable = { id: number; title: string; columns: number; rows: unknown[][]; hasMarkers: boolean };
export type AuditState = { tables: AuditTable[]; existingIds: number[] };
export function auditSchema(scope: AuditScope) {
  if (scope !== 'transactions' && scope !== 'loans') throw new Error('INVALID_SCOPE');
  return scope === 'transactions' ? { title: 'Transaction_Edits', headers: EDIT_HEADERS }
    : { title: 'Finance_App_Audit', headers: LOAN_AUDIT_HEADERS };
}
export function planAuditMigration(state: AuditState, scope: AuditScope, rollback = false): Record<string, unknown>[] {
  const { title, headers } = auditSchema(scope), matches = state.tables.filter((t) => t.title === title);
  if (matches.length > 1) throw new Error('AUDIT_SCHEMA_MISMATCH');
  const table = matches[0];
  if (table) {
    if (table.columns < headers.length || !hasExpectedHeaders(table.rows[0] ?? [], headers)
      || table.rows.some((r) => r.slice(headers.length).some((v) => v !== '' && v != null))) throw new Error('AUDIT_SCHEMA_MISMATCH');
    if (!rollback) return [];
    if (table.hasMarkers || table.rows.slice(1).some((r) => r.some((v) => v !== '' && v != null))) throw new Error('AUDIT_ROLLBACK_HAS_DATA');
    return [{ deleteSheet: { sheetId: table.id } }];
  }
  if (rollback) return [];
  const sheetId = Math.max(0, ...state.existingIds) + 1;
  if (!Number.isSafeInteger(sheetId) || sheetId > 2147483647) throw new Error('INVALID_SHEET_ID');
  return [{ addSheet: { properties: { sheetId, title, gridProperties: { rowCount: 1000, columnCount: headers.length + 1, frozenRowCount: 1 } } } },
    { updateCells: { start: { sheetId, rowIndex: 0, columnIndex: 0 }, rows: [{ values: headers.map((stringValue) => ({ userEnteredValue: { stringValue } })) }], fields: 'userEnteredValue' } }];
}

export async function prepareAudit(port: { read: () => Promise<AuditState>; backup: () => Promise<void>; write: (requests: Record<string, unknown>[]) => Promise<void> },
  scope: AuditScope, options: { apply?: boolean; rollback?: boolean } = {}) {
  const before = await port.read(), requests = planAuditMigration(before, scope, options.rollback);
  if (!options.apply || !requests.length) return { status: requests.length ? 'CHANGES_REQUIRED' : 'ALREADY_READY', writes: 0 };
  await port.backup();
  if (JSON.stringify(await port.read()) !== JSON.stringify(before)) throw new Error('AUDIT_SOURCE_CHANGED');
  await port.write(requests);
  const after = await port.read();
  if (planAuditMigration(after, scope, options.rollback).length) throw new Error('AUDIT_READBACK_FAILED');
  return { status: options.rollback ? 'ROLLED_BACK' : 'PREPARED', writes: 1 };
}
