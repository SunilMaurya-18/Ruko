import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

// WCAG 2.2 AA in both colour schemes: 4.5:1 for text, 3:1 for borders, stripes, and the focus ring.
const css = readFileSync(new URL('./styles.css', import.meta.url), 'utf8');

const TEXT = [
  ['ink', 'bg'], ['ink', 'surface'], ['ink', 'surface-2'],
  ['muted', 'bg'], ['muted', 'surface'], ['muted', 'surface-2'],
  ['on-accent', 'accent'], ['on-accent', 'accent-strong'],
  ['accent', 'surface'], ['accent', 'bg'], ['accent', 'accent-soft'],
  ['on-header', 'header-bg'],
  ['ink', 'danger-bg'], ['ink', 'warn-bg'], ['ink', 'info-bg'],
  ['muted', 'danger-bg'], ['muted', 'warn-bg'], ['muted', 'info-bg'],
];

const NON_TEXT = [
  ['field-border', 'surface'], ['focus', 'surface'], ['focus', 'bg'],
  ['accent', 'danger-bg'], ['warn', 'warn-bg'], ['warn', 'surface'], ['info', 'info-bg'], ['info', 'surface'],
  ['ok', 'surface'], ['muted', 'surface'],
];

function tokens(block) {
  return Object.fromEntries([...block.matchAll(/--([\w-]+):\s*(#[0-9a-f]{6})\s*;/gi)].map(([, name, hex]) => [name, hex]));
}

function rootBlock(source) {
  const start = source.indexOf(':root {');
  assert.ok(start >= 0, 'a :root block');
  return source.slice(start, source.indexOf('}', start));
}

const light = tokens(rootBlock(css));
const darkStart = css.indexOf('@media (prefers-color-scheme: dark)');
assert.ok(darkStart >= 0, 'a dark colour scheme');
const dark = { ...light, ...tokens(rootBlock(css.slice(darkStart))) };

const luminance = (hex) => {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map((c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};

const ratio = (a, b) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

for (const [scheme, palette] of [['light', light], ['dark', dark]]) {
  test(`${scheme}: text colours reach 4.5:1`, () => {
    for (const [fg, bg] of TEXT) {
      assert.ok(palette[fg] && palette[bg], `${scheme}: --${fg} and --${bg} are hex tokens`);
      const value = ratio(palette[fg], palette[bg]);
      assert.ok(value >= 4.5, `${scheme}: --${fg} on --${bg} is ${value.toFixed(2)}:1`);
    }
  });

  test(`${scheme}: borders, stripes, and focus reach 3:1`, () => {
    for (const [fg, bg] of NON_TEXT) {
      const value = ratio(palette[fg], palette[bg]);
      assert.ok(value >= 3, `${scheme}: --${fg} on --${bg} is ${value.toFixed(2)}:1`);
    }
  });
}

test('dark mode overrides every colour token', () => {
  const overridden = tokens(rootBlock(css.slice(darkStart)));
  assert.deepEqual(Object.keys(overridden).sort(), Object.keys(light).sort());
});
