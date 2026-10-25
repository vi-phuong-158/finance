import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { parseMoneyLover } from '../lib/moneylover/parser.js';
import { HISTORY_HEADERS, historyRow, parseBoolean, sumMoney } from '../lib/moneylover/schema.js';
import { cleanupImportedHistory, importMoneyLover, planCleanup, planImport } from '../lib/moneylover/importer.js';
import type { HistorySnapshot, ImportPort } from '../lib/moneylover/importer.js';
import { readMoneyLoverXlsx } from '../lib/moneylover/xlsx-reader.js';
import { readHistoryRow } from '../lib/moneylover/history-reader.js';
import { historyAppendRequests, historyReplaceRequests, makeHistoryImportPort } from '../lib/moneylover/google-history.js';
import { summarizeSource, reconcileSource } from '../lib/moneylover/report.js';
import { sourceRow, sourceTable } from './moneylover-fixtures.js';
import { testEnvironment } from './support.js';
import { readGoogleConfiguration } from '../lib/configuration.js';

test('Money Lover signed amounts, date-only VND and source fields survive normalization; non-VND is excluded', () => {
  const result = parseMoneyLover(sourceTable(sourceRow(), sourceRow({ 3: 600000, 4: 'JPY' })));
  assert.deepEqual(result.rejected, []);
  assert.equal(result.excluded.length, 1);
  assert.equal(result.excluded[0].reason, 'CURRENCY_NOT_VND');
  assert.equal(result.excluded[0].currency, 'JPY');
  assert.equal(result.transactions.length, 1);
  const [chi] = result.transactions;
  assert.equal(chi.type, 'CHI'); assert.equal(chi.amount, 12500); assert.equal(chi.amountVnd, 12500);
  assert.equal(chi.currency, 'VND');
  assert.equal(chi.date, '2026-02-28'); assert.equal(chi.wallet, 'Wallet A'); assert.equal(chi.category, 'Cafe');
  assert.equal(chi.note, 'Note\nsecond line'); assert.equal(chi.counterparty, 'Merchant A'); assert.equal(chi.event, 'Event A');
  assert.equal(chi.member, 'Member A');
  assert.deepEqual(readHistoryRow(historyRow(chi)), chi);
});
test('Money Lover non-consumption categories (Cho vay, Trả lãi, Vay, Trả nợ, Thu nợ, Đi vay) are excluded', () => {
  const table = sourceTable(
    sourceRow({ 2: 'Cho vay', 3: -30000000 }),
    sourceRow({ 2: 'Trả lãi', 3: -500000 }),
    sourceRow({ 2: 'Vay', 3: 10000000 }),
    sourceRow({ 2: 'Đi vay', 3: 5000000 }),
    sourceRow({ 2: 'Trả nợ', 3: -2000000 }),
    sourceRow({ 2: 'Thu nợ', 3: 1500000 }),
    sourceRow({ 2: 'Lương', 3: 20000000 }),
    sourceRow({ 2: 'Thu nhập khác', 3: 500000 }),
    sourceRow({ 2: 'Tiền chuyển đến', 3: 1000000 }),
  );
  const result = parseMoneyLover(table);
  assert.deepEqual(result.rejected, []);
  assert.equal(result.excluded.length, 6);
  assert.equal(result.excluded.every((e) => e.reason === 'NON_CONSUMPTION_CATEGORY'), true);
  assert.equal(result.transactions.length, 3);
  assert.deepEqual(result.transactions.map((t) => t.category), ['Lương', 'Thu nhập khác', 'Tiền chuyển đến']);
});
test('blank optional fields are preserved and exclusion flags have an explicit allowlist', () => {
  const t = parseMoneyLover(sourceTable(sourceRow({ 6: '', 7: null, 8: undefined, 9: '' }))).transactions[0];
  assert.equal(t.note, ''); assert.equal(t.counterparty, ''); assert.equal(t.event, ''); assert.equal(t.excludedFromReport, false);
  for (const v of [true, 1, 'TRUE', 'Có', '✅', 'x']) assert.equal(parseBoolean(v), true);
  for (const v of [false, 0, 'FALSE', 'Không', '', null, undefined]) assert.equal(parseBoolean(v), false);
  for (const v of ['anything', 'maybe', 2, {}, []]) assert.throws(() => parseBoolean(v), /INVALID_EXCLUDED_FLAG/);
});
test('strict header, amount, currency, date and unmapped-column validation rejects without guessing', () => {
  const cases = [sourceRow({ 3: 0 }), sourceRow({ 3: '-1,000' }), sourceRow({ 3: NaN }), sourceRow({ 3: 0.1 }),
    sourceRow({ 3: Number.MAX_SAFE_INTEGER }), sourceRow({ 4: '' }), sourceRow({ 4: '¥' }),
    sourceRow({ 1: '2026-02-30' }), sourceRow({ 1: '2026-02-28T00:00:00' }), sourceRow({ 9: 'maybe' }), sourceRow({ 11: 'unmapped' })];
  for (const row of cases) assert.equal(parseMoneyLover(sourceTable(row)).rejected.length, 1);
  const table = sourceTable(sourceRow()); table.rows[0].cells[4] = 'Unknown currency';
  assert.throws(() => parseMoneyLover(table), /INVALID_MONEYLOVER_HEADERS/);
  const duplicate = sourceTable(sourceRow()); duplicate.rows[0].cells.push('Ngày');
  assert.throws(() => parseMoneyLover(duplicate), /INVALID_MONEYLOVER_HEADERS/);
});
test('Excel 1900/1904 serial dates and Vietnamese dates are handled without inventing transaction times', () => {
  assert.equal(parseMoneyLover(sourceTable(sourceRow({ 1: 61 }))).transactions[0].date, '1900-03-01');
  assert.equal(parseMoneyLover(sourceTable(sourceRow({ 1: 60 }))).rejected.length, 1);
  assert.equal(parseMoneyLover(sourceTable(sourceRow({ 1: 61.5 }))).rejected.length, 1);
  const table = sourceTable(sourceRow({ 1: 0 })); table.date1904 = true;
  assert.equal(parseMoneyLover(table).transactions[0].date, '1904-01-01');
  assert.equal(parseMoneyLover(sourceTable(sourceRow({ 1: '28/02/2026' }))).transactions[0].date, '2026-02-28');
});
test('deterministic multiset identity survives reordering/renumbering and preserves identical occurrences', () => {
  const a = sourceRow(), b = sourceRow({ 3: -20000 });
  const first = parseMoneyLover(sourceTable(a, a, b)), second = parseMoneyLover(sourceTable(b, sourceRow({ 0: 9 }), a));
  assert.deepEqual(first.transactions.map((t) => t.legacyId).sort(), second.transactions.map((t) => t.legacyId).sort());
  assert.equal(new Set(first.transactions.map((t) => t.legacyId)).size, 3); assert.equal(first.identicalRows, 1);
  const changed = parseMoneyLover(sourceTable(sourceRow({ 6: 'changed' }))).transactions[0];
  assert.notEqual(changed.legacyId, first.transactions[0].legacyId);
});
function memoryPort(): { port: ImportPort; snapshot: HistorySnapshot; writes: number; loseResponse: boolean } {
  const state = { snapshot: { exists: false, headers: [], rows: [] } as HistorySnapshot, writes: 0, loseResponse: false,
    port: {} as ImportPort };
  state.port = {
    readHistory: async () => structuredClone(state.snapshot),
    appendHistory: async (rows, create) => {
      state.writes++; if (create) { state.snapshot.exists = true; state.snapshot.headers = HISTORY_HEADERS; }
      state.snapshot.rows.push(...rows); if (state.loseResponse) throw new Error('private-upstream-details');
    },
    replaceHistory: async (rows) => {
      state.writes++;
      state.snapshot.rows = structuredClone(rows);
    },
  };
  return state;
}
test('Sheets import request writes only the history tab and never evaluates formula-like source text', () => {
  const t = parseMoneyLover(sourceTable(sourceRow({ 6: '=1+1' }))).transactions[0];
  const first = historyAppendRequests([historyRow(t)], 7, true);
  assert.equal(first.length, 2); assert.equal(first[0].addSheet!.properties.title, 'MoneyLover_History');
  const cells = first[1].appendCells!;
  assert.equal(cells.sheetId, 7); assert.equal(cells.rows[1].values[8].userEnteredValue.stringValue, '=1+1');
  assert.equal(cells.rows[1].values[3].userEnteredValue.numberValue, 12500);
  assert.equal(cells.rows[1].values[11].userEnteredValue.boolValue, false);
  assert.equal(JSON.stringify(first).includes('formulaValue'), false);
  const next = historyAppendRequests([historyRow(t)], 7, false);
  assert.equal(next.length, 1); assert.equal(next[0].appendCells!.rows.length, 1);
});
test('historyReplaceRequests writes atomic updateCells request targeting rows from row 2 downward', () => {
  const t = parseMoneyLover(sourceTable(sourceRow({ 6: '=1+1' }))).transactions[0];
  const requests = historyReplaceRequests([historyRow(t)], 42);
  assert.equal(requests.length, 1);
  const update = requests[0].updateCells;
  assert.ok(update);
  assert.equal(update.range.sheetId, 42);
  assert.equal(update.range.startRowIndex, 1);
  assert.equal('endRowIndex' in update.range, false);
  assert.equal(update.fields, 'userEnteredValue');
  assert.equal(update.rows.length, 1);
  assert.equal(update.rows[0].values[8].userEnteredValue.stringValue, '=1+1');
  assert.equal(update.rows[0].values[3].userEnteredValue.numberValue, 12500);
  assert.equal(update.rows[0].values[11].userEnteredValue.boolValue, false);
  assert.equal(JSON.stringify(requests).includes('formulaValue'), false);
});
test('dry-run writes nothing; first apply inserts all source occurrences; second apply skips all', async () => {
  const parsed = parseMoneyLover(sourceTable(sourceRow(), sourceRow())), state = memoryPort();
  assert.equal((await importMoneyLover(parsed, state.port)).pending, 2); assert.equal(state.writes, 0);
  assert.equal((await importMoneyLover(parsed, state.port, true)).inserted, 2);
  const again = await importMoneyLover(parsed, state.port, true);
  assert.equal(again.inserted, 0); assert.equal(again.skipped, 2); assert.equal(state.writes, 1);
});
test('lost append response is reconciled by full readback; unavailable readback never reports success', async () => {
  const parsed = parseMoneyLover(sourceTable(sourceRow())), state = memoryPort(); state.loseResponse = true;
  assert.equal((await importMoneyLover(parsed, state.port, true)).status, 'IMPORTED_RECONCILED_AFTER_ERROR');
  const uncertain = memoryPort();
  uncertain.port.appendHistory = async () => { uncertain.port.readHistory = async () => { throw new Error(); }; throw new Error(); };
  await assert.rejects(importMoneyLover(parsed, uncertain.port, true), /IMPORT_OUTCOME_UNKNOWN_READBACK_REQUIRED/);
});
test('bad source, wrong schema, duplicate IDs and altered historical data fail before writes', async () => {
  const state = memoryPort(), good = parseMoneyLover(sourceTable(sourceRow()));
  await assert.rejects(importMoneyLover(parseMoneyLover(sourceTable(sourceRow({ 9: 'unknown' }))), state.port, true), /SOURCE_ROWS_REJECTED/);
  assert.equal(state.writes, 0);
  assert.throws(() => planImport(good, { exists: true, headers: [], rows: [] }), /HISTORY_SCHEMA_MISMATCH/);
  await importMoneyLover(good, state.port, true);
  state.snapshot.rows[0][8] = 'manually changed';
  assert.throws(() => planImport(good, state.snapshot), /HISTORY_CONTENT_CONFLICT/);
  state.snapshot.rows.push([...state.snapshot.rows[0]]);
  assert.throws(() => planImport(good, state.snapshot), /DUPLICATE_HISTORY_ID/);
});
test('concurrent historical edits block apply and never touch preexisting rows', async () => {
  const state = memoryPort(), parsed = parseMoneyLover(sourceTable(sourceRow())); let reads = 0;
  state.port.readHistory = async () => { reads++; return reads === 1 ? state.snapshot : { exists: true, headers: HISTORY_HEADERS, rows: [] }; };
  await assert.rejects(importMoneyLover(parsed, state.port, true), /HISTORY_CHANGED_RETRY_DRY_RUN/); assert.equal(state.writes, 0);
});
test('reconciliation separates original currency and excluded rows from report totals', () => {
  const parsed = parseMoneyLover(sourceTable(sourceRow(), sourceRow({ 3: 600000, 4: 'JPY' }), sourceRow({ 3: -30000, 9: '✅' })));
  const summary = summarizeSource(parsed);
  assert.equal(summary.totalsByCurrency.VND.expense, 42500); assert.equal(summary.reportVnd.expense, 12500);
  assert.equal(summary.currencies.VND, 2);
  assert.equal(summary.currencies.JPY, undefined);
  assert.equal(summary.excludedCount, 1);
  assert.equal(sumMoney([0.1, 0.2]), 0.3); assert.throws(() => sumMoney([Number.MAX_SAFE_INTEGER]), /INVALID_AMOUNT/);
  assert.throws(() => sumMoney([0.0000001]), /INVALID_AMOUNT/);
  assert.equal(reconcileSource(parsed, { rows: 2, expenseVnd: 42500, categoryExpenseVnd: { Cafe: 42500 } }).matches, true);
  assert.equal(reconcileSource(parsed, { expenseVnd: 1 }).matches, false);
  assert.throws(() => reconcileSource(parsed, {}), /INVALID_EXPECTATIONS/);
});

