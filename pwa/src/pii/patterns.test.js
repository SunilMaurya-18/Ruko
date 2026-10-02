import assert from 'node:assert/strict';
import { test } from 'node:test';
import entities from '../../../shared/patterns/entities.v0.json' with { type: 'json' };
import pii from '../../../shared/patterns/pii.v0.json' with { type: 'json' };
import signals from '../../../shared/rules/signals.v0.json' with { type: 'json' };

const conditionPatterns = (owner, conditions = []) =>
  conditions.flatMap((condition, c) => (condition.patterns ?? []).map((p, i) => [`${owner}.with[${c}][${i}]`, { pattern: p }]));

const listed = (owner, patterns = []) => patterns.map((p, i) => [`${owner}[${i}]`, { pattern: p }]);

const signalPatterns = [
  ...Object.entries(signals.sets).flatMap(([name, set]) => listed(`set ${name}`, set.patterns)),
  ...Object.entries(signals.markers).flatMap(([name, marker]) => [
    ...listed(`marker ${name}`, marker.patterns),
    ...conditionPatterns(`marker ${name}`, marker.with),
  ]),
  ...signals.analogy_triggers.flatMap((trigger) => listed(trigger.analogy_key, trigger.patterns)),
  ...signals.signals.flatMap((signal) => [
    ...listed(signal.id, signal.patterns),
    ...conditionPatterns(signal.id, signal.with),
    ...(signal.match ? [[`${signal.id}.match`, { pattern: signal.match }]] : []),
    ...(signal.exclude ? [[`${signal.id}.exclude`, { pattern: signal.exclude }]] : []),
  ]),
];

// The server compiles the same files with java.util.regex; this keeps them inside the common subset.
const all = [
  ...pii.rules.map((rule) => [`pii ${rule.id}`, rule]),
  ...entities.types.flatMap((type) => type.patterns.map((pattern, i) => [`${type.id}[${i}]`, pattern])),
  ...signalPatterns,
];

for (const [name, { pattern, flags = '' }] of all) {
  test(`compiles in JavaScript: ${name}`, () => {
    assert.match(flags, /^i?$/);
    assert.doesNotThrow(() => new RegExp(pattern.normalize('NFKC'), `gu${flags}`));
  });
}

const escape = (text) => text.replace(/[.*+?^${}()|[\]\\/]/g, '\\$&');

// Mirrors SpanMatcher: whole words, a trailing * allows any word ending.
function termRegex(terms) {
  const alternatives = terms.map((term) => {
    const folded = term.normalize('NFKC').toLowerCase();
    return folded.endsWith('*') ? escape(folded.slice(0, -1)) : `${escape(folded)}(?![\\p{L}\\p{M}\\p{N}])`;
  });
  return new RegExp(`(?<![\\p{L}\\p{M}\\p{N}])(?:${alternatives.join('|')})`, 'gu');
}

const termLists = [
  ...Object.entries(signals.sets).map(([name, set]) => [`set ${name}`, set.terms]),
  ...signals.analogy_triggers.map((trigger) => [trigger.analogy_key, trigger.terms]),
  ...signals.signals.map((signal) => [signal.id, signal.terms]),
].filter(([, terms]) => terms);

for (const [name, terms] of termLists) {
  test(`terms match whole words in JavaScript: ${name}`, () => {
    const regex = termRegex(terms);
    for (const term of terms.filter((t) => !t.endsWith('*'))) {
      const folded = term.normalize('NFKC').toLowerCase();
      assert.ok(`x ${folded} x`.match(regex), `${name}: "${term}" should match on its own`);
    }
  });
}

test('the catalogue has the ids the result screen expects', () => {
  const ids = signals.signals.map((signal) => signal.id);
  for (const id of ['C1', 'C2', 'C3', 'C4', 'C15', 'C16', 'C17', 'S5', 'S6', 'S7', 'S8', 'S9', 'S10', 'S19',
    'M11', 'M12', 'M13', 'U14', 'R1', 'R2']) {
    assert.ok(ids.includes(id), id);
  }
});
