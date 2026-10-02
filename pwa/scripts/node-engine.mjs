// The on-device engine set up for Node (catalogue read from shared/i18n, shipped snapshot). Used by the eval
// scripts and the browser performance test, which stands it in for the server: parity tests keep its output
// identical to the server's.
import { readFileSync } from 'node:fs';
import shippedSnapshot from '../../shared/snapshot/sebi-intermediaries.json' with { type: 'json' };
import { analyzeOnDevice } from '../src/engine/analyze.js';
import { snapshotIndex } from '../src/engine/signals.js';
import { lookup, parseProperties } from '../src/i18n/properties.js';

const catalog = Object.fromEntries(['hi', 'en'].map((lang) => [lang, parseProperties(
  readFileSync(new URL(`../../shared/i18n/messages_${lang}.properties`, import.meta.url), 'utf8'))]));
const i18n = (lang, key, params) => lookup(catalog[lang], key, params);
const snapshot = snapshotIndex(shippedSnapshot);

/** `{text, lang, source, ocrConfidence}` (text already masked) to the full AnalyzeResponse. */
export function analyzeInNode(request) {
  return analyzeOnDevice(request, { i18n, snapshot });
}
