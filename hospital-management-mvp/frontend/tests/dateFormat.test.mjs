import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { formatDate } from '../src/dateFormat.mjs';

test('missing or invalid API dates never throw during rendering', () => {
  for (const value of [undefined, null, '', ' ', 'undefined', 'null', 'invalid-date', NaN, Infinity, {}, [], new Date(NaN)]) {
    assert.equal(formatDate(value), '—');
    assert.equal(formatDate(value, true), '—');
  }
});

test('account and department rows cannot crash the notification date renderer', () => {
  // These are the row shapes retained during the old workspace mode transition.
  for (const row of [{ id: 1, name: 'Cont fictiv', active: true }, { id: 2, name: 'Departament fictiv' }]) {
    assert.equal(formatDate(String(row.created_at), true), '—');
  }
});

test('valid notification and working-period dates remain readable', () => {
  assert.notEqual(formatDate('2026-11-11T09:30:00', true), '—');
  assert.notEqual(formatDate('2026-11-11T09:30:00Z', true), '—');
  assert.notEqual(formatDate(0, true), '—');
});

test('invalid calendar days are rejected while leap days are retained', () => {
  assert.equal(formatDate('2026-02-30'), '—');
  assert.equal(formatDate('2025-02-29'), '—');
  assert.notEqual(formatDate('2024-02-29'), '—');
});

test('calendar dates do not shift to the preceding day in western timezones', () => {
  const moduleUrl = new URL('../src/dateFormat.mjs', import.meta.url).href;
  for (const zone of ['UTC', 'Europe/Bucharest', 'America/Los_Angeles']) {
    const code = `import { formatDate } from ${JSON.stringify(moduleUrl)}; process.stdout.write(formatDate('1994-04-12'));`;
    const result = spawnSync(process.execPath, ['--input-type=module', '-e', code], {
      encoding: 'utf8', env: { ...process.env, TZ: zone },
    });
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /^12 /, zone);
  }
});
