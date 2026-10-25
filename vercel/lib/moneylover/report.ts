import { sumMoney } from './schema.js';
import type { MoneyLoverTransaction } from './schema.js';
import type { ParseResult } from './parser.js';

export function summarizeSource(parsed: ParseResult) {
  const ts = parsed.transactions;
  const counts = (key: 'currency' | 'wallet' | 'category') => Object.fromEntries(
    [...new Set(ts.map((t) => t[key]))].sort().map((name) => [name, ts.filter((t) => t[key] === name).length]));
  const totals = (rows: MoneyLoverTransaction[]) => ({
    income: sumMoney(rows.filter((t) => t.type === 'THU').map((t) => t.amount)),
    expense: sumMoney(rows.filter((t) => t.type === 'CHI').map((t) => t.amount)),
  });
  const eligible = ts.filter((t) => !t.excludedFromReport && t.category !== 'Chuyển nội bộ' && t.amountVnd !== null);
  return {
    rows: ts.length + parsed.rejected.length, accepted: ts.length,
    dateRange: { from: ts.map((t) => t.date).sort()[0] ?? null, to: ts.map((t) => t.date).sort().at(-1) ?? null },
    incomeCount: ts.filter((t) => t.type === 'THU').length, expenseCount: ts.filter((t) => t.type === 'CHI').length,
    currencies: counts('currency'), wallets: counts('wallet'), categories: counts('category'),
    totalsByCurrency: Object.fromEntries(Object.keys(counts('currency')).map((currency) => [currency, totals(ts.filter((t) => t.currency === currency))])),
    categoryExpenseVnd: Object.fromEntries(Object.keys(counts('category')).map((category) => [category,
      sumMoney(ts.filter((t) => t.category === category && t.type === 'CHI' && t.currency === 'VND').map((t) => t.amount))])),
    reportVnd: { income: sumMoney(eligible.filter((t) => t.type === 'THU').map((t) => t.amountVnd!)),
      expense: sumMoney(eligible.filter((t) => t.type === 'CHI').map((t) => t.amountVnd!)) },
    excludedCount: ts.filter((t) => t.excludedFromReport).length,
    foreignUnconverted: ts.filter((t) => t.amountVnd === null).length,
    identicalRowsPreserved: parsed.identicalRows, rejected: parsed.rejected,
  };
}

export type ReconciliationExpectations = {
  rows?: number; incomeCount?: number; expenseCount?: number;
  expenseVnd?: number; incomeVnd?: number; categoryExpenseVnd?: Record<string, number>;
};
export function reconcileSource(parsed: ParseResult, expected: ReconciliationExpectations) {
  const s = summarizeSource(parsed);
  const checks: { name: string; expected: number; actual: number }[] = [];
  for (const [key, value] of Object.entries(expected)) {
    if (key === 'categoryExpenseVnd') {
      if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('INVALID_EXPECTATIONS');
      for (const [category, total] of Object.entries(value)) {
        if (typeof total !== 'number' || total < 0 || !Number.isFinite(total)) throw new Error('INVALID_EXPECTATIONS');
        checks.push({ name: `category:${category}`, expected: total,
          actual: Object.hasOwn(s.categoryExpenseVnd, category) ? s.categoryExpenseVnd[category] : 0 });
      }
    } else {
      if (!['rows', 'incomeCount', 'expenseCount', 'expenseVnd', 'incomeVnd'].includes(key)
        || typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) throw new Error('INVALID_EXPECTATIONS');
      const actual = key === 'expenseVnd' ? s.totalsByCurrency.VND?.expense ?? 0
        : key === 'incomeVnd' ? s.totalsByCurrency.VND?.income ?? 0 : s[key as 'rows' | 'incomeCount' | 'expenseCount'];
      checks.push({ name: key, expected: value, actual });
    }
  }
  if (!checks.length) throw new Error('INVALID_EXPECTATIONS');
  return { matches: parsed.rejected.length === 0 && checks.every((c) => c.actual === c.expected),
    checks: checks.map((c) => ({ ...c, difference: c.actual - c.expected })) };
}
