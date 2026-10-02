import { isLetter, isWhitespace, strip } from './chars.js';

// Port of the server's RuleText, SpanMatcher, AnchoredDetector, PrefixMapDetector, and RuleLoader compiler.
// Logic is hand-ported once; every term, pattern, and threshold comes from shared/rules/signals.v0.json.

export const SEVERITIES = ['CRITICAL', 'STRONG', 'MODERATE', 'UNVERIFIED', 'REASSURANCE'];
export const countsTowardBand = (severity) => severity === 'CRITICAL' || severity === 'STRONG' || severity === 'MODERATE';

const ACCOUNTS = 'accounts';
const PHONES = 'phones';
const PLACEHOLDER = /\[(?:PHONE|ACCT|AADHAAR|PAN|OTP)\]/g;
const ABBREVIATIONS = new Set(['rs', 'inr', 'no', 'mr', 'mrs', 'dr', 'st', 'vs']);
const MAX_EVIDENCE_CHARS = 160;
const SOURCE_EVIDENCE_CHARS = 120;

const span = (start, end, value = null) => ({ start, end, value });
const inside = (s, from, to) => s.start >= from && s.end <= to;
const distance = (a, b) => (a.end <= b.start ? b.start - a.end : b.end <= a.start ? a.start - b.end : 0);
const byPosition = (a, b) => a.start - b.start || b.end - a.end;

/** The masked text as the rules see it; `masked` is a MappedText over the request text. */
export class RuleText {
  constructor(masked, matches, source) {
    this.masked = masked;
    this.text = masked.value;
    this.source = source;
    this.sentences = split(this.text);
    this.placeholders = [];
    this.entitySpans = new Map();
    const add = (type, s) => {
      if (!this.entitySpans.has(type)) this.entitySpans.set(type, []);
      this.entitySpans.get(type).push(s);
    };
    for (const match of this.text.matchAll(PLACEHOLDER)) {
      const s = span(match.index, match.index + match[0].length, match[0]);
      this.placeholders.push(s);
      if (match[0] === '[ACCT]') add(ACCOUNTS, s);
      if (match[0] === '[PHONE]') add(PHONES, s);
    }
    for (const match of matches) add(match.type, span(match.start, match.end, match.value));
  }

  get length() {
    return this.text.length;
  }

  entities(type) {
    return this.entitySpans.get(type) ?? [];
  }

  sentenceAt(position) {
    return this.sentences.find((s) => position >= s.start && position < s.end) ?? span(0, this.text.length);
  }

  /** Verbatim slice of the request for [start, end) of the masked text, placeholders kept masked. */
  quote(start, end) {
    let out = '';
    let cursor = start;
    for (const placeholder of this.placeholders) {
      if (placeholder.end <= start || placeholder.start >= end) continue;
      if (placeholder.start > cursor) out += this.masked.originalSlice(cursor, placeholder.start);
      out += placeholder.value;
      cursor = placeholder.end;
    }
    if (cursor < end) out += this.masked.originalSlice(cursor, end);
    return strip(out);
  }
}

function split(text) {
  const sentences = [];
  let start = 0;
  for (let i = 0; i < text.length; i++) {
    if (isBreak(text, i)) {
      addTrimmed(sentences, text, start, i + 1);
      start = i + 1;
    }
  }
  addTrimmed(sentences, text, start, text.length);
  return sentences;
}

function isBreak(text, i) {
  const c = text[i];
  if (c === '!' || c === '?' || c === '\n' || c === '\u0964' || c === '\u0965') return true;
  if (c !== '.') return false;
  const atEnd = i + 1 === text.length || isWhitespace(text[i + 1]);
  if (!atEnd) return false;
  let wordStart = i;
  while (wordStart > 0 && isLetter(text[wordStart - 1])) wordStart--;
  return !ABBREVIATIONS.has(text.substring(wordStart, i));
}

function addTrimmed(sentences, text, start, end) {
  while (start < end && isWhitespace(text[start])) start++;
  while (end > start && isWhitespace(text[end - 1])) end--;
  if (end > start) sentences.push(span(start, end));
}

const NOT_AFTER_WORD = '(?<![\\p{L}\\p{M}\\p{N}])';
const NOT_BEFORE_WORD = '(?![\\p{L}\\p{M}\\p{N}])';
const OPAQUE_ENTITIES = ['upi_ids', 'urls'];

export const foldTerm = (term) => term.normalize('NFKC').toLowerCase();
const escape = (text) => text.replace(/[.*+?^${}()|[\]\\/]/g, '\\$&');
const compile = (source) => new RegExp(source.normalize('NFKC'), 'gu');

