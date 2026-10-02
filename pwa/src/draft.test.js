import assert from 'node:assert/strict';
import test from 'node:test';

const store = new Map([['ruko.lang', 'en']]);
globalThis.localStorage = {
  getItem: (key) => store.get(key) ?? null,
  setItem: (key, value) => store.set(key, String(value)),
};
const { getDraft, setLang, updateDraft } = await import('./draft.js');

test('the saved language is the starting language', () => {
  assert.equal(getDraft().lang, 'en');
});

test('setLang saves only the language, never the message', () => {
  updateDraft({ text: 'send ₹5000 to 9876543210' });
  setLang('hi');
  assert.equal(getDraft().lang, 'hi');
  assert.deepEqual([...store.entries()], [['ruko.lang', 'hi']]);
});

test('setLang ignores languages the app does not have', () => {
  setLang('fr');
  assert.equal(getDraft().lang, 'hi');
  assert.equal(store.get('ruko.lang'), 'hi');
});
