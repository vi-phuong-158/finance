import assert from 'node:assert/strict';
import test from 'node:test';
import { formulaSeparator, localizeFormula } from '../lib/sheet-formula-locale.js';

test('known Sheets locales choose explicit separators without changing quoted business text or sheet names', () => {
  const formula = '=IF(A1="a,b",SUM(1,2),\'A,B\'!C1)';
  assert.equal(localizeFormula(formula, formulaSeparator('vi_VN')), '=IF(A1="a,b";SUM(1;2);\'A,B\'!C1)');
  assert.equal(localizeFormula(formula, formulaSeparator('en_US')), formula);
  assert.throws(() => formulaSeparator('unknown'), /LOCALE_UNSUPPORTED/);
});