function compileTerms(terms) {
  const alternatives = [...new Set(terms.map(foldTerm))]
    .sort((a, b) => b.length - a.length)
    .map((term) => (term.endsWith('*') ? escape(term.slice(0, -1)) : escape(term) + NOT_BEFORE_WORD));
  return new RegExp(`${NOT_AFTER_WORD}(?:${alternatives.join('|')})`, 'gu');
}

function collect(regex, text) {
  const spans = [];
  for (const match of text.matchAll(regex)) {
    if (match[0].length > 0) spans.push(span(match.index, match.index + match[0].length));
  }
  return spans;
}

/** Terms match whole words of the case-folded text; term hits inside a UPI id or URL do not count. */
class SpanMatcher {
  constructor(termRegexes, patterns, entities) {
    this.termRegexes = termRegexes;
    this.patterns = patterns;
    this.entities = entities;
  }

  static of(terms = [], patterns = [], entities = []) {
    return new SpanMatcher(terms.length ? [compileTerms(terms)] : [], patterns.map(compile), [...entities]);
  }

  static union(matchers) {
    return new SpanMatcher(
      matchers.flatMap((m) => m.termRegexes),
      matchers.flatMap((m) => m.patterns),
      [...new Set(matchers.flatMap((m) => m.entities))],
    );
  }

  find(text) {
    const spans = [];
    const opaque = OPAQUE_ENTITIES.flatMap((type) => text.entities(type));
    for (const terms of this.termRegexes) {
      for (const s of collect(terms, text.text)) {
        if (!opaque.some((entity) => inside(s, entity.start, entity.end))) spans.push(s);
      }
    }
    for (const pattern of this.patterns) spans.push(...collect(pattern, text.text));
    for (const entity of this.entities) spans.push(...text.entities(entity));
    return spans.sort(byPosition);
  }

  findIn(text) {
    return [...this.termRegexes, ...this.patterns].flatMap((regex) => collect(regex, text));
  }
}

const NONE = { find: () => [] };

/** TERMS, REGEX, and ENTITY rules, and markers: anchors filtered by match/exclude, negation, and `with`. */
function anchoredDetector({ anchors, match = null, exclude = null, negation = null, withs = [], sources = new Set() }) {
  function withConditions(text, anchor, sentence, conditionSpans) {
    let { start, end } = anchor;
    for (let i = 0; i < withs.length; i++) {
      const sentenceScope = withs[i].scope === 'sentence';
      const from = sentenceScope ? sentence.start : 0;
      const to = sentenceScope ? sentence.end : text.length;
      let best = null;
      for (const candidate of conditionSpans[i]) {
        if (!inside(candidate, from, to) || inside(candidate, anchor.start, anchor.end)) continue;
        if (best == null || distance(candidate, anchor) < distance(best, anchor)) best = candidate;
      }
      if (best == null) return null;
      if (sentenceScope) {
        start = Math.min(start, best.start);
        end = Math.max(end, best.end);
      }
    }
    return end - start > MAX_EVIDENCE_CHARS ? span(anchor.start, anchor.end) : span(start, end);
  }

  function negated(text, sentence, ownAnchors) {
    const chars = text.text.substring(sentence.start, sentence.end).split('');
    for (const anchor of ownAnchors) {
      for (let i = Math.max(anchor.start, sentence.start); i < Math.min(anchor.end, sentence.end); i++) {
        chars[i - sentence.start] = ' ';
      }
    }
    return negation.findIn(chars.join('')).length > 0;
  }

  function firstSentence(text) {
    const first = text.sentences[0];
    let { end } = first;
    if (end - first.start > SOURCE_EVIDENCE_CHARS) {
      const cut = text.text.lastIndexOf(' ', first.start + SOURCE_EVIDENCE_CHARS);
      end = cut > first.start ? cut : first.start + SOURCE_EVIDENCE_CHARS;
    }
    return span(first.start, end);
  }

  return {
    find(text) {
      const found = [];
      if (sources.has(text.source) && text.sentences.length > 0) found.push(firstSentence(text));
      const all = anchors.find(text);
      const conditionSpans = withs.map((condition) => condition.matcher.find(text));
      for (const anchor of all) {
        const value = anchor.value ?? text.text.substring(anchor.start, anchor.end);
        if ((match && !match.test(value)) || (exclude && exclude.test(value))) continue;
        const sentence = text.sentenceAt(anchor.start);
        if (negation && negated(text, sentence, all)) continue;
        const hit = withConditions(text, anchor, sentence, conditionSpans);
        if (hit) found.push(span(hit.start, hit.end, anchor.value));
      }
      return found;
    },
  };
}