test('cleanup purges excluded rows from existing history and re-import skips already imported rows without re-adding excluded', async () => {
  const state = memoryPort();
  const chi = sourceRow({ 0: 1, 2: 'Cafe', 3: -12500, 4: 'VND' });
  const choVay = sourceRow({ 0: 2, 2: 'Cho vay', 3: -30000000, 4: 'VND' });
  const traLai = sourceRow({ 0: 3, 2: 'Trả lãi', 3: -500000, 4: 'VND' });
  const jpy = sourceRow({ 0: 4, 2: 'Thu nhập khác', 3: 600000, 4: 'JPY' });

  const parsed = parseMoneyLover(sourceTable(chi, choVay, traLai, jpy));
  assert.equal(parsed.excluded.length, 3);
  assert.equal(parsed.transactions.length, 1);
  const validTx = parsed.transactions[0];

  // Simulate old snapshot with 4 rows (1 valid, 3 excluded)
  state.snapshot.exists = true;
  state.snapshot.headers = [...HISTORY_HEADERS];
  state.snapshot.rows = [
    historyRow(validTx),
    historyRow({ legacyId: 'ml1_' + '2'.repeat(64), date: '2026-02-28', type: 'CHI', amount: 30000000, currency: 'VND', amountVnd: 30000000, category: 'Cho vay', wallet: 'Wallet A', note: '', counterparty: '', event: '', excludedFromReport: true, source: 'MONEYLOVER', originalNo: '2', member: '' }),
    historyRow({ legacyId: 'ml1_' + '3'.repeat(64), date: '2026-02-28', type: 'CHI', amount: 500000, currency: 'VND', amountVnd: 500000, category: 'Trả lãi', wallet: 'Wallet A', note: '', counterparty: '', event: '', excludedFromReport: false, source: 'MONEYLOVER', originalNo: '3', member: '' }),
    historyRow({ legacyId: 'ml1_' + '4'.repeat(64), date: '2026-02-28', type: 'THU', amount: 600000, currency: 'JPY', amountVnd: null, category: 'Thu nhập khác', wallet: 'Yên', note: '', counterparty: '', event: '', excludedFromReport: false, source: 'MONEYLOVER', originalNo: '4', member: '' }),
  ];

  const plan = planCleanup(state.snapshot);
  assert.equal(plan.toRemove.length, 3);
  assert.equal(plan.toKeep.length, 1);
  assert.deepEqual(plan.removedRows.map((r) => r.reason).sort(), ['CURRENCY_NOT_VND', 'NON_CONSUMPTION_CATEGORY', 'NON_CONSUMPTION_CATEGORY']);

  const cleaned = await cleanupImportedHistory(state.port, true);
  assert.equal(cleaned.status, 'CLEANED_UP');
  assert.equal(cleaned.removedCount, 3);
  assert.equal(cleaned.remainingCount, 1);
  assert.equal(state.snapshot.rows.length, 1);

  // Now run importer again with source containing all 4 rows
  const reimport = await importMoneyLover(parsed, state.port, true);
  assert.equal(reimport.status, 'ALREADY_IMPORTED');
  assert.equal(reimport.inserted, 0);
  assert.equal(reimport.skipped, 1);
  assert.equal(state.snapshot.rows.length, 1);
});

