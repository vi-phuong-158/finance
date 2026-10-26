import test from 'node:test';
import assert from 'node:assert/strict';
import { auditSchema, planAuditMigration, prepareAudit } from '../lib/audit-migration.js';
import type { AuditState } from '../lib/audit-migration.js';

test('audit preparation is additive, dry-run by default, backed up, idempotent and empty-only rollback', async () => {
  for (const scope of ['transactions', 'loans'] as const) {
    const schema = auditSchema(scope), order: string[] = [];
    let state: AuditState = { tables: [], existingIds: [10, 11] };
    const port = { read: async () => structuredClone(state), backup: async () => { order.push('backup'); },
      write: async (requests: Record<string, unknown>[]) => {
        order.push('write');
        if (requests[0].deleteSheet) { state = { tables: [], existingIds: [10, 11] }; return; }
        assert.equal(requests.length, 2);
        state = { tables: [{ id: 12, title: schema.title, columns: schema.headers.length + 1, rows: [[...schema.headers]], hasMarkers: false }], existingIds: [10, 11, 12] };
      } };
    assert.equal((await prepareAudit(port, scope)).status, 'CHANGES_REQUIRED');
    assert.equal(order.length, 0);
    assert.equal((await prepareAudit(port, scope, { apply: true })).status, 'PREPARED');
    assert.deepEqual(order, ['backup', 'write']);
    assert.equal((await prepareAudit(port, scope, { apply: true })).status, 'ALREADY_READY');
    assert.equal((await prepareAudit(port, scope, { apply: true, rollback: true })).status, 'ROLLED_BACK');
    assert.deepEqual(state.existingIds, [10, 11]);
  }
});

test('audit migration rejects drift, missing backups and rollback when audit data or markers exist', async () => {
  const schema = auditSchema('loans');
  const table = { id: 10, title: schema.title, columns: 8, rows: [schema.headers], hasMarkers: false };
  assert.throws(() => planAuditMigration({ tables: [{ ...table, rows: [['unknown']] }], existingIds: [10] }, 'loans'), /SCHEMA/);
  for (const unsafe of [{ ...table, hasMarkers: true }, { ...table, rows: [schema.headers, ['synthetic event']] }]) {
    assert.throws(() => planAuditMigration({ tables: [unsafe], existingIds: [10] }, 'loans', true), /HAS_DATA/);
  }
  let reads = 0, writes = 0;
  await assert.rejects(() => prepareAudit({ read: async () => ({ tables: [], existingIds: reads++ ? [10, 11] : [10] }),
    backup: async () => {}, write: async () => { writes++; } }, 'loans', { apply: true }), /SOURCE_CHANGED/);
  assert.equal(writes, 0);
  await assert.rejects(() => prepareAudit({ read: async () => ({ tables: [], existingIds: [10] }),
    backup: async () => { throw Error('backup denied'); }, write: async () => { writes++; } }, 'loans', { apply: true }), /backup denied/);
  assert.equal(writes, 0);
});
