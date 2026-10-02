import assert from 'node:assert/strict';
import { test } from 'node:test';
import { MAX_CHARS, OnDeviceRejected, analyzeOnDevice } from './analyze.js';
import { normalize, rejectReason } from './normalize.js';
import { snapshotIndex } from './signals.js';

// Mirrors InputGuardTest: the phone refuses exactly what the server would answer 400/413 to.
test('input guard reasons match the server', () => {
  assert.equal(rejectReason('a'.repeat(MAX_CHARS), MAX_CHARS), null);
  assert.equal(rejectReason('a'.repeat(MAX_CHARS + 1), MAX_CHARS), 'too_long');
  assert.equal(rejectReason('😀'.repeat(MAX_CHARS), MAX_CHARS), null, 'counts code points, not UTF-16 units');
  assert.equal(rejectReason(' \n\t ', MAX_CHARS), 'empty');
  assert.equal(rejectReason('abc\u0000def', MAX_CHARS), 'binary');
  assert.equal(rejectReason(`${'\u0001'.repeat(10)}${'a'.repeat(100)}`, MAX_CHARS), 'binary');
  assert.equal(rejectReason('abc\uD800def', MAX_CHARS), 'invalid_encoding');
  assert.equal(rejectReason('abc\uFFFDdef', MAX_CHARS), 'invalid_encoding');
});

test('a rejected input throws with the reason', () => {
  const run = () => analyzeOnDevice({ text: '   ', lang: 'hi', source: 'paste' },
    { i18n: () => '', snapshot: snapshotIndex(null) });
  assert.throws(run, (e) => e instanceof OnDeviceRejected && e.reason === 'empty');
});

test('normalised text keeps a map back to the original', () => {
  const text = normalize('G u\u200B a r a n t e e d ５%');
  assert.equal(text.value, 'guaranteed 5%');
  assert.equal(text.originalSlice(0, 10), 'G u\u200B a r a n t e e d');
  assert.equal(text.originalSlice(11, 12), '５');
});

test('an undated or empty snapshot never looks anything up', () => {
  assert.equal(snapshotIndex({ snapshot_date: null, registration_numbers: [] }).lookup('INH000000001'), null);
  assert.equal(snapshotIndex({ snapshot_date: '2026-09-30', registration_numbers: [] }).lookup('INH000000001'), null);
  const dated = snapshotIndex({ snapshot_date: '2026-09-30', registration_numbers: ['inh000000001'] });
  assert.equal(dated.lookup('INH000000001'), 'listed');
  assert.equal(dated.lookup('INH000000002'), 'not_listed');
});
