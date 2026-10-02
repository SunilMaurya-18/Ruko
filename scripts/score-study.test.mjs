import assert from 'node:assert/strict';
import { test } from 'node:test';
import fixtures from '../shared/fixtures/fixtures.v0.json' with { type: 'json' };
import { COLUMNS, SET_A, SET_B, parseSheet, score } from './score-study.mjs';

const header = COLUMNS.join(',');
const kind = (id) => fixtures.find((f) => f.id === id).kind;
const right = (id) => (kind(id) === 'scam' ? 'risky' : 'fine');

/** One participant's ten rows; `wrong` lists message ids answered wrongly, `notRestated` with-Ruko ids not restated. */
function participant(code, group, { wrong = [], notRestated = [] } = {}) {
  const [withoutSet, withSet] = group === 'A' ? [SET_A, SET_B] : [SET_B, SET_A];
  const answer = (id) => (wrong.includes(id) ? (right(id) === 'risky' ? 'fine' : 'risky') : right(id));
  return [
    ...withoutSet.map((id) => `${code},${group},${id},without,${answer(id)},`),
    ...withSet.map((id) => `${code},${group},${id},with,${answer(id)},${notRestated.includes(id) ? 'no' : 'yes'}`),
  ];
}

test('the study messages exist and mix scams with genuine messages in both sets', () => {
  for (const set of [SET_A, SET_B]) {
    assert.equal(set.length, 5);
    assert.deepEqual(set.map(kind).sort(), ['education', 'education', 'scam', 'scam', 'scam']);
  }
});

test('scores correct identification with and without Ruko, and restatement', () => {
  const csv = [header,
    ...participant('P01', 'A', { wrong: ['sc-001', 'sc-005', 'ed-012'] }),
    ...participant('P02', 'B', { wrong: ['sc-002', 'ed-008'], notRestated: ['sc-020'] }),
  ].join('\n');
  const result = score(parseSheet(csv));
  assert.equal(result.n, 2);
  assert.deepEqual(result.groups, { A: 1, B: 1 });
  assert.equal(result.without, 50);
  assert.equal(result.with, 100);
  assert.equal(result.improvement, 50);
  assert.equal(result.restated, 90);
  assert.ok(result.improvementMet && result.restatedMet);
});

test('incomplete participants are left out and counted', () => {
  const csv = [header, ...participant('P01', 'A'), ...participant('P02', 'B').slice(0, 7)].join('\n');
  const result = score(parseSheet(csv));
  assert.equal(result.n, 1);
  assert.equal(result.incomplete, 1);
});

test('unsure counts as not correctly identified', () => {
  const rows = participant('P01', 'A').map((line) => line.replace(',without,risky,', ',without,unsure,'));
  assert.equal(score(parseSheet([header, ...rows].join('\n'))).without, 40);
});

test('the sheet refuses names, free text, and inconsistent rows', () => {
  const ok = participant('P01', 'A');
  assert.throws(() => parseSheet(['name,group,message_id,condition,answer,restated', ...ok].join('\n')), /first line/);
  assert.throws(() => parseSheet([header, ok[0].replace('P01', 'Ramesh')].join('\n')), /participant/);
  assert.throws(() => parseSheet([header, `${ok[5]},he said it was fake`].join('\n')), /columns/);
  assert.throws(() => parseSheet([header, ok[0].replace(',without,', ',with,')].join('\n')), /sees/);
  assert.throws(() => parseSheet([header, ok[0], ok[0]].join('\n')), /twice/);
  assert.throws(() => parseSheet([header, ok[5].replace(/yes$/, '')].join('\n')), /restated/);
});
