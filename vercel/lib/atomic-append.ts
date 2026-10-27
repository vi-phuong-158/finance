// Google batchUpdate is atomic; AddNamedRange rejects an already-existing namedRangeId.
// Keep the marker and the literal row in ONE batch. Never delete markers on a timer.
// https://developers.google.com/workspace/sheets/api/guides/batch
// https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets/request#AddNamedRangeRequest
type Cell = { userEnteredValue: { boolValue?: boolean; numberValue?: number; stringValue?: string } };
export type AppendRequest = {
  addNamedRange?: { namedRange: { namedRangeId: string; name: string; range: { sheetId: number; startRowIndex: number; endRowIndex: number; startColumnIndex: number; endColumnIndex: number } } };
  appendCells?: { sheetId: number; rows: { values: Cell[] }[]; fields: string };
};

// `extra` rides in the same batch, so it exists exactly when the guarded row does (used for Transaction_Identity).
export function atomicAppendRequests(sheetId: number, eventId: string, row: unknown[], extra?: { sheetId: number; row: unknown[] }): AppendRequest[] {
  if (!Number.isSafeInteger(sheetId) || sheetId < 0 || !/^[a-f0-9]{64}$/.test(eventId)) throw new Error('INVALID_APPEND');
  if (extra && (!Number.isSafeInteger(extra.sheetId) || extra.sheetId < 0 || extra.sheetId === sheetId)) throw new Error('INVALID_APPEND');
  const appendCells = (target: number, cells: unknown[]): AppendRequest => ({ appendCells: { sheetId: target, rows: [{ values: cells.map((value): Cell => {
    if (typeof value === 'boolean') return { userEnteredValue: { boolValue: value } };
    if (typeof value === 'number' && Number.isFinite(value)) return { userEnteredValue: { numberValue: value } };
    if (typeof value === 'string') return { userEnteredValue: { stringValue: value } };
    throw new Error('INVALID_APPEND');
  }) }], fields: 'userEnteredValue' } });
  return [
    { addNamedRange: { namedRange: { namedRangeId: `finance_${eventId}`, name: `_finance_event_${eventId}`,
      range: { sheetId, startRowIndex: 0, endRowIndex: 1, startColumnIndex: 0, endColumnIndex: 1 } } } },
    appendCells(sheetId, row),
    ...(extra ? [appendCells(extra.sheetId, extra.row)] : []),
  ];
}

export async function guardedAppend(write: () => Promise<unknown>, rowIsPresent: () => Promise<boolean>): Promise<'APPENDED' | 'DUPLICATE'> {
  try { await write(); return 'APPENDED'; }
  catch (error) {
    // Includes a lost response after commit and a concurrent winner. A marker alone is
    // insufficient proof: a matching financial row MUST exist before acknowledging.
    if (await rowIsPresent()) return 'DUPLICATE';
    throw error;
  }
}
