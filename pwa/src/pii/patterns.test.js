import assert from 'node:assert/strict';
import { test } from 'node:test';
import entities from '../../../shared/patterns/entities.v0.json' with { type: 'json' };
import pii from '../../../shared/patterns/pii.v0.json' with { type: 'json' };

// The server compiles the same files with java.util.regex; this keeps them inside the common subset.
const all = [
  ...pii.rules.map((rule) => [`pii ${rule.id}`, rule]),
  ...entities.types.flatMap((type) => type.patterns.map((pattern, i) => [`${type.id}[${i}]`, pattern])),
];

for (const [name, { pattern, flags = '' }] of all) {
  test(`compiles in JavaScript: ${name}`, () => {
    assert.match(flags, /^i?$/);
    assert.doesNotThrow(() => new RegExp(pattern.normalize('NFKC'), `gu${flags}`));
  });
}
