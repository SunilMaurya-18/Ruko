import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import signals from '../../../shared/rules/signals.v0.json' with { type: 'json' };
import { lookup, parseProperties } from './properties.js';

const read = (lang) => parseProperties(readFileSync(new URL(`../../../shared/i18n/messages_${lang}.properties`, import.meta.url), 'utf8'));
const catalogs = { hi: read('hi'), en: read('en') };

const BANDS = ['high_concern', 'some_concern', 'few_flags_still_verify', 'not_enough_to_judge'];
const CLASSES = ['promotion', 'education', 'mixed', 'unknown'];

test('parses key=value lines, comments, escapes, and continuations', () => {
  const parsed = parseProperties('# note\n! also\na=1\nb = two words\nc:\\u0041\\n\nd=one \\\n  two\nempty=\n');
  assert.deepEqual(parsed, { a: '1', b: 'two words', c: 'A\n', d: 'one two', empty: '' });
});

test('fills placeholders and leaves unknown ones', () => {
  assert.equal(lookup({ k: 'dated {date} {other}' }, 'k', { date: '2026-09-30' }), 'dated 2026-09-30 {other}');
  assert.equal(lookup({}, 'missing'), null);
});

for (const lang of ['hi', 'en']) {
  test(`${lang}: every key the result and content screens use exists`, () => {
    const catalog = catalogs[lang];
    const keys = [
      ...BANDS.flatMap((band) => [`band.${band}`, `band.${band}.hint`]),
      ...CLASSES.map((klass) => `class.${klass}`),
      'footer.no_flags_not_safe', 'snapshot.listed', 'snapshot.not_listed',
      'how.title', 'how.intro', 'how.bands', 'how.limit.1', 'how.snapshot.none',
      'voice.action', 'voice.generic',
      ...['zero', 'one', 'other'].map((form) => `voice.count.red_flags.${form}`),
      ...['couldnt_verify', 'reassuring'].flatMap((name) => [`voice.count.${name}.one`, `voice.count.${name}.other`]),
      ...signals.analogy_triggers.map((trigger) => trigger.analogy_key),
      ...signals.signals.flatMap((signal) => [signal.reason_key, signal.card_key, signal.analogy_key].filter(Boolean)),
    ];
    for (const key of keys) {
      assert.ok(catalog[key], `${lang}: ${key}`);
    }
  });
}

test('both languages carry the same keys', () => {
  assert.deepEqual(Object.keys(catalogs.hi).sort(), Object.keys(catalogs.en).sort());
});
