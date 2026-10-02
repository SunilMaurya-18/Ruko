import { PII_RULES, PLACEHOLDER } from '../pii/mask.js';
import { isBlank, isControl, isMark, isUpperOrTitle } from './chars.js';
import { MappedText } from './mapped.js';

// Port of the server's TextNormalizer, PiiMasker (offset-mapped), and InputGuard.

const INVISIBLE = /[\u00AD\u180E\u200B-\u200F\u202A-\u202E\u2060-\u2064\u2066-\u2069\uFEFF\x00-\x08\x0B-\x1F\x7F-\x9F]/gu;
const SPACE_RUNS = /[ \t]{2,}|\t/gu;
const SPACED_LETTERS = /(?<![\p{L}\p{M}\p{N}])[a-zA-Z](?: [a-zA-Z]){2,}(?![\p{L}\p{M}\p{N}])/gu;
const SEPARATED_LETTERS = /(?<![\p{L}\p{M}\p{N}@./_*-])[a-zA-Z](?:[.\-_*][a-zA-Z]){2,}(?![\p{L}\p{M}\p{N}@/]|[.\-_*][\p{L}\p{N}])/gu;
const NOT_ASCII_LETTER = /[^a-zA-Z]/g;
const DEVANAGARI_DIGIT = /[\u0966-\u096F]/gu;

export function normalize(input) {
  let text = nfkc(MappedText.of(input));
  text = text.replaceAll(INVISIBLE, () => '');
  text = text.replaceAll(SPACE_RUNS, () => ' ');
  text = text.replaceAll(SPACED_LETTERS, (match) => match[0].replace(NOT_ASCII_LETTER, ''));
  text = text.replaceAll(SEPARATED_LETTERS, (match) => match[0].replace(NOT_ASCII_LETTER, ''));
  text = text.replaceAll(DEVANAGARI_DIGIT, (match) => String.fromCharCode(48 + match[0].charCodeAt(0) - 0x0966));
  return caseFold(text);
}

/** NFKC per base character plus its combining marks, so each output unit maps to a small original range. */
function nfkc(text) {
  const { value } = text;
  if (value.normalize('NFKC') === value) return text;
  const edits = [];
  let i = 0;
  while (i < value.length) {
    const start = i;
    i += width(value, i);
    while (i < value.length && isMark(String.fromCodePoint(value.codePointAt(i)))) {
      i += width(value, i);
    }
    const cluster = value.slice(start, i);
    const normalized = cluster.normalize('NFKC');
    if (normalized !== cluster) edits.push({ start, end: i, replacement: normalized });
  }
  return text.apply(edits);
}

function caseFold(text) {
  const { value } = text;
  const edits = [];
  for (let i = 0; i < value.length;) {
    const step = width(value, i);
    const ch = value.slice(i, i + step);
    if (isUpperOrTitle(ch)) edits.push({ start: i, end: i + step, replacement: ch.toLowerCase() });
    i += step;
  }
  return text.apply(edits);
}

const width = (text, i) => (text.codePointAt(i) > 0xffff ? 2 : 1);

export function mask(text) {
  let masked = text.replaceAll(PLACEHOLDER, (match) => match[0].toUpperCase());
  for (const rule of PII_RULES) {
    const edits = [];
    for (const match of masked.value.matchAll(rule.regex)) {
      const start = rule.keepPrefix ? match.index + match[1].length : match.index;
      edits.push({ start, end: match.index + match[0].length, replacement: rule.placeholder });
    }
    masked = masked.apply(edits);
  }
  return masked;
}

const MAX_CONTROL_RATIO = 0.05;

/** Returns why the server would reject `text` (its problem `reason`), or null when it is acceptable. */
export function rejectReason(text, maxChars) {
  if (text == null || isBlank(text)) return 'empty';
  let controls = 0;
  let codePoints = 0;
  for (let i = 0; i < text.length; i++) {
    const c = text.charCodeAt(i);
    codePoints++;
    if (c >= 0xd800 && c <= 0xdbff && i + 1 < text.length) {
      const next = text.charCodeAt(i + 1);
      if (next >= 0xdc00 && next <= 0xdfff) {
        i++;
        continue;
      }
    }
    if ((c >= 0xd800 && c <= 0xdfff) || c === 0xfffd) return 'invalid_encoding';
    if (c === 0) return 'binary';
    if (isControl(text[i]) && c !== 10 && c !== 13 && c !== 9) controls++;
  }
  if (codePoints > maxChars) return 'too_long';
  if (controls > codePoints * MAX_CONTROL_RATIO) return 'binary';
  return null;
}
