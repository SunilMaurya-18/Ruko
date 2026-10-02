import { useId, useRef, useState } from 'react';
import { AnalyzeError, analyze } from '../api.js';
import { updateDraft, useDraft } from '../draft.js';
import { maskPii } from '../pii/mask.js';
import { Link, navigate } from '../router.jsx';

const MAX_CHARS = 4000;
const LOW_OCR_CONFIDENCE = 0.6;

const ERRORS = {
  offline: 'इंटरनेट नहीं मिला। थोड़ी देर बाद फिर कोशिश करें।',
  too_long: `मैसेज बहुत लंबा है। ${MAX_CHARS} अक्षर तक भेजें।`,
  rate_limited: 'बहुत ज़्यादा जाँच हो गईं। एक मिनट बाद फिर कोशिश करें।',
  rejected: 'यह मैसेज पढ़ा नहीं जा सका। टेक्स्ट ठीक करके फिर कोशिश करें।',
  server: 'कुछ गड़बड़ हुई। फिर कोशिश करें।',
  ocr: 'फ़ोटो नहीं पढ़ी जा सकी। दूसरी फ़ोटो चुनें या टेक्स्ट चिपकाएँ।',
  clipboard: 'चिपकाया नहीं जा सका। मैसेज के बॉक्स को दबाकर रखें और "Paste" चुनें।',
};

const percent = (value) => `${Math.round(value * 100)}%`;

