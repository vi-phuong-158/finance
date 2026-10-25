import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { stat } from 'node:fs/promises';
import type { SourceTable } from './parser.js';

export async function readMoneyLoverXlsx(path: string): Promise<SourceTable> {
  if (!path.toLowerCase().endsWith('.xlsx')) throw new Error('XLSX_FILE_REQUIRED');
  const info = await stat(path).catch(() => { throw new Error('XLSX_READ_FAILED'); });
  if (!info.isFile() || info.size > 16 * 1024 * 1024) throw new Error('XLSX_TOO_LARGE');
  try {
    const { stdout } = await promisify(execFile)(process.env.MONEYLOVER_PYTHON ?? (process.platform === 'win32' ? 'python' : 'python3'),
      [fileURLToPath(new URL('../../scripts/read-moneylover-xlsx.py', import.meta.url)), path],
      { encoding: 'utf8', maxBuffer: 32 * 1024 * 1024, timeout: 30_000, windowsHide: true });
    return JSON.parse(stdout) as SourceTable;
  } catch (error) {
    const code = (error as { stderr?: string }).stderr?.trim();
    if (code && /^(XLSX_TOO_LARGE|INVALID_XLSX|TRANSACTION_SHEET_REQUIRED|FORMULA_NOT_ALLOWED|UNSUPPORTED_CELL_TYPE)$/.test(code)) throw new Error(code);
    throw new Error('XLSX_READ_FAILED');
  }
}
