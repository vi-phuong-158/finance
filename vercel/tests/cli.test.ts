import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

test('bootstrap and deployment-check entrypoints fail closed without leaking invalid configuration', () => {
  for (const script of ['bootstrap-sheets.ts', 'check-deployment.ts']) {
    const result = spawnSync(process.execPath, ['--import', 'tsx', `scripts/${script}`], {
      cwd: new URL('..', import.meta.url), encoding: 'utf8',
      env: { ...process.env, SPREADSHEET_ID: '', WEBHOOK_SECRET: '', GOOGLE_CLIENT_EMAIL: '', GOOGLE_PRIVATE_KEY: 'sensitive-invalid-marker', WEBHOOK_URL: 'http://invalid.example.invalid/api/transaction' },
    });
    assert.equal(result.status, 1);
    assert.equal(result.stdout, '');
    assert.ok(['SERVER_CONFIGURATION_ERROR', 'DEPLOYMENT_CHECK_FAILED'].includes(result.stderr.trim()), result.stderr);
    assert.equal(result.stderr.includes('sensitive-invalid-marker'), false);
  }
});

test('transaction migration CLI rejects invalid configuration without displaying secrets or writing data', () => {
  const result = spawnSync(process.execPath, ['--import', 'tsx', 'scripts/migrate-transactions.ts'], {
    cwd: new URL('..', import.meta.url), encoding: 'utf8',
    env: { ...process.env, SPREADSHEET_ID: '', GOOGLE_CLIENT_EMAIL: '', GOOGLE_PRIVATE_KEY: 'private-invalid-marker' },
  });
  assert.equal(result.status, 1);
  assert.equal(result.stdout, '');
  assert.ok(result.stderr.includes('MIGRATION_BLOCKED'));
  assert.equal(result.stderr.includes('private-invalid-marker'), false);
});

test('loan import and audit preparation CLIs are read-only or fail closed before configuration/network', () => {
  for (const [script, args] of [['import-loans.ts', ['--apply']], ['prepare-finance-audit.ts', []]] as [string, string[]][]) {
    const result = spawnSync(process.execPath, ['--import', 'tsx', `scripts/${script}`, ...args], {
      cwd: new URL('..', import.meta.url), encoding: 'utf8', env: { ...process.env, GOOGLE_PRIVATE_KEY: 'private-invalid-marker' },
    });
    assert.equal(result.status, 1);
    assert.equal(result.stdout, '');
    assert.equal(result.stderr.includes('private-invalid-marker'), false);
  }
});
