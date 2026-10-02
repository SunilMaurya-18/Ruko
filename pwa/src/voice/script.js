// What the Listen button says. Mirrors the server's VoiceScripts: a script is a catalogue key, and the words come
// from the shared catalogue, so Bhashini (server) and speechSynthesis (phone) say the same thing.
// shared/fixtures/voice-scripts.v0.json keeps the two builders in step.
//
// `text(key, params)` looks a key up in the result's language; passing it in keeps this file free of the Vite-only
// catalogue import so node tests can run it.

export const ACTION = 'voice.action';
export const GENERIC = 'voice.generic';
const BAND = 'band.';
const NOT_ENOUGH = 'band.not_enough_to_judge';
const MAX_FLAGS = 3;

function count(text, name, n) {
  const form = n === 0 ? 'zero' : n === 1 ? 'one' : 'other';
  return text(`voice.count.${name}.${form}`, { n: String(n) });
}

/** Spoken text for one script key. A band is its label, hint, and count sentences (none when not enough to judge). */
export function scriptText(text, lang, key, counts) {
  if (!key.startsWith(BAND)) return text(key);
  const spoken = `${text(key)}${lang === 'hi' ? '।' : '.'} ${text(`${key}.hint`)}`;
  if (key === NOT_ENOUGH) return spoken;
  const parts = [spoken, count(text, 'red_flags', counts.red_flags)];
  if (counts.couldnt_verify > 0) parts.push(count(text, 'couldnt_verify', counts.couldnt_verify));
  if (counts.reassuring > 0) parts.push(count(text, 'reassuring', counts.reassuring));
  return parts.join(' ');
}

/** Script keys in spoken order: band, class, top flags, analogy, action, footer. */
export function spokenKeys(result) {
  const keys = [`${BAND}${result.band}`, `class.${result.content_class}`];
  if (result.cards.some((card) => !card.signal_id)) keys.push(GENERIC);
  for (const signal of result.signals.slice(0, MAX_FLAGS)) keys.push(`sig.${signal.id}.spoken`);
  if (result.unverified.length > 0) keys.push(`sig.${result.unverified[0].id}.spoken`);
  if (result.analogy_key) keys.push(result.analogy_key);
  keys.push(ACTION, `footer.${result.footer_key}`);
  return keys;
}

/**
 * `{key, text}` per spoken segment. The key is what the server may speak; the text is what the phone speaks if it
 * has to. A signal line with a placeholder the phone cannot fill (the snapshot date) uses the server's reason.
 */
export function segments(result, text) {
  const reasons = Object.fromEntries(result.signals.map((signal) => [`sig.${signal.id}.spoken`, signal.reason]));
  return spokenKeys(result).map((key) => {
    let spoken = scriptText(text, result.language, key, result.counts);
    if ((spoken == null || spoken.includes('{')) && reasons[key]) spoken = reasons[key];
    return { key, text: spoken };
  }).filter((segment) => segment.text);
}
