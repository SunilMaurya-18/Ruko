const TIMEOUT_MS = 15000;

export class AnalyzeError extends Error {
  constructor(kind, status = 0) {
    super(kind);
    this.kind = kind;
    this.status = status;
  }
}

/** Sends already-masked text only. */
export async function analyze({ text, lang, source, ocrConfidence }) {
  const body = { text, lang, source };
  if (ocrConfidence != null) {
    body.ocr_confidence = ocrConfidence;
  }

  let response;
  try {
    response = await fetch('/api/v1/analyze', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(body),
      credentials: 'omit',
      cache: 'no-store',
      referrerPolicy: 'no-referrer',
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch {
    throw new AnalyzeError('offline');
  }

  if (response.ok) {
    return response.json();
  }
  if (response.status === 413) throw new AnalyzeError('too_long', 413);
  if (response.status === 429) throw new AnalyzeError('rate_limited', 429);
  if (response.status >= 500) throw new AnalyzeError('server', response.status);
  throw new AnalyzeError('rejected', response.status);
}

const TTS_TIMEOUT_MS = 5000;

/**
 * Server speech for one catalogue script key; the request carries a key and counts, never message text. Resolves to
 * an audio Blob, or null on any failure (including 503 {fallback: "browser_tts"}) so the caller uses the phone voice.
 */
export async function tts({ scriptKey, lang, counts }) {
  try {
    const response = await fetch('/api/v1/voice/tts', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'audio/wav, application/problem+json' },
      body: JSON.stringify({
        script_key: scriptKey,
        lang,
        counts: { red_flags: counts.red_flags, couldnt_verify: counts.couldnt_verify, reassuring: counts.reassuring },
      }),
      credentials: 'omit',
      cache: 'no-store',
      referrerPolicy: 'no-referrer',
      signal: AbortSignal.timeout(TTS_TIMEOUT_MS),
    });
    if (!response.ok) return null;
    const blob = await response.blob();
    return blob.size > 0 ? blob : null;
  } catch {
    return null;
  }
}

const COMPLAINT_TIMEOUT_MS = 5000;

/**
 * Complaint draft from fixed choices, a date, and an amount; never anything the user typed in their own words.
 * Resolves to the draft text, or null on any failure so the caller builds the same draft on the phone.
 */
export async function complaintDraft({ lang, date, amount, channel, platform, payee_id_type: payeeIdType }) {
  try {
    const response = await fetch('/api/v1/complaint/draft', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ lang, date, amount, channel, platform, payee_id_type: payeeIdType }),
      credentials: 'omit',
      cache: 'no-store',
      referrerPolicy: 'no-referrer',
      signal: AbortSignal.timeout(COMPLAINT_TIMEOUT_MS),
    });
    if (!response.ok) return null;
    const body = await response.json();
    return typeof body.text === 'string' ? body.text : null;
  } catch {
    return null;
  }
}

/** Public page content; carries no user data. Resolves to null when the server cannot be reached. */
export async function howRukoDecides(lang) {
  try {
    const response = await fetch(`/api/v1/content/how-ruko-decides?lang=${encodeURIComponent(lang)}`, {
      headers: { Accept: 'application/json' },
      credentials: 'omit',
      referrerPolicy: 'no-referrer',
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    return response.ok ? await response.json() : null;
  } catch {
    return null;
  }
}
