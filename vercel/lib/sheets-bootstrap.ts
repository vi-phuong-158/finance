import { hasExpectedHeaders, hasTransactionHeaders, SHEET_SCHEMAS } from './sheet-schema.js';

export type SheetSnapshot = { id: number; title: string; type: string; rows: unknown[][] };
export type BootstrapRequest =
  | { addSheet: { properties: { sheetId: number; title: string } } }
  | { updateCells: { start: { sheetId: number; rowIndex: number; columnIndex: number }; rows: { values: { userEnteredValue: { stringValue: string } }[] }[]; fields: string } };
export type BootstrapPort = {
  readSnapshot: () => Promise<SheetSnapshot[]>;
  apply: (requests: BootstrapRequest[]) => Promise<void>;
};

export function planSheetsBootstrap(sheets: SheetSnapshot[]): BootstrapRequest[] {
  const requests: BootstrapRequest[] = [];
  let nextId = Math.max(0, ...sheets.map((sheet) => sheet.id)) + 1;
  const nonempty = (cell: unknown) => cell !== '' && cell !== null && cell !== undefined;
  for (const [title, headers] of Object.entries(SHEET_SCHEMAS)) {
    const matches = sheets.filter((sheet) => sheet.title === title);
    if (matches.length > 1) throw new Error('SHEET_SCHEMA_MISMATCH');
    const sheet = matches[0];
    if (sheet && (sheet.type !== 'GRID' || !Number.isInteger(sheet.id))) throw new Error('SHEET_SCHEMA_MISMATCH');
    const empty = !sheet || !sheet.rows.some((row) => row.some(nonempty));
    if (!empty) {
      const actual = sheet.rows[0] ?? [];
      if (!(title === 'Transactions' ? hasTransactionHeaders(actual) : hasExpectedHeaders(actual, headers))
        || sheet.rows.some((row) => row.slice(actual.length).some(nonempty))) throw new Error('SHEET_SCHEMA_MISMATCH');
      continue;
    }
    const sheetId = sheet?.id ?? nextId++;
    if (!sheet) requests.push({ addSheet: { properties: { sheetId, title } } });
    requests.push({ updateCells: {
      start: { sheetId, rowIndex: 0, columnIndex: 0 },
      rows: [{ values: headers.map((header) => ({ userEnteredValue: { stringValue: header } })) }],
      fields: 'userEnteredValue',
    } });
  }
  return requests;
}

export async function bootstrapSheets(port: BootstrapPort, apply: boolean) {
  // Preflight every target before any write; never repair a populated table.
  const requests = planSheetsBootstrap(await port.readSnapshot());
  if (apply && requests.length) await port.apply(requests);
  return { status: requests.length ? (apply ? 'BOOTSTRAPPED' : 'CHANGES_REQUIRED') : 'ALREADY_READY', operations: requests.length };
}