test('replaceHistory executes exactly 1 batchUpdate write, never calls values.clear, and atomically cleans 1589 to 1581 rows', async () => {
  const env = testEnvironment();
  const config = readGoogleConfiguration(env)!;
  assert.ok(config);

  // Synthesize 1581 valid rows (1488 CHI, 93 THU) matching acceptance totals
  const sourceRows: unknown[][] = [];
  let id = 1;
  for (let i = 0; i < 1487; i++) sourceRows.push(sourceRow({ 0: id++, 2: 'Cafe', 3: -382810, 4: 'VND' }));
  sourceRows.push(sourceRow({ 0: id++, 2: 'Cafe', 3: -411350, 4: 'VND' }));
  for (let i = 0; i < 92; i++) sourceRows.push(sourceRow({ 0: id++, 2: 'Lương', 3: 8382800, 4: 'VND' }));
  sourceRows.push(sourceRow({ 0: id++, 2: 'Lương', 3: 8387330, 4: 'VND' }));

  // 8 excluded rows: 1 Cho vay, 6 Trả lãi, 1 JPY
  sourceRows.push(sourceRow({ 0: id++, 2: 'Cho vay', 3: -30000000, 4: 'VND' }));
  for (let i = 0; i < 5; i++) sourceRows.push(sourceRow({ 0: id++, 2: 'Trả lãi', 3: -800000, 4: 'VND' }));
  sourceRows.push(sourceRow({ 0: id++, 2: 'Trả lãi', 3: -1036837, 4: 'VND' }));
  sourceRows.push(sourceRow({ 0: id++, 2: 'Thu nhập khác', 3: 600000, 4: 'JPY' }));

  const parsed = parseMoneyLover(sourceTable(...sourceRows));
  assert.equal(parsed.transactions.length, 1581);
  assert.equal(parsed.excluded.length, 8);
  const summary = summarizeSource(parsed);
  assert.equal(summary.expenseCount, 1488);
  assert.equal(summary.incomeCount, 93);
  assert.equal(summary.reportVnd.expense, 569649820);
  assert.equal(summary.reportVnd.income, 779604930);
  assert.equal(summary.net, 209955110);
  assert.equal(summary.currencies.JPY, undefined);
  assert.equal(summary.currencies.VND, 1581);

  // Simulated Google Sheets grid:
  // Row 0: Header
  // Rows 1..1589: 1589 data rows (1581 valid + 8 excluded)
  const initialGrid: unknown[][] = [
    [...HISTORY_HEADERS],
    ...parsed.transactions.map(historyRow),
    historyRow({ legacyId: 'ml1_' + 'a'.repeat(64), date: '2026-02-28', type: 'CHI', amount: 30000000, currency: 'VND', amountVnd: 30000000, category: 'Cho vay', wallet: 'Wallet A', note: '', counterparty: '', event: '', excludedFromReport: true, source: 'MONEYLOVER', originalNo: '1582', member: '' }),
    ...Array.from({ length: 6 }, (_, idx) => historyRow({ legacyId: 'ml1_' + String(idx).repeat(64), date: '2026-02-28', type: 'CHI', amount: 800000, currency: 'VND', amountVnd: 800000, category: 'Trả lãi', wallet: 'Wallet A', note: '', counterparty: '', event: '', excludedFromReport: false, source: 'MONEYLOVER', originalNo: String(1583 + idx), member: '' })),
    historyRow({ legacyId: 'ml1_' + 'f'.repeat(64), date: '2026-02-28', type: 'THU', amount: 600000, currency: 'JPY', amountVnd: null, category: 'Thu nhập khác', wallet: 'Yên', note: '', counterparty: '', event: '', excludedFromReport: false, source: 'MONEYLOVER', originalNo: '1589', member: '' }),
  ];
  assert.equal(initialGrid.length, 1590); // 1 header + 1589 data

  let grid = structuredClone(initialGrid);
  let batchUpdateCalls = 0;
  let valuesClearCalls = 0;
  let batchUpdateRequests: any[] = [];

  const mockSheets = {
    spreadsheets: {
      get: async () => ({
        data: {
          sheets: [{
            properties: {
              sheetId: 101,
              title: 'MoneyLover_History',
              sheetType: 'GRID',
              gridProperties: {
                rowCount: 3000,
                columnCount: HISTORY_HEADERS.length,
              },
            },
          }],
        },
      }),
      values: {
        get: async () => ({
          data: {
            values: grid.filter((r) => r.some((c) => c !== '' && c !== null && c !== undefined)),
          },
        }),
        clear: async () => {
          valuesClearCalls++;
          throw new Error('values.clear must NOT be called!');
        },
      },
      batchUpdate: async ({ requestBody }: any) => {
        batchUpdateCalls++;
        batchUpdateRequests = requestBody.requests;
        for (const req of requestBody.requests) {
          if (req.updateCells) {
            const { range, rows } = req.updateCells;
            const startRow = range.startRowIndex ?? 0;
            const newRowCount = rows.length;
            // Write new rows
            for (let r = 0; r < newRowCount; r++) {
              grid[startRow + r] = rows[r].values.map((v: any) =>
                v.userEnteredValue.numberValue ?? v.userEnteredValue.boolValue ?? v.userEnteredValue.stringValue ?? ''
              );
            }
            // Clear remaining cells in range not covered by rows
            for (let r = startRow + newRowCount; r < grid.length; r++) {
              grid[r] = [];
            }
          }
        }
        return { data: {} };
      },
    },
  };

  const port = await makeHistoryImportPort(config, mockSheets);

  // 1. Check before snapshot and plan
  const beforeSnapshot = await port.readHistory();
  assert.equal(beforeSnapshot.rows.length, 1589);
  const plan = planCleanup(beforeSnapshot);
  assert.equal(plan.toRemove.length, 8);
  assert.equal(plan.toKeep.length, 1581);
  assert.equal(plan.removedRows.length, 8);

  // 2. Perform atomic cleanup
  const cleanupResult = await cleanupImportedHistory(port, true);
  assert.equal(cleanupResult.status, 'CLEANED_UP');
  assert.equal(cleanupResult.removedCount, 8);
  assert.equal(cleanupResult.remainingCount, 1581);

  // 3. Verify exactly 1 write request, 0 values.clear calls
  assert.equal(batchUpdateCalls, 1);
  assert.equal(valuesClearCalls, 0);
  assert.equal(batchUpdateRequests.length, 1);
  assert.ok('updateCells' in batchUpdateRequests[0]);
  assert.equal(batchUpdateRequests[0].updateCells.range.sheetId, 101);
  assert.equal(batchUpdateRequests[0].updateCells.range.startRowIndex, 1);
  assert.equal('endRowIndex' in batchUpdateRequests[0].updateCells.range, false);
  assert.equal(batchUpdateRequests[0].updateCells.rows.length, 1581);

  // 4. Verify Header is completely unmodified (row 0 untouched)
  assert.deepEqual(grid[0], HISTORY_HEADERS);

  // 5. Verify 8 excess rows at the bottom (indices 1582..1589) are cleared
  for (let r = 1582; r < 1590; r++) {
    assert.deepEqual(grid[r], [], `Row ${r} must be empty after cleanup`);
  }

  // 6. Verify readback has exactly 1581 rows and planCleanup shows ALREADY_CLEAN
  const afterSnapshot = await port.readHistory();
  assert.equal(afterSnapshot.rows.length, 1581);
  const afterPlan = planCleanup(afterSnapshot);
  assert.equal(afterPlan.toRemove.length, 0);
  assert.equal(afterPlan.toKeep.length, 1581);

  // 7. Verify re-import idempotency
  const reimport = await importMoneyLover(parsed, port, true);
  assert.equal(reimport.status, 'ALREADY_IMPORTED');
  assert.equal(reimport.pending, 0);
  assert.equal(reimport.skipped, 1581);
  assert.equal(reimport.inserted, 0);
  assert.equal(reimport.rejected, 0);
});