export default function Home() {
  const draft = useDraft();
  const [busy, setBusy] = useState(false);
  const [ocr, setOcr] = useState(null);
  const [error, setError] = useState(null);
  const fileInput = useRef(null);
  const ids = { text: useId(), hint: useId(), count: useId(), status: useId() };

  const chars = [...draft.text].length;
  const tooLong = chars > MAX_CHARS;
  const canPaste = typeof navigator !== 'undefined' && Boolean(navigator.clipboard?.readText);

  const setText = (text, source, ocrConfidence = null) => {
    setError(null);
    updateDraft({ text, source, ocrConfidence, result: null, sentText: null });
  };

  const paste = async () => {
    try {
      setText(await navigator.clipboard.readText(), 'paste');
      setOcr(null);
    } catch {
      setError('clipboard');
    }
  };

  const readPhoto = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    setError(null);
    setOcr({ stage: 'loading', progress: 0 });
    try {
      const { text, confidence } = await recognize(file, setOcr);
      setText(text, 'ocr', confidence);
      setOcr({ stage: 'done', confidence });
    } catch {
      setOcr(null);
      setError('ocr');
    }
  };

  const check = async (event) => {
    event.preventDefault();
    if (!draft.text.trim() || tooLong || busy) return;
    const request = {
      text: maskPii(draft.text),
      lang: draft.lang,
      source: draft.source,
      ocrConfidence: draft.source === 'ocr' ? draft.ocrConfidence : null,
    };
    setBusy(true);
    setError(null);
    try {
      const result = navigator.onLine === false ? await onDevice(request) : await analyze(request).catch((e) => {
        const kind = e instanceof AnalyzeError ? e.kind : 'server';
        if (!ON_DEVICE_FALLBACK.has(kind)) throw e;
        return onDevice(request);
      });
      updateDraft({ sentText: request.text, result });
      navigate('/result');
    } catch (e) {
      const fallback = navigator.onLine === false ? 'offline' : 'server';
      setError(e instanceof AnalyzeError ? e.kind : ON_DEVICE_ERRORS[e?.reason] ?? fallback);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section aria-labelledby="home-title">
      <h1 id="home-title" tabIndex={-1}>रुको</h1>
      <p className="lead">निवेश वाला कोई मैसेज मिला? पैसे भेजने से पहले यहाँ जाँचें।</p>

      <form className="check-form" onSubmit={check} noValidate>
        <label htmlFor={ids.text} className="field-label">मैसेज</label>
        <p id={ids.hint} className="hint">WhatsApp या Telegram में मैसेज को “शेयर” करके रुको चुनें, या यहाँ चिपकाएँ।</p>
        <textarea
          id={ids.text}
          className="transcript"
          rows={8}
          value={draft.text}
          onChange={(e) => updateDraft({ text: e.target.value, result: null, sentText: null })}
          aria-describedby={`${ids.hint} ${ids.count}`}
          aria-invalid={tooLong || undefined}
          lang={draft.lang}
          spellCheck={false}
          autoComplete="off"
        />
        <p id={ids.count} className={tooLong ? 'count count-over' : 'count'}>
          {chars} / {MAX_CHARS} अक्षर
        </p>

        <div className="row">
          {canPaste && (
            <button type="button" className="button button-secondary touch" onClick={paste}>चिपकाएँ</button>
          )}
          <button
            type="button"
            className="button button-secondary touch"
            onClick={() => fileInput.current?.click()}
            disabled={ocr?.stage === 'loading' || ocr?.stage === 'reading'}
          >
            फ़ोटो से पढ़ें
          </button>
          <input ref={fileInput} type="file" accept="image/*" className="visually-hidden" tabIndex={-1}
                 aria-hidden="true" onChange={readPhoto} />
        </div>

        <p id={ids.status} className="status" role="status" aria-live="polite">
          {ocr?.stage === 'loading' && 'फ़ोटो पढ़ने की तैयारी हो रही है…'}
          {ocr?.stage === 'reading' && `फ़ोटो पढ़ रहे हैं… ${percent(ocr.progress ?? 0)}`}
          {ocr?.stage === 'done' && (ocr.confidence < LOW_OCR_CONFIDENCE
            ? `फ़ोटो साफ़ नहीं है (भरोसा ${percent(ocr.confidence)})। ऊपर का टेक्स्ट ठीक कर लें या दूसरी फ़ोटो लें।`
            : `फ़ोटो से पढ़ा गया (भरोसा ${percent(ocr.confidence)})। गलत शब्द हों तो ठीक कर लें।`)}
        </p>

        <fieldset className="lang-choice">
          <legend>जवाब की भाषा</legend>
          {[['hi', 'हिंदी'], ['en', 'English']].map(([value, label]) => (
            <label key={value} className="radio touch" lang={value}>
              <input type="radio" name="lang" value={value} checked={draft.lang === value}
                     onChange={() => updateDraft({ lang: value })} />
              {label}
            </label>
          ))}
        </fieldset>

        {error && <p className="error" role="alert">{ERRORS[error]}</p>}

        <button type="submit" className="button touch" disabled={!draft.text.trim() || tooLong || busy} aria-busy={busy}>
          {busy ? 'जाँच हो रही है…' : 'जाँचें'}
        </button>
        <p className="hint">
          फ़ोटो इसी फ़ोन पर पढ़ी जाती है। फ़ोन नंबर, खाता नंबर, आधार, PAN और OTP भेजने से पहले छिपा दिए जाते हैं।
        </p>
      </form>

      <ul className="actions">
        <li>
          <Link to="/recovery" className="button button-secondary touch">पैसे भेज चुके हैं? मदद लें</Link>
        </li>
        <li>
          <Link to="/how-ruko-decides" className="button button-secondary touch">रुको कैसे तय करता है</Link>
        </li>
      </ul>
    </section>
  );
}

// The server could not answer, so the same rules run on this phone. Input problems (400, 413) are not retried.
const ON_DEVICE_FALLBACK = new Set(['offline', 'server', 'rate_limited']);
const ON_DEVICE_ERRORS = { too_long: 'too_long', empty: 'rejected', binary: 'rejected', invalid_encoding: 'rejected' };

async function onDevice(request) {
  const engine = await import('../engine/index.js');
  return engine.analyzeOffline(request);
}

async function recognize(file, onProgress) {
  const ocr = await import('../ocr.js');
  return ocr.recognize(file, onProgress);
}
