import assert from 'node:assert/strict';
import { test } from 'node:test';
import fixtures from '../../../shared/fixtures/fixtures.v0.json' with { type: 'json' };
import signals from '../../../shared/rules/signals.v0.json' with { type: 'json' };

// The server half (OnDeviceParityTest) runs these fixtures through the Java engine. The on-device engine
// arrives in Phase 5; until then each case is reported as pending so the gap stays visible.
const llmOnly = new Set(signals.signals.filter((signal) => signal.type === 'LLM_TAG').map((signal) => signal.id));

test('fixtures only name signals from the catalogue', () => {
  const ids = new Set(signals.signals.map((signal) => signal.id));
  for (const fixture of fixtures) {
    for (const id of fixture.expected.signals) {
      assert.ok(ids.has(id), `${fixture.id}: unknown signal ${id}`);
    }
  }
});

for (const fixture of fixtures) {
  const expected = fixture.expected.signals.filter((id) => !llmOnly.has(id));
  test(`on-device parity ${fixture.id}: ${expected.join(',') || 'none'} / ${fixture.expected.band}`, {
    todo: 'on-device engine lands in Phase 5',
  });
}
