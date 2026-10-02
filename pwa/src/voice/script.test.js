import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import signals from '../../../shared/rules/signals.v0.json' with { type: 'json' };
import fixture from '../../../shared/fixtures/voice-scripts.v0.json' with { type: 'json' };
import { lookup, parseProperties } from '../i18n/properties.js';
import { ACTION, GENERIC, scriptText, segments, spokenKeys } from './script.js';

const read = (lang) => parseProperties(readFileSync(new URL(`../../../shared/i18n/messages_${lang}.properties`, import.meta.url), 'utf8'));
const catalogs = { hi: read('hi'), en: read('en') };
const textFor = (lang) => (key, params) => lookup(catalogs[lang], key, params);

const counts = (red, unverified, reassuring) => ({ red_flags: red, couldnt_verify: unverified, reassuring });

function result(overrides) {
  return {
    language: 'en',
    signals: [],
    unverified: [],
    reassuring: [],
    band: 'high_concern',
    content_class: 'promotion',
    counts: counts(0, 0, 0),
    cards: [],
    analogy_key: null,
    footer_key: 'no_flags_not_safe',
    ...overrides,
  };
}

test('builds the same text as the server for every shared fixture case', () => {
  assert.ok(fixture.cases.length >= 8);
  for (const c of fixture.cases) {
    assert.equal(scriptText(textFor(c.lang), c.lang, c.script_key, c.counts), c.text, c.script_key);
  }
});

test('speaks band, class, top three flags, the unverified number, analogy, action, footer in that order', () => {
  const keys = spokenKeys(result({
    signals: ['C1', 'C2', 'S7', 'M11'].map((id) => ({ id, severity: 'critical', evidence: 'x', reason: 'r' })),
    unverified: [{ id: 'U14', item: 'INH000000001', action: 'sebi_check' }],
    analogy_key: 'analogy.guaranteed_return',
    cards: [{ signal_id: 'C1', text: 'card' }],
  }));
  assert.deepEqual(keys, ['band.high_concern', 'class.promotion', 'sig.C1.spoken', 'sig.C2.spoken', 'sig.S7.spoken',
    'sig.U14.spoken', 'analogy.guaranteed_return', ACTION, 'footer.no_flags_not_safe']);
});

test('the generic fallback notice is spoken instead of flags', () => {
  const keys = spokenKeys(result({ cards: [{ signal_id: null, text: 'generic' }] }));
  assert.deepEqual(keys, ['band.high_concern', 'class.promotion', GENERIC, ACTION, 'footer.no_flags_not_safe']);
});

test('every segment has catalogue text in the result language', () => {
  for (const lang of ['hi', 'en']) {
    const spoken = segments(result({
      language: lang,
      band: 'some_concern',
      counts: counts(2, 1, 1),
      signals: [{ id: 'C3', severity: 'critical', evidence: 'anydesk', reason: 'r' }],
      analogy_key: 'analogy.remote_access',
    }), textFor(lang));
    assert.equal(spoken.length, 6);
    for (const segment of spoken) {
      assert.ok(segment.text && !segment.text.includes('{'), `${lang} ${segment.key}`);
    }
  }
});

test('a dated snapshot line the phone cannot fill falls back to the server reason', () => {
  const [, , s19] = segments(result({
    signals: [{ id: 'S19', severity: 'strong', evidence: 'INH000000002', reason: 'Not in the list dated 2026-09-30.' }],
  }), textFor('en'));
  assert.deepEqual(s19, { key: 'sig.S19.spoken', text: 'Not in the list dated 2026-09-30.' });
});

test('every signal is spoken through sig.<id>.spoken, which exists in both languages', () => {
  for (const signal of signals.signals) {
    assert.equal(signal.spoken_key, `sig.${signal.id}.spoken`);
    for (const lang of ['hi', 'en']) assert.ok(catalogs[lang][signal.spoken_key], `${lang} ${signal.spoken_key}`);
  }
});
