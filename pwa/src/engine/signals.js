import { SEVERITIES, countsTowardBand } from './rules.js';

// Port of the server's SignalEngine, SebiSnapshotIndex, BandCalculator, ContentClassifier, and AnalogyCatalog.

const SNAPSHOT_DETECTOR = 'snapshot';
const REGISTRATION = /^IN[A-Z][0-9]{9}$/;

/** Dated list of registration numbers. Empty or undated means "no snapshot": U14 still fires, S19 never does. */
export function snapshotIndex(file) {
  const numbers = new Set((file?.registration_numbers ?? []).map((number, i) => {
    const normalized = String(number ?? '').trim().toUpperCase();
    if (!REGISTRATION.test(normalized)) throw new Error(`sebi-intermediaries.json: malformed entry ${i}`);
    return normalized;
  }));
  const date = file?.snapshot_date || null;
  const active = date != null && numbers.size > 0;
  return {
    active,
    date: active ? date : null,
    lookup: (number) => (active ? (numbers.has(number.toUpperCase()) ? 'listed' : 'not_listed') : null),
  };
}

/** One hit per signal id, except rules with an action (U14), one per distinct item. Severity, then catalogue order. */
export function evaluate(ruleSet, text, snapshot) {
  const hits = [];
  const markers = new Map();
  const deferred = [];
  for (const rule of ruleSet.rules) {
    if (!rule.enabled || rule.detector != null) continue;
    if (rule.unlessSeverities.size === 0) run(rule);
    else deferred.push(rule);
  }
  deferred.forEach(run);
  applySnapshot();
  return hits.sort((a, b) => SEVERITIES.indexOf(a.severity) - SEVERITIES.indexOf(b.severity)
    || ruleSet.require(a.id).order - ruleSet.require(b.id).order);

  function blocked(rule) {
    for (const name of rule.unlessMarkers) {
      if (!markers.has(name)) markers.set(name, ruleSet.marker(name, text));
      if (markers.get(name)) return true;
    }
    return hits.some((hit) => rule.unlessSeverities.has(hit.severity));
  }

  function run(rule) {
    if (blocked(rule)) return;
    const found = rule.find(text);
    if (found.length === 0) return;
    if (rule.action == null) {
      hits.push(hit(rule, found[0]));
      return;
    }
    const items = new Set();
    for (const one of found) {
      if (one.value != null && !items.has(one.value)) {
        items.add(one.value);
        hits.push(hit(rule, one));
      }
    }
  }

  function hit(rule, found) {
    return {
      id: rule.id,
      severity: rule.severity,
      evidence: text.quote(found.start, found.end),
      item: rule.action == null ? null : found.value,
      snapshot: null,
    };
  }

  function applySnapshot() {
    const absent = ruleSet.emittedBy(SNAPSHOT_DETECTOR);
    const added = [];
    for (const hit of hits) {
      if (ruleSet.require(hit.id).action == null || hit.item == null) continue;
      const status = snapshot.lookup(hit.item);
      if (status == null) continue;
      hit.snapshot = status;
      if (status === 'not_listed' && absent) {
        added.push({ id: absent.id, severity: absent.severity, evidence: hit.evidence, item: hit.item, snapshot: null });
      }
    }
    hits.push(...added);
  }
}

/** TRD §2 band: distinct signal ids, counts not percentages. Unverified and reassurance never count. */
export function band(hits, unreadable) {
  if (unreadable) return 'not_enough_to_judge';
  const distinct = new Map(hits.map((hit) => [`${hit.id}\u0000${hit.severity}`, hit.severity]));
  const n = (severity) => [...distinct.values()].filter((s) => s === severity).length;
  const c = n('CRITICAL');
  const s = n('STRONG');
  const m = n('MODERATE');
  if (c >= 1 || s >= 2) return 'high_concern';
  if (s === 1 || m >= 2) return 'some_concern';
  return 'few_flags_still_verify';
}

export function classify(ruleSet, text, hits) {
  if (hits.some((hit) => hit.severity === 'CRITICAL' || hit.severity === 'STRONG')) return 'promotion';
  if (ruleSet.marker('payment_ask', text)) return ruleSet.marker('explanation', text) ? 'mixed' : 'promotion';
  if (hits.some((hit) => hit.id === 'R2')) return 'education';
  return 'unknown';
}

/** `hits` in engine order: the first counted hit whose row has an analogy, else an analogy trigger. */
export function selectAnalogy(ruleSet, text, hits) {
  for (const hit of hits) {
    const key = ruleSet.require(hit.id).analogyKey;
    if (key != null && countsTowardBand(hit.severity)) return key;
  }
  return ruleSet.analogyTrigger(text);
}
