import { parseMoneyLover } from '../lib/moneylover/parser.js';
import type { SourceTable } from '../lib/moneylover/parser.js';
import { HISTORY_HEADERS, historyRow } from '../lib/moneylover/schema.js';
import { TRANSACTION_HEADERS } from '../lib/sheet-schema.js';

export const SOURCE_HEADERS = ['No.', 'Ngày', 'Nhóm', 'Số tiền', 'Đơn vị tiền tệ', 'Ví', 'Ghi chú', 'Với', 'Sự kiện', 'Không tính vào báo cáo', 'Thành viên'];
export function sourceTable(...rows: unknown[][]): SourceTable {
  return { rows: [[...SOURCE_HEADERS], ...rows].map((cells, i) => ({ cells, rowNumber: i + 1 })) };
}
export function sourceRow(overrides: Record<number, unknown> = {}): unknown[] {
  const row: unknown[] = [1, '2026-02-28', 'Cafe', -12500, 'VND', 'Wallet A', 'Note\nsecond line', 'Merchant A', 'Event A', '', 'Member A'];
  for (const [key, value] of Object.entries(overrides)) row[Number(key)] = value;
  return row;
}
export function financeFixture() {
  const parsed = parseMoneyLover(sourceTable(
    sourceRow(), sourceRow({ 0: 2, 3: 600000, 4: 'JPY', 5: 'Wallet JPY', 2: 'Thu nhập khác' }),
    sourceRow({ 0: 3, 3: -30000, 2: 'Cho vay', 9: '✅' }), sourceRow({ 0: 4, 3: 100000, 2: 'Lương' }),
  ));
  return { bank: [TRANSACTION_HEADERS,
    ['28/02/2026 13:22', 'VCB', 'CHI', 12500, 50000, 'Merchant A', 'Cafe', 'Cafe', '1'.repeat(64)],
    ['28/02/2026 13:23', 'BIDV', 'CHI', 50000, '', '', 'CHUYEN TIEN NOI BO', 'Chuyển nội bộ', '2'.repeat(64)],
  ], history: [HISTORY_HEADERS, ...parsed.transactions.map(historyRow)] };
}
