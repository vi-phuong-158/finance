// Protect explicit currency values and date/time tokens before looking for long
// digit runs. Never apply this to typed amount/balance or the event_id column.
const VALUE_TOKENS = /(?<![\p{L}\p{N}])(?:[+-]?\d+(?:[., \u00a0]\d{3})*\s*(?:VND|VNĐ|₫|đ|dong)(?!\p{L})|[+-]\s*\d+(?:[.,]\d{3})*|(?:\d{2}[-/]\d{2}[-/]\d{4}|\d{4}[-/]\d{2}[-/]\d{2})(?!\d)|\d{2}:\d{2}(?::\d{2})?(?!\d))/giu;
const IDENTIFIER = /(?<![\p{L}\p{N}])\d(?:[\d \u00a0.,/_-]*\d)?(?![\p{L}\p{N}])/gu;

function maskIdentifiers(value: string): string {
  return value.replace(IDENTIFIER, (candidate) =>
    candidate.replace(/\D/g, '').length >= 8 ? '***' : candidate)
    // Runs glued to letters (virtual accounts "XY00Z00000000000001", refs "#SP#0000…abc") escape the
    // standalone pass above; mask only their digits so surrounding text and short codes stay readable.
    .replace(/\d{8,}/g, '***');
}

export function maskSensitiveIdentifiers(text: string): string {
  let cursor = 0;
  let safe = '';
  for (const token of text.matchAll(VALUE_TOKENS)) {
    safe += maskIdentifiers(text.slice(cursor, token.index)) + token[0];
    cursor = token.index + token[0].length;
  }
  return safe + maskIdentifiers(text.slice(cursor));
}
