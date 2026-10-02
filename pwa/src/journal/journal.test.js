import assert from 'node:assert/strict';
import { test } from 'node:test';
import { KEY, WAIT_MS, clearJournal, loadJournal, saveJournal, startWait, waitState } from './journal.js';

function memoryStorage() {
  const data = new Map();
  return {
    getItem: (key) => (data.has(key) ? data.get(key) : null),
    setItem: (key, value) => data.set(key, String(value)),
    removeItem: (key) => data.delete(key),
    data,
  };
}

test('the journal round-trips through localStorage only', () => {
  const storage = memoryStorage();
  saveJournal({ why: 'my sister said so', horizon: 'months', afford: 'no' }, storage);
  assert.deepEqual([...storage.data.keys()], [KEY]);
  assert.deepEqual(loadJournal(storage), { why: 'my sister said so', horizon: 'months', afford: 'no', wait_until: null, notified: false });
  clearJournal(storage);
  assert.equal(storage.data.size, 0);
});

test('unknown or corrupt saved values are dropped', () => {
  const storage = memoryStorage();
  storage.setItem(KEY, JSON.stringify({ why: 7, horizon: 'forever', afford: 'maybe', wait_until: 'soon', extra: 'x' }));
  assert.deepEqual(loadJournal(storage), { why: '', horizon: '', afford: '', wait_until: null, notified: false });
  storage.setItem(KEY, '{not json');
  assert.equal(loadJournal(storage).why, '');
});

test('waiting 24 hours stores a local timestamp', () => {
  const storage = memoryStorage();
  const now = Date.UTC(2026, 9, 2, 10, 0);
  const entry = startWait(now, storage);
  assert.equal(entry.wait_until, now + WAIT_MS);
  assert.equal(waitState(entry, now + 1000).status, 'waiting');
  assert.equal(waitState(entry, now + WAIT_MS).status, 'over');
  assert.equal(waitState({ wait_until: null }, now).status, 'none');
});
