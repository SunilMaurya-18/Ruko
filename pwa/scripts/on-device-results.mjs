// Prints the on-device engine's response for every labelled fixture, one JSON object per line:
// {"id": "...", "response": {...}}. The server's eval report runs this to measure server/on-device agreement.
// Usage: node scripts/on-device-results.mjs
import fixtures from '../../shared/fixtures/fixtures.v0.json' with { type: 'json' };
import { maskPii } from '../src/pii/mask.js';
import { analyzeInNode } from './node-engine.mjs';

for (const fixture of fixtures) {
  const response = analyzeInNode({
    text: maskPii(fixture.text), lang: fixture.lang, source: fixture.source,
    ocrConfidence: fixture.ocr_confidence ?? null,
  });
  process.stdout.write(`${JSON.stringify({ id: fixture.id, response })}\n`);
}
