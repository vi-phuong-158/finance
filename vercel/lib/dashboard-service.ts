import { sumMoney } from './moneylover/schema.js';
import type { UnifiedTransaction } from './unified-transactions.js';
import type { FinanceQuery } from './finance-query.js';

export function buildDashboard(rows: UnifiedTransaction[], query: FinanceQuery, now = new Date()) {
  const eligible = rows.filter((t) => !t.excludedFromReport && t.category !== 'Chuyển nội bộ' && t.amountVnd !== null);
  const totals = (ts: UnifiedTransaction[]) => {
    const income = sumMoney(ts.filter((t) => t.type === 'THU').map((t) => t.amountVnd!));
    const expense = sumMoney(ts.filter((t) => t.type === 'CHI').map((t) => t.amountVnd!));
    return { income, expense, net: sumMoney([income, -expense]) };
  };
  const groups = (key: 'category' | 'accountOrWallet') => {
    const map = new Map<string | null, UnifiedTransaction[]>();
    for (const t of rows) { const list = map.get(t[key]) ?? []; list.push(t); map.set(t[key], list); }
    return [...map].map(([name, transactions]) => ({ name, transactionCount: transactions.length,
      ...totals(transactions.filter((t) => !t.excludedFromReport && t.category !== 'Chuyển nội bộ' && t.amountVnd !== null)) }));
  };
  return { period: { from: query.from, to: query.to, timeZone: 'Asia/Ho_Chi_Minh', dateBoundaries: 'inclusive' },
    currency: 'VND', ...totals(eligible), transactionCount: rows.length, includedTransactionCount: eligible.length,
    excludedTransactionCount: rows.filter((t) => t.excludedFromReport || t.category === 'Chuyển nội bộ').length,
    unconvertedTransactionCount: rows.filter((t) => t.amountVnd === null).length,
    unconvertedByCurrency: Object.fromEntries([...new Set(rows.filter((t) => t.amountVnd === null).map((t) => t.currency))]
      .map((currency) => [currency, { income: sumMoney(rows.filter((t) => t.currency === currency && t.amountVnd === null && t.type === 'THU').map((t) => t.amount)),
        expense: sumMoney(rows.filter((t) => t.currency === currency && t.amountVnd === null && t.type === 'CHI').map((t) => t.amount)) }])),
    possibleDuplicateCount: rows.filter((t) => t.possibleDuplicate).length,
    duplicatePolicy: 'FLAG_ONLY_TOTALS_MAY_INCLUDE_BOTH_SOURCES',
    categorySummary: groups('category'), accountSummary: groups('accountOrWallet'),
    recentTransactions: rows.slice(0, 10), lastUpdatedAt: now.toISOString(),
    lastUpdatedAtMeaning: 'READ_AT_NOT_SOURCE_MODIFIED_AT' };
}
