import { createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { resolve, relative, isAbsolute } from 'node:path';
import { readGoogleConfiguration } from '../lib/configuration.js';
import { makeGoogleInterestReadPort } from '../lib/google-interest-read.js';
import { planLoanImport } from '../lib/loan-import.js';

async function main() {
  const args = process.argv.slice(2), options: Record<string, string> = {};
  for (let i = 0; i < args.length; i++) {
    if (!['--output', '--previous'].includes(args[i]) || !args[i + 1] || args[i + 1].startsWith('--') || options[args[i]]) throw Error('INVALID_ARGUMENT');
    options[args[i]] = resolve(args[++i]);
  }
  for (const path of Object.values(options)) {
    const location = relative(resolve(import.meta.dirname, '../..'), path);
    if (!location.startsWith('../') && !location.startsWith('..\\') && !isAbsolute(location)) throw Error('PRIVATE_MANIFEST_MUST_BE_OUTSIDE_REPOSITORY');
  }
  const config = readGoogleConfiguration({ ...process.env, SPREADSHEET_ID: process.env.INTEREST_SPREADSHEET_ID });
  if (!config) throw Error('INTEREST_CONFIGURATION_REQUIRED');
  const reader = await makeGoogleInterestReadPort(config);
  const previous = options['--previous'] ? JSON.parse(await readFile(options['--previous'], 'utf8')) : undefined;
  const plan = planLoanImport(await reader.readInterest(), createHash('sha256').update(config.spreadsheetId).digest('hex'), previous);
  if (options['--output']) await writeFile(options['--output'], JSON.stringify(plan), { flag: 'wx', mode: 0o600 });
  console.log(JSON.stringify({ status: previous?.fingerprint === plan.fingerprint ? 'ALREADY_RECONCILED' : 'DRY_RUN_READY',
    loans: plan.loans.length, events: plan.events.length, unresolved: plan.unresolved.length, writesToSheets: 0 }));
}
main().catch(() => { console.error('LOAN_IMPORT_BLOCKED: verify source configuration, provenance, unchanged history and private manifest paths.'); process.exitCode = 1; });
