import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

// WCAG 2.2 AA: 4.5:1 for text, 3:1 for borders, stripes, and the focus ring. The app is light-only.
const css = readFileSync(new URL('./styles.css', import.meta.url), 'utf8');

const TEXT = [
  ['ink', 'bg'], ['ink', 'surface'], ['ink', 'surface-2'],
  ['muted', 'bg'], ['muted', 'surface'], ['muted', 'surface-2'],
  ['on-accent', 'accent'], ['on-accent', 'accent-strong'],
  ['accent', 'surface'], ['accent', 'bg'], ['accent', 'accent-soft'],
  ['on-header', 'header-bg'],
  ['danger', 'surface'], ['danger', 'danger-bg'],
  ['ink', 'danger-bg'], ['ink', 'warn-bg'], ['ink', 'info-bg'],
  ['muted', 'danger-bg'], ['muted', 'warn-bg'], ['muted', 'info-bg'],
  ['ink', 'accent-soft'], ['muted', 'accent-soft'],
];

const NON_TEXT = [
  ['field-border', 'surface'], ['field-border', 'surface-2'], ['focus', 'surface'], ['focus', 'bg'], ['focus', 'accent-soft'],
  ['warn', 'warn-bg'], ['warn', 'surface'], ['info', 'info-bg'], ['info', 'surface'],
  ['ok', 'surface'], ['muted', 'surface'],
  ['accent-line', 'surface'], ['accent-line', 'bg'], ['accent-line', 'accent-soft'],
];

function tokens(block) {
  return Object.fromEntries([...block.matchAll(/--([\w-]+):\s*(#[0-9a-f]{6})\s*;/gi)].map(([, name, hex]) => [name, hex]));
}

function rootBlock(source) {
  const start = source.indexOf(':root {');
  assert.ok(start >= 0, 'a :root block');
  return source.slice(start, source.indexOf('}', start));
}

const palette = tokens(rootBlock(css));

const luminance = (hex) => {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map((c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};

const ratio = (a, b) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

test('text colours reach 4.5:1', () => {
  for (const [fg, bg] of TEXT) {
    assert.ok(palette[fg] && palette[bg], `--${fg} and --${bg} are hex tokens`);
    const value = ratio(palette[fg], palette[bg]);
    assert.ok(value >= 4.5, `--${fg} on --${bg} is ${value.toFixed(2)}:1`);
  }
});

test('borders, stripes, and focus reach 3:1', () => {
  for (const [fg, bg] of NON_TEXT) {
    const value = ratio(palette[fg], palette[bg]);
    assert.ok(value >= 3, `--${fg} on --${bg} is ${value.toFixed(2)}:1`);
  }
});

test('the UI stays light whatever the phone setting', () => {
  assert.ok(!css.includes('prefers-color-scheme'), 'no colour-scheme media query');
  assert.match(css, /color-scheme:\s*light;/);
});
