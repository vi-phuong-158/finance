export const INTEREST_RULE = 'MONTHLY_25_V1';
export type Loan = { id: string; name: string; principal: number };
export type Disbursement = { id: string; loanId: string; principal: number; annualRate: number; date: string; maturity: string | null };
export type LoanPayment = { row: number; loanId: string; type: 'Thu gốc' | 'Thu lãi'; amount: number; date: string };
export type InterestData = { loans: Loan[]; disbursements: Disbursement[]; payments: LoanPayment[] };

export function dateOnly(value: unknown): string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)
    || new Date(value + 'T00:00:00Z').toISOString().slice(0, 10) !== value
    || value < '1970-01-01' || value > '2200-12-31') throw new Error('INVALID_INTEREST_DATE');
  return value;
}
export function sheetDate(value: unknown): string {
  if (typeof value !== 'number' || !Number.isInteger(value) || value < 25569 || value > 109939) throw new Error('INVALID_INTEREST_DATE');
  return dateOnly(new Date((value - 25569) * 86400000).toISOString().slice(0, 10));
}
export function periodStart(date: string): string {
  const [y, m, d] = dateOnly(date).split('-').map(Number);
  return new Date(Date.UTC(y, m - (d < 25 ? 2 : 1), 25)).toISOString().slice(0, 10);
}
export function nextPeriod(start: string): string {
  const [y, m] = start.split('-').map(Number);
  return new Date(Date.UTC(y, m, 25)).toISOString().slice(0, 10);
}
export function firstPeriod(date: string): string {
  const start = periodStart(date);
  return date === start ? start : nextPeriod(start);
}
const round = (value: number) => Math.round(value + 1e-8);

export function buildInterest(data: InterestData, asOf: string) {
  dateOnly(asOf);
  const start = periodStart(asOf), end = nextPeriod(start);
  const loans = data.loans.map((loan) => {
    const tranches = data.disbursements.filter((d) => d.loanId === loan.id)
      .sort((a, b) => a.date.localeCompare(b.date) || a.id.localeCompare(b.id));
    if (!tranches.length || tranches.reduce((sum, t) => sum + t.principal, 0) !== loan.principal) throw new Error('INTEREST_PRINCIPAL_MISMATCH');
    const payments = data.payments.filter((p) => p.loanId === loan.id && p.date <= asOf);
    const principalPayments = payments.filter((p) => p.type === 'Thu gốc');
    for (const p of principalPayments) {
      const received = principalPayments.filter((q) => q.date <= p.date).reduce((s, q) => s + q.amount, 0);
      const disbursed = tranches.filter((t) => t.date <= p.date).reduce((s, t) => s + t.principal, 0);
      if (received > disbursed) throw new Error('INTEREST_OVERPAYMENT');
    }
    const amountForPeriod = (from: string) => {
      const to = nextPeriod(from);
      const repaid = principalPayments.filter((p) => p.date <= from).reduce((s, p) => s + p.amount, 0);
      let previous = 0;
      return tranches.reduce((sum, tranche) => {
        // FIFO allocates repayments to the oldest disbursement, preserving each maturity.
        const remaining = Math.max(0, tranche.principal - Math.max(0, repaid - previous));
        previous += tranche.principal;
        const eligible = firstPeriod(tranche.date) <= from && (!tranche.maturity || to <= tranche.maturity);
        return sum + (eligible ? remaining * tranche.annualRate / 12 : 0);
      }, 0);
    };
    const monthIndex = (date: string) => Number(date.slice(0, 4)) * 12 + Number(date.slice(5, 7));
    const completedPeriods = Math.max(...tranches.map((t) => Math.max(0,
      monthIndex(periodStart(t.maturity && t.maturity < asOf ? t.maturity : asOf)) - monthIndex(firstPeriod(t.date)))));
    let accrued = 0, iterations = 0;
    for (let from = firstPeriod(tranches[0].date); from < start; from = nextPeriod(from)) {
      if (++iterations > 1200) throw new Error('INTEREST_SOURCE_LIMIT_EXCEEDED');
      accrued += amountForPeriod(from);
    }
    const accruedInterest = round(accrued);
    const paidInterest = payments.filter((p) => p.type === 'Thu lãi').reduce((s, p) => s + p.amount, 0);
    const repaidPrincipal = principalPayments.reduce((s, p) => s + p.amount, 0);
    const outstandingPrincipal = tranches.filter((t) => t.date <= asOf).reduce((s, t) => s + t.principal, 0) - repaidPrincipal;
    const currentInterest = round(amountForPeriod(start));
    const advanceInterest = Math.max(0, paidInterest - accruedInterest);
    const maturities = tranches.map((t) => t.maturity).filter((d): d is string => d !== null);
    const maturity = maturities.length === tranches.length ? maturities.sort().at(-1)! : null;
    const status = asOf < tranches[0].date ? 'Chưa giải ngân' : outstandingPrincipal === 0 ? 'Đã tất toán'
      : maturity && maturity < asOf ? 'Quá hạn' : maturity && (Date.parse(maturity) - Date.parse(asOf)) / 86400000 <= 7 ? 'Sắp đến hạn' : 'Đang vay';
    return { ...loan, outstandingPrincipal, repaidPrincipal, accruedInterest, paidInterest,
      interestDue: Math.max(0, accruedInterest - paidInterest), advanceInterest, currentInterest,
      currentRemaining: Math.max(0, currentInterest - advanceInterest),
      collectedThisPeriod: payments.filter((p) => p.type === 'Thu lãi' && p.date >= start).reduce((s, p) => s + p.amount, 0),
      completedPeriods, maturity, status, disbursements: tranches, payments };
  });
  const sum = (key: 'outstandingPrincipal' | 'accruedInterest' | 'paidInterest' | 'interestDue' | 'currentInterest' | 'advanceInterest' | 'collectedThisPeriod' | 'currentRemaining') => loans.reduce((s, l) => s + l[key], 0);
  return { rule: INTEREST_RULE, asOf, period: { from: start, to: end }, loans,
    totals: { outstandingPrincipal: sum('outstandingPrincipal'), accruedInterest: sum('accruedInterest'),
      paidInterest: sum('paidInterest'), interestDue: sum('interestDue'), currentInterest: sum('currentInterest'),
      advanceInterest: sum('advanceInterest'), collectedThisPeriod: sum('collectedThisPeriod'), currentRemaining: sum('currentRemaining') } };
}

