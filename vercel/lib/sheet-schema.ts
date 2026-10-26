export const TRANSACTION_HEADERS = ['time', 'bank', 'type', 'amount', 'balance', 'counterparty', 'description', 'category', 'event_id'];
export const RAW_CAPTURE_HEADERS = ['time', 'bank', 'raw_text', 'event_id'];
export const TRANSACTION_HEADERS_V2 = [...TRANSACTION_HEADERS, 'source', 'account'];
export function hasTransactionHeaders(actual: unknown[]): boolean {
  return hasExpectedHeaders(actual, TRANSACTION_HEADERS) || hasExpectedHeaders(actual, TRANSACTION_HEADERS_V2);
}
export const SHEET_SCHEMAS = { Transactions: TRANSACTION_HEADERS, Raw_Capture: RAW_CAPTURE_HEADERS };

export function hasExpectedHeaders(actual: unknown[], expected: string[]): boolean {
  return actual.length === expected.length && expected.every((header, index) => actual[index] === header);
}