/** S5: a known prefix with the wrong digit count, or one that matches no role the message claims. */
function prefixMapDetector(digits, rolesByPrefix) {
  const candidate = new RegExp(
    `(?<![\\p{L}\\p{M}\\p{N}])(${[...rolesByPrefix.keys()].join('|')})([0-9]{4,12})(?![\\p{L}\\p{M}\\p{N}])`, 'gu');
  return {
    find(text) {
      const claimed = new Set([...rolesByPrefix].filter(([, roles]) => roles.find(text).length > 0).map(([p]) => p));
      const found = [];
      for (const match of text.text.matchAll(candidate)) {
        const malformed = match[2].length !== digits;
        const roleMismatch = claimed.size > 0 && !claimed.has(match[1]);
        if (malformed || roleMismatch) {
          found.push(span(match.index, match.index + match[0].length, match[0].toUpperCase()));
        }
      }
      return found;
    },
  };
}

const NEGATION_SET = 'negation';

/** Compiles the catalogue the same way RuleLoader does. Throws on unknown sets, markers, or severities. */
export function compileRules(file) {
  const sets = new Map(Object.entries(file.sets).map(([name, set]) => [name, SpanMatcher.of(set.terms, set.patterns)]));
  const set = (owner, name) => {
    if (!sets.has(name)) throw new Error(`signals.v0.json: ${owner} refers to unknown set ${name}`);
    return sets.get(name);
  };
  const matcher = (owner, { terms, patterns, entities, sets: names = [] }) => {
    const parts = [SpanMatcher.of(terms, patterns, entities), ...names.map((name) => set(owner, name))];
    return parts.length === 1 ? parts[0] : SpanMatcher.union(parts);
  };
  const conditions = (owner, withs = []) => withs.map((c) => ({ matcher: matcher(owner, c), scope: c.scope }));

  const markers = new Map(Object.entries(file.markers).map(([name, marker]) => [name, anchoredDetector({
    anchors: matcher(`marker ${name}`, marker),
    withs: conditions(`marker ${name}`, marker.with),
  })]));

  const triggers = file.analogy_triggers.map((trigger) => ({
    analogyKey: trigger.analogy_key,
    matcher: matcher(trigger.analogy_key, trigger),
  }));

  const rules = file.signals.map((json, order) => {
    if (!SEVERITIES.includes(json.severity)) throw new Error(`signals.v0.json: ${json.id} has severity ${json.severity}`);
    const unlessMarkers = json.unless?.markers ?? [];
    for (const name of unlessMarkers) {
      if (!markers.has(name)) throw new Error(`signals.v0.json: ${json.id} refers to unknown marker ${name}`);
    }
    let detector = NONE;
    if (json.type === 'PREFIX_MAP') {
      const roles = new Map(json.prefix_map.prefixes.map((p) => [p.prefix, SpanMatcher.of(p.terms)]));
      detector = prefixMapDetector(json.prefix_map.digits, roles);
    } else if (json.type !== 'LLM_TAG' && json.detector == null) {
      detector = anchoredDetector({
        anchors: matcher(json.id, json),
        match: json.match == null ? null : new RegExp(json.match.normalize('NFKC'), 'u'),
        exclude: json.exclude == null ? null : new RegExp(json.exclude.normalize('NFKC'), 'u'),
        negation: json.negatable ? set(json.id, NEGATION_SET) : null,
        withs: conditions(json.id, json.with),
        sources: new Set(json.sources ?? []),
      });
    }
    return {
      id: json.id,
      severity: json.severity,
      type: json.type,
      enabled: json.enabled,
      llmTag: Boolean(json.llm_tag),
      reasonKey: json.reason_key,
      spokenKey: json.spoken_key,
      cardKey: json.card_key,
      analogyKey: json.analogy_key ?? null,
      action: json.action ?? null,
      detector: json.detector ?? null,
      unlessMarkers: new Set(unlessMarkers),
      unlessSeverities: new Set(json.unless?.severities ?? []),
      order,
      find: (text) => detector.find(text),
    };
  });

  const byId = new Map(rules.map((rule) => [rule.id, rule]));
  return {
    rules,
    require(id) {
      const rule = byId.get(id);
      if (!rule) throw new Error(`unknown signal ${id}`);
      return rule;
    },
    emittedBy: (name) => rules.find((rule) => rule.enabled && rule.detector === name) ?? null,
    marker(name, text) {
      const marker = markers.get(name);
      if (!marker) throw new Error(`unknown marker ${name}`);
      return marker.find(text).length > 0;
    },
    analogyTrigger: (text) => triggers.find((trigger) => trigger.matcher.find(text).length > 0)?.analogyKey ?? null,
  };
}
