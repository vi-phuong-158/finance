export function formulaSeparator(locale: string): ',' | ';' {
  if (/^en_[A-Z]{2}$/.test(locale)) return ',';
  if (locale === 'vi_VN') return ';';
  throw new Error('LOAN_LOCALE_UNSUPPORTED');
}

export function localizeFormula(formula: string, separator: ',' | ';'): string {
  let result = '', quote: string | null = null;
  for (let index = 0; index < formula.length; index++) {
    const ch = formula[index];
    if (quote && ch === quote && formula[index + 1] === quote) { result += ch + ch; index++; continue; }
    if ((ch === '"' || ch === "'") && (!quote || quote === ch)) quote = quote ? null : ch;
    result += ch === ',' && !quote ? separator : ch;
  }
  return result;
}
