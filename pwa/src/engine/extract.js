import shared from '../../../shared/patterns/entities.v0.json' with { type: 'json' };

// Port of the server's EntityExtractor. Runs on normalised, masked text; values in order of first appearance.

export const ENTITY_TYPE_IDS = ['upi_ids', 'reg_numbers', 'ifsc', 'urls', 'return_claims', 'app_names', 'qr_phrases'];

const TYPES = shared.types.map((type) => {
  if (!ENTITY_TYPE_IDS.includes(type.id)) throw new Error(`entities.v0.json: unknown entity type ${type.id}`);
  return {
    id: type.id,
    phrase: type.kind === 'phrase',
    caseMode: type.case ?? 'none',
    trim: type.trim ?? '',
    patterns: type.patterns.map((p) => ({
      regex: new RegExp(p.pattern.normalize('NFKC'), `gu${p.flags ?? ''}`),
      value: p.value ?? null,
    })),
  };
});

const PHONE_PLACEHOLDER = /\[PHONE\]/g;

export function extractWithSpans(text) {
  const matches = [];
  const byType = {};
  for (const type of TYPES) {
    const found = typeMatches(type, text);
    matches.push(...found);
    byType[type.id] = [...new Set(found.map((match) => match.value))];
  }
  const phones = text.match(PHONE_PLACEHOLDER)?.length ?? 0;
  return { entities: entitiesOf(byType, phones), matches };
}

function entitiesOf(byType, phoneCount) {
  const list = (id) => byType[id] ?? [];
  return {
    upi_ids: list('upi_ids'),
    reg_numbers: list('reg_numbers'),
    ifsc: list('ifsc'),
    urls: list('urls'),
    phone_count: phoneCount,
    return_claims: list('return_claims'),
    app_names: list('app_names'),
    qr_phrases: list('qr_phrases'),
  };
}

function typeMatches(type, text) {
  const hits = [];
  for (const pattern of type.patterns) {
    for (const match of text.matchAll(pattern.regex)) {
      if (match[0].length > 0) {
        hits.push({ start: match.index, end: match.index + match[0].length, value: pattern.value });
      }
    }
  }
  hits.sort((a, b) => a.start - b.start || b.end - a.end);

  const kept = [];
  for (const hit of hits) {
    const last = kept.at(-1);
    if (type.phrase && last && hit.start <= last.end + 1) {
      kept[kept.length - 1] = { start: last.start, end: Math.max(last.end, hit.end), value: null };
    } else if (!last || hit.start >= last.end) {
      kept.push(hit);
    }
  }

  const matches = [];
  for (const hit of kept) {
    let { end } = hit;
    let value;
    if (hit.value != null) {
      value = hit.value;
    } else {
      value = trimTrailing(text.substring(hit.start, hit.end), type.trim);
      end = hit.start + value.length;
    }
    if (type.caseMode === 'upper') value = value.toUpperCase();
    else if (type.caseMode === 'lower') value = value.toLowerCase();
    if (value.length > 0) matches.push({ type: type.id, start: hit.start, end, value });
  }
  return matches;
}

function trimTrailing(value, chars) {
  let end = value.length;
  while (end > 0 && chars.includes(value[end - 1])) end--;
  return value.slice(0, end);
}
