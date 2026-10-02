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
