// Google batchUpdate is atomic; AddNamedRange rejects an already-existing namedRangeId.
// Keep the marker and the literal row in ONE batch. Never delete markers on a timer.
// https://developers.google.com/workspace/sheets/api/guides/batch
// https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets/request#AddNamedRangeRequest
export function atomicAppendRequests(sheetId: number, eventId: string, row: unknown[]) {
  if (!Number.isSafeInteger(sheetId) || sheetId < 0 || !/^[a-f0-9]{64}$/.test(eventId)) throw new Error('INVALID_APPEND');
  const values = row.map((value) => {
    if (typeof value === 'boolean') return { userEnteredValue: { boolValue: value } };
    if (typeof value === 'number' && Number.isFinite(value)) return { userEnteredValue: { numberValue: value } };
    if (typeof value === 'string') return { userEnteredValue: { stringValue: value } };
    throw new Error('INVALID_APPEND');
  });
  return [
    { addNamedRange: { namedRange: { namedRangeId: `finance_${eventId}`, name: `_finance_event_${eventId}`,
      range: { sheetId, startRowIndex: 0, endRowIndex: 1, startColumnIndex: 0, endColumnIndex: 1 } } } },
    { appendCells: { sheetId, rows: [{ values }], fields: 'userEnteredValue' } },
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