export const LOAN_HEADERS = ['Mã khoản', 'Người vay', 'Gốc ban đầu (đ)', 'Lãi suất/năm', 'Ngày bắt đầu', 'Ngày đáo hạn'];
export const PAYMENT_HEADERS = ['Ngày', 'Mã khoản', 'Loại giao dịch', 'Người nộp/ghi chú', 'Số tiền', 'Ghi chú'];
export const DISBURSEMENT_HEADERS = ['Mã đợt', 'Mã khoản', 'Gốc giải ngân (đ)', 'Lãi suất/năm', 'Ngày giải ngân', 'Ngày đáo hạn'];
export type InterestSnapshot = { loans: unknown[][]; payments: unknown[][]; disbursements: unknown[][]; config: unknown[][] };
function text(value: unknown): string {
  if (typeof value !== 'string' || !value.trim() || value.length > 200) throw new Error('INVALID_INTEREST_ROW');
  return value;
}
function money(value: unknown): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value <= 0 || value > 1e12) throw new Error('INVALID_INTEREST_ROW');
  return value;
}
function rows(source: unknown[][], headers: string[], width = headers.length) {
  if (headers.some((h, i) => source[0]?.[i] !== h)) throw new Error('INTEREST_SCHEMA_MISMATCH');
  return source.slice(1).map((row, i) => ({ row, sourceRow: i + 2 }))
    .filter(({ row }) => row.slice(0, width).some((v) => v !== '' && v !== undefined && v !== null));
}
export function parseInterestSnapshot(snapshot: InterestSnapshot): { data: InterestData; asOf: string } {
  if (snapshot.config[11]?.[1] !== INTEREST_RULE) throw new Error('INTEREST_RULE_MISMATCH');
  const asOf = sheetDate(snapshot.config[1]?.[1]);
  const loans = rows(snapshot.loans, LOAN_HEADERS).map(({ row }) => ({ id: text(row[0]), name: text(row[1]), principal: money(row[2]) }));
  const disbursements = rows(snapshot.disbursements, DISBURSEMENT_HEADERS).map(({ row }) => {
    const annualRate = row[3];
    if (typeof annualRate !== 'number' || !Number.isFinite(annualRate) || annualRate < 0 || annualRate > 1) throw new Error('INVALID_INTEREST_ROW');
    const date = sheetDate(row[4]), maturity = row[5] === '' || row[5] == null ? null : sheetDate(row[5]);
    if (maturity && maturity < date) throw new Error('INVALID_INTEREST_ROW');
    return { id: text(row[0]), loanId: text(row[1]), principal: money(row[2]), annualRate, date, maturity };
  });
  const payments = rows(snapshot.payments, PAYMENT_HEADERS, 5).map(({ row, sourceRow }) => {
    if (row[2] !== 'Thu lãi' && row[2] !== 'Thu gốc') throw new Error('INVALID_INTEREST_ROW');
    return { row: sourceRow, loanId: text(row[1]), type: row[2] as LoanPayment['type'], amount: money(row[4]), date: sheetDate(row[0]) };
  });
  if (new Set(loans.map((l) => l.id)).size !== loans.length || new Set(disbursements.map((t) => t.id)).size !== disbursements.length) throw new Error('DUPLICATE_INTEREST_ID');
  if ([...disbursements, ...payments].some((x) => !loans.some((l) => l.id === x.loanId))) throw new Error('INVALID_INTEREST_ROW');
  return { data: { loans, disbursements, payments }, asOf };
}
