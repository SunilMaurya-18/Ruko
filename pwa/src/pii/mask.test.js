import assert from 'node:assert/strict';
import { test } from 'node:test';
import cases from '../../../shared/fixtures/pii-cases.v0.json' with { type: 'json' };
import { maskPii } from './mask.js';

for (const { input, masked } of cases) {
  test(`masks like the server: ${input}`, () => {
    assert.equal(maskPii(input), masked);
  });
}

test('upper-cases placeholders that were lower-cased elsewhere', () => {
  assert.equal(maskPii('seen [phone] before'), 'seen [PHONE] before');
});
