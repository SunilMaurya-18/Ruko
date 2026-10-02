import assert from 'node:assert/strict';
import test from 'node:test';
import { LANG_OPTIONS, ui } from './labels.js';

const shape = (value) => (typeof value === 'object' && value !== null
  ? Object.fromEntries(Object.entries(value).map(([key, inner]) => [key, shape(inner)]))
  : typeof value);

test('Hindi and English screen wording have the same keys and kinds', () => {
  assert.deepEqual(shape(ui('en')), shape(ui('hi')));
});

test('every label is filled in both languages', () => {
  const walk = (value, path) => {
    if (typeof value === 'string') assert.ok(value.trim(), path);
    else if (typeof value === 'function') assert.ok(value(1).trim(), path);
    else Object.entries(value).forEach(([key, inner]) => walk(inner, `${path}.${key}`));
  };
  for (const [lang] of LANG_OPTIONS) walk(ui(lang), lang);
});

test('unknown languages fall back to Hindi', () => {
  assert.equal(ui('fr'), ui('hi'));
});
