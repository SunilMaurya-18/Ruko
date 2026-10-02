import signals from '../../../shared/rules/signals.v0.json' with { type: 'json' };
import { codePointCount, isLetterOrMark, isWhitespace, strip } from './chars.js';
import { extractWithSpans } from './extract.js';
import { mask, normalize, rejectReason } from './normalize.js';
import { RuleText, compileRules, countsTowardBand } from './rules.js';
import { band, classify, evaluate, selectAnalogy } from './signals.js';

// Same limits as the server's application.yml (ruko.analyze).
export const MAX_CHARS = 4000;
const MIN_CHARS = 20;
const MIN_OCR_CONFIDENCE = 0.6;
const MIN_LETTER_RATIO = 0.5;
const FOOTER_KEY = 'no_flags_not_safe';

export const RULES = compileRules(signals);

export class OnDeviceRejected extends Error {
  constructor(reason) {
    super(reason);
    this.reason = reason;
  }
}

function unreadable(masked, ocrConfidence) {
  if (ocrConfidence != null && ocrConfidence < MIN_OCR_CONFIDENCE) return true;
  let letters = 0;
  let visible = 0;
  for (const ch of masked) {
    if (isWhitespace(ch)) continue;
    visible++;
    if (isLetterOrMark(ch)) letters++;
  }
  return codePointCount(strip(masked)) < MIN_CHARS || letters < visible * MIN_LETTER_RATIO;
}

/**
 * The server pipeline on the phone: guard, normalise, mask, extract, rules, band, class, analogy, template compose.
 * `text` is the already-masked request text. `i18n(lang, key, params)` resolves catalogue text. Returns the
 * AnalyzeResponse shape with `engine: "on_device"`; there is no model step, so the draft is pure catalogue text.
 */
export function analyzeOnDevice({ text, lang, source, ocrConfidence = null }, { i18n, snapshot }) {
  const rejected = rejectReason(text, MAX_CHARS);
  if (rejected) throw new OnDeviceRejected(rejected);

  const masked = mask(normalize(text));
  const { entities, matches } = extractWithSpans(masked.value);
  const isUnreadable = unreadable(masked.value, ocrConfidence);
  const ruleText = new RuleText(masked, matches, source);

  const hits = evaluate(RULES, ruleText, snapshot);
  const result = {
    band: band(hits, isUnreadable),
    contentClass: isUnreadable ? 'unknown' : classify(RULES, ruleText, hits),
    analogyKey: isUnreadable ? null : selectAnalogy(RULES, ruleText, hits),
  };
  return compose({ lang, entities, hits, ...result }, { i18n, snapshot });
}

/** Port of TemplateExplainer: reasons and one card per fired signal id, in engine order, from catalogue keys. */
function compose({ lang, entities, hits, band: bandName, contentClass, analogyKey }, { i18n, snapshot }) {
  const params = { date: snapshot.date ?? '' };
  const text = (key) => i18n(lang, key, params);
  const signalsOut = [];
  const unverified = [];
  const reassuring = [];
  const cards = [];
  const carded = new Set();
  for (const hit of hits) {
    const rule = RULES.require(hit.id);
    if (countsTowardBand(hit.severity)) {
      signalsOut.push({ id: hit.id, severity: hit.severity.toLowerCase(), evidence: hit.evidence, reason: text(rule.reasonKey) });
    } else if (hit.severity === 'UNVERIFIED') {
      const item = { id: hit.id, item: hit.item, action: rule.action };
      if (hit.snapshot != null) item.snapshot = hit.snapshot;
      unverified.push(item);
    } else {
      reassuring.push({ id: hit.id, evidence: hit.evidence, reason: text(rule.reasonKey) });
    }
    if (!carded.has(hit.id)) {
      carded.add(hit.id);
      cards.push({ signal_id: hit.id, text: text(rule.cardKey) });
    }
  }
  return {
    language: lang,
    entities,
    signals: signalsOut,
    unverified,
    reassuring,
    band: bandName,
    content_class: contentClass,
    counts: { red_flags: signalsOut.length, couldnt_verify: unverified.length, reassuring: reassuring.length },
    cards,
    analogy_key: analogyKey,
    footer_key: FOOTER_KEY,
    engine: 'on_device',
  };
}
