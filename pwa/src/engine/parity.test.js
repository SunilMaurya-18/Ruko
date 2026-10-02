import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import fixtures from '../../../shared/fixtures/fixtures.v0.json' with { type: 'json' };
import golden from '../../../shared/fixtures/engine-golden.v0.json' with { type: 'json' };
import probes from '../../../shared/fixtures/engine-probes.v0.json' with { type: 'json' };
import signals from '../../../shared/rules/signals.v0.json' with { type: 'json' };
import shippedSnapshot from '../../../shared/snapshot/sebi-intermediaries.json' with { type: 'json' };
import { lookup, parseProperties } from '../i18n/properties.js';
import { maskPii } from '../pii/mask.js';
import { analyzeOnDevice } from './analyze.js';
import { snapshotIndex } from './signals.js';

// The server half (OnDeviceParityTest) runs the same fixtures through the Java engine and keeps
// engine-golden.v0.json equal to its full response. Here the on-device engine must reproduce it exactly.
const catalog = Object.fromEntries(['hi', 'en'].map((lang) => [lang, parseProperties(
  readFileSync(new URL(`../../../shared/i18n/messages_${lang}.properties`, import.meta.url), 'utf8'))]));
const i18n = (lang, key, params) => lookup(catalog[lang], key, params);
const snapshot = snapshotIndex(shippedSnapshot);
// Same as Pipeline.datedSnapshot() on the server.
const datedSnapshot = snapshotIndex({ snapshot_date: '2026-09-30', registration_numbers: ['INH000000001'] });
const llmOnly = new Set(signals.signals.filter((signal) => signal.type === 'LLM_TAG').map((signal) => signal.id));
const serverById = new Map(golden.map((entry) => [entry.id, entry.response]));

const run = (fixture, text) => analyzeOnDevice(
  { text, lang: fixture.lang, source: fixture.source, ocrConfidence: fixture.ocr_confidence ?? null },
  { i18n, snapshot });

const ids = (response) => [...response.signals, ...response.unverified, ...response.reassuring].map((s) => s.id).sort();

test('fixtures only name signals from the catalogue', () => {
  const known = new Set(signals.signals.map((signal) => signal.id));
  for (const fixture of fixtures) {
    for (const id of fixture.expected.signals) {
      assert.ok(known.has(id), `${fixture.id}: unknown signal ${id}`);
    }
  }
});

test('the golden file covers every fixture and probe', () => {
  assert.deepEqual(golden.map((entry) => entry.id), [...fixtures, ...probes].map((item) => item.id));
});

for (const probe of probes) {
  test(`on-device parity probe ${probe.id}`, () => {
    const options = { i18n, snapshot: probe.snapshot === 'dated' ? datedSnapshot : snapshot };
    const request = { text: maskPii(probe.text), lang: probe.lang, source: probe.source, ocrConfidence: probe.ocr_confidence ?? null };
    const response = analyzeOnDevice(request, options);
    const server = serverById.get(probe.id);
    assert.deepEqual({ ...response, engine: server.engine }, server);
  });
}

for (const fixture of fixtures) {
  const expected = fixture.expected.signals.filter((id) => !llmOnly.has(id));
  test(`on-device parity ${fixture.id}: ${expected.join(',') || 'none'} / ${fixture.expected.band}`, () => {
    // What the phone runs on: the text after the client masker, exactly as it would have been sent.
    const response = run(fixture, maskPii(fixture.text));

    const got = [response.band, response.content_class, ids(response)];
    const want = [fixture.expected.band, fixture.expected.content_class, [...expected].sort()];
    if (fixture.known_gap) {
      assert.notDeepEqual(got, want, `${fixture.id} matches its labels now: remove its known_gap note`);
    } else {
      assert.deepEqual(got, want);
    }
    assert.equal(response.engine, 'on_device');
    assert.equal(response.footer_key, 'no_flags_not_safe');

    const server = serverById.get(fixture.id);
    assert.deepEqual({ ...response, engine: server.engine }, server, 'same response as the server engine');
    assert.deepEqual(run(fixture, fixture.text), response, 'masking first does not change the result');
  });
}