test('real XLSX reader handles sanitized shared/inline strings, sparse cells and numeric dates; rejects formulas', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'moneylover-fixture-')), path = join(dir, 'fixture.xlsx');
  const builder = `import zipfile,json,sys,xml.sax.saxutils as X
headers=json.loads(sys.argv[2])
ns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'
cells=''.join('<c r="'+chr(65+i)+'1" t="inlineStr"><is><t>'+X.escape(h)+'</t></is></c>' for i,h in enumerate(headers))
data='<row r="1">'+cells+'</row><row r="2"><c r="B2"><v>46081</v></c><c r="C2" t="s"><v>0</v></c><c r="D2"><v>-123</v></c><c r="E2" t="inlineStr"><is><t>VND</t></is></c><c r="F2" t="inlineStr"><is><t>Wallet</t></is></c></row>'
with zipfile.ZipFile(sys.argv[1],'w') as z:
 z.writestr('xl/workbook.xml','<workbook xmlns="'+ns+'" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Transaction" sheetId="1" r:id="rId1"/></sheets></workbook>')
 z.writestr('xl/_rels/workbook.xml.rels','<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>')
 z.writestr('xl/sharedStrings.xml','<sst xmlns="'+ns+'"><si><t>Cafe</t></si></sst>')
 z.writestr('xl/worksheets/sheet1.xml','<worksheet xmlns="'+ns+'"><sheetData>'+data+'</sheetData></worksheet>')
`;
  const python = process.env.MONEYLOVER_PYTHON ?? (process.platform === 'win32' ? 'python' : 'python3');
  const built = spawnSync(python, ['-c', builder, path, JSON.stringify(sourceTable(sourceRow()).rows[0].cells)], { encoding: 'utf8', windowsHide: true });
  assert.equal(built.status, 0, built.stderr);
  const before = await readFile(path), parsed = parseMoneyLover(await readMoneyLoverXlsx(path));
  assert.deepEqual(parsed.rejected, []); assert.equal(parsed.transactions[0].category, 'Cafe');
  assert.equal(parsed.transactions[0].amount, 123); assert.equal(parsed.transactions[0].date, '2026-02-28');
  assert.deepEqual(await readFile(path), before);
  const report = join(dir, 'dry-run.json'), expected = join(dir, 'expectations.json');
  await writeFile(expected, JSON.stringify({ rows: 1, expenseVnd: 123 }));
  const runCli = (...args: string[]) => spawnSync(process.execPath,
    ['--import', 'tsx', 'scripts/import-moneylover.ts', '--file', path, ...args],
    { cwd: new URL('..', import.meta.url), encoding: 'utf8', windowsHide: true,
      env: { ...process.env, SPREADSHEET_ID: '', GOOGLE_PRIVATE_KEY: 'private-marker-not-for-logs', MONEYLOVER_SOURCE_FILE: '' } });
  const dryRun = runCli('--expectations', expected, '--report', report);
  assert.equal(dryRun.status, 0, dryRun.stderr);
  assert.equal(JSON.parse(dryRun.stdout).status, 'DRY_RUN_SOURCE_ONLY');
  assert.equal(JSON.parse(await readFile(report, 'utf8')).reconciliation.matches, true);
  assert.equal(runCli('--report', path).stderr.trim(), 'SOURCE_OVERWRITE_FORBIDDEN');
  const apply = runCli('--apply');
  assert.equal(apply.status, 1); assert.equal(apply.stderr.trim(), 'SERVER_CONFIGURATION_ERROR');
  assert.equal(apply.stderr.includes('private-marker'), false);
  await writeFile(expected, JSON.stringify({ rows: 99 }));
  const mismatch = runCli('--apply', '--expectations', expected);
  assert.equal(mismatch.status, 1); assert.equal(JSON.parse(mismatch.stdout).status, 'RECONCILIATION_MISMATCH');
  assert.deepEqual(await readFile(path), before);
  const mutate = `import zipfile,sys
with zipfile.ZipFile(sys.argv[1]) as z: parts={n:z.read(n) for n in z.namelist()}
parts['xl/worksheets/sheet1.xml']=parts['xl/worksheets/sheet1.xml'].replace(b'<v>-123</v>',b'<f>1+2</f><v>3</v>')
with zipfile.ZipFile(sys.argv[1],'w') as z:
 for n,d in parts.items():z.writestr(n,d)
`;
  assert.equal(spawnSync(python, ['-c', mutate, path], { windowsHide: true }).status, 0);
  await assert.rejects(readMoneyLoverXlsx(path), /FORMULA_NOT_ALLOWED/);
  await writeFile(join(dir, 'invalid.xlsx'), 'not a zip');
  await assert.rejects(readMoneyLoverXlsx(join(dir, 'invalid.xlsx')), /XLSX_READ_FAILED/);
});
