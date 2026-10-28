import { readFile, open } from 'node:fs/promises';
import { isAbsolute, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readGoogleConfiguration } from '../lib/configuration.js';
import { readMoneyLoverXlsx } from '../lib/moneylover/xlsx-reader.js';
import { parseMoneyLover } from '../lib/moneylover/parser.js';
import { publicImportReport, summarizeSource, reconcileSource } from '../lib/moneylover/report.js';
import { cleanupImportedHistory, importMoneyLover } from '../lib/moneylover/importer.js';
import { makeHistoryImportPort } from '../lib/moneylover/google-history.js';

async function main() {
  const args = process.argv.slice(2);
  let file = process.env.MONEYLOVER_SOURCE_FILE ?? '', reportPath = '', expectationsPath = '', decisionsPath = '', apply = false, checkSheets = false, cleanup = false;
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === '--apply') apply = true;
    else if (arg === '--check-sheets') checkSheets = true;
    else if (arg === '--cleanup') cleanup = true;
    else if (['--file', '--report', '--expectations', '--overlap-decisions'].includes(arg) && args[i + 1] && !args[i + 1].startsWith('--')) {
      if (arg === '--file') file = args[++i];
      else if (arg === '--report') reportPath = args[++i];
      else if (arg === '--expectations') expectationsPath = args[++i];
      else decisionsPath = args[++i];
    } else throw new Error('INVALID_ARGUMENTS');
  }
  if (!file) throw new Error('MONEYLOVER_SOURCE_FILE_REQUIRED');
  if (reportPath && resolve(file) === resolve(reportPath)) throw new Error('SOURCE_OVERWRITE_FORBIDDEN');
  if (apply && !expectationsPath) throw new Error('EXPECTATIONS_REQUIRED');
  if (decisionsPath && !(apply || checkSheets || cleanup)) throw new Error('OVERLAP_CHECK_REQUIRES_SHEETS');
  if (reportPath) {
    const location = relative(fileURLToPath(new URL('../..', import.meta.url)), resolve(reportPath));
    if (!location || (!isAbsolute(location) && location !== '..' && !location.startsWith(`..${sep}`))) throw new Error('REPORT_OUTSIDE_REPOSITORY_REQUIRED');
  }
  const parsed = parseMoneyLover(await readMoneyLoverXlsx(file));
  const summary = summarizeSource(parsed);
  const expectations = expectationsPath ? JSON.parse(await readFile(expectationsPath, 'utf8')) : undefined;
  const decisions = decisionsPath ? JSON.parse(await readFile(decisionsPath, 'utf8')) : undefined;
  if (expectationsPath && (!expectations || typeof expectations !== 'object' || Array.isArray(expectations))) throw new Error('INVALID_EXPECTATIONS');
  const reconciliation = expectations ? reconcileSource(parsed, expectations) : { matches: null, checks: [], status: 'EXPECTATIONS_NOT_PROVIDED' };
  const report: Record<string, unknown> = { mode: apply ? 'APPLY' : 'DRY_RUN', summary, reconciliation,
    warnings: ['IDENTICAL_SOURCE_ROWS_ARE_PRESERVED'] };
  let lock: Awaited<ReturnType<typeof open>> | undefined;
  let reportFile: Awaited<ReturnType<typeof open>> | undefined;
  const lockPath = new URL('./.moneylover-import.lock', import.meta.url);
  try {
    // Reserve the report before any external write; existing files/symlinks are never overwritten.
    if (reportPath) reportFile = await open(reportPath, 'wx', 0o600);
    if (parsed.rejected.length) {
      report.status = 'SOURCE_ROWS_REJECTED';
      process.exitCode = 1;
    } else if (reconciliation.matches === false) {
      report.status = 'RECONCILIATION_MISMATCH';
      process.exitCode = 1;
    } else if (apply || checkSheets || cleanup) {
      const config = readGoogleConfiguration(process.env);
      if (!config) throw new Error('SERVER_CONFIGURATION_ERROR');
      if (apply) lock = await open(lockPath, 'wx', 0o600).catch(() => { throw new Error('IMPORT_LOCKED'); });
      const port = await makeHistoryImportPort(config, undefined, apply);
      // No cleanup write may precede the source/overlap/size gates.
      if (cleanup && apply) {
        const preflight = await importMoneyLover(parsed, port, false, decisions);
        if (preflight.overlap.unresolvedCount) throw new Error('OVERLAP_REVIEW_REQUIRED');
      }
      if (cleanup) {
        report.cleanup = await cleanupImportedHistory(port, apply);
      }
      const result = await importMoneyLover(parsed, port, apply, decisions);
      report.import = result;
      report.status = result.overlap.unresolvedCount ? 'OVERLAP_REVIEW_REQUIRED' : apply ? 'APPLY_VERIFIED' : 'DRY_RUN_WITH_SHEETS';
      if (result.overlap.unresolvedCount) process.exitCode = 2;
    } else {
      report.status = 'DRY_RUN_SOURCE_ONLY';
      report.import = { inserted: 0, pending: parsed.transactions.length, skipped: null, duplicateCheck: 'NOT_CHECKED_AGAINST_SHEETS' };
    }
    const publicReport = publicImportReport(report);
    if (reportFile) await reportFile.writeFile(JSON.stringify(publicReport, null, 2), 'utf8');
    console.log(JSON.stringify(publicReport));
  } finally {
    if (reportFile) await reportFile.close();
    if (lock) {
      await lock.close();
      const { unlink } = await import('node:fs/promises');
      await unlink(lockPath);
    }
  }
}
main().catch((error: unknown) => {
  const code = (error as Error)?.message;
  const allowed = /^(INVALID_ARGUMENTS|INVALID_EXPECTATIONS|EXPECTATIONS_REQUIRED|REPORT_OUTSIDE_REPOSITORY_REQUIRED|OVERLAP_CHECK_REQUIRES_SHEETS|INVALID_OVERLAP_DECISIONS|OVERLAP_REVIEW_REQUIRED|TRANSACTIONS_CHANGED_RETRY_DRY_RUN|HISTORY_SOURCE_LIMIT_EXCEEDED|HISTORY_BATCH_TOO_LARGE|HISTORY_WRITE_DISABLED|FINANCE_SCHEMA_MISMATCH|FINANCE_SOURCE_LIMIT_EXCEEDED|INVALID_FINANCE_ROW|DUPLICATE_FINANCE_ID|MONEYLOVER_SOURCE_FILE_REQUIRED|SOURCE_OVERWRITE_FORBIDDEN|SERVER_CONFIGURATION_ERROR|IMPORT_LOCKED|XLSX_FILE_REQUIRED|XLSX_READ_FAILED|XLSX_TOO_LARGE|INVALID_XLSX|TRANSACTION_SHEET_REQUIRED|FORMULA_NOT_ALLOWED|UNSUPPORTED_CELL_TYPE|EMPTY_TRANSACTION_SHEET|INVALID_MONEYLOVER_HEADERS|HISTORY_SCHEMA_MISMATCH|INVALID_HISTORY_ROW|DUPLICATE_HISTORY_ID|DUPLICATE_SOURCE_ID|HISTORY_CONTENT_CONFLICT|SOURCE_ROWS_REJECTED|HISTORY_CHANGED_RETRY_DRY_RUN|IMPORT_OUTCOME_UNKNOWN_READBACK_REQUIRED|IMPORT_READBACK_MISMATCH|HISTORY_CHANGED_DURING_IMPORT|CLEANUP_READBACK_MISMATCH|REPLACE_HISTORY_NOT_SUPPORTED)$/;
  console.error(allowed.test(code) ? code : 'MONEYLOVER_IMPORT_FAILED');
  process.exitCode = 1;
});
