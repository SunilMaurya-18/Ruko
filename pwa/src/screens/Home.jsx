import { useId, useRef, useState } from 'react';
import { AnalyzeError, analyze } from '../api.js';
import { updateDraft, useDraft } from '../draft.js';
import Icon from '../icons.jsx';
import { MAX_CHARS, ui } from '../labels.js';
import { maskPii } from '../pii/mask.js';
import { Link, navigate } from '../router.jsx';

const LOW_OCR_CONFIDENCE = 0.6;

const percent = (value) => `${Math.round(value * 100)}%`;

export default function Home() {
  const draft = useDraft();
  const all = ui(draft.lang);
  const words = all.home;
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
    <section aria-labelledby="home-title" lang={draft.lang}>
      <h1 id="home-title" tabIndex={-1}>{words.title}</h1>
      <p className="lead">{words.lead}</p>

      <form className="check-form card" onSubmit={check} noValidate>
        <label htmlFor={ids.text} className="field-label">{words.message}</label>
        <p id={ids.hint} className="hint">{words.messageHint}</p>
        <textarea
          id={ids.text}
          className="transcript"
          rows={8}
          value={draft.text}
          onChange={(e) => updateDraft({ text: e.target.value, result: null, sentText: null })}
          aria-describedby={`${ids.hint} ${ids.count}`}
          aria-invalid={tooLong || undefined}
          spellCheck={false}
          autoComplete="off"
        />
        <p id={ids.count} className={tooLong ? 'count count-over' : 'count'}>
          {words.count(chars)}
        </p>

        <div className="row">
          {canPaste && (
            <button type="button" className="button button-secondary touch" onClick={paste}>
              <Icon name="paste" />{words.paste}
            </button>
          )}
          <button
            type="button"
            className="button button-secondary touch"
            onClick={() => fileInput.current?.click()}
            disabled={ocr?.stage === 'loading' || ocr?.stage === 'reading'}
          >
            <Icon name="camera" />{words.photo}
          </button>
          <input ref={fileInput} type="file" accept="image/*" className="visually-hidden" tabIndex={-1}
                 aria-hidden="true" onChange={readPhoto} />
        </div>

        <p id={ids.status} className="status" role="status" aria-live="polite">
          {ocr?.stage === 'loading' && words.ocrLoading}
          {ocr?.stage === 'reading' && words.ocrReading(percent(ocr.progress ?? 0))}
          {ocr?.stage === 'done' && (ocr.confidence < LOW_OCR_CONFIDENCE
            ? words.ocrUnclear(percent(ocr.confidence))
            : words.ocrDone(percent(ocr.confidence)))}
        </p>

        {error && <p className="error" role="alert">{words.errors[error]}</p>}

        <p className="hint">{words.answerNote}</p>
        <button type="submit" className="button button-primary touch" disabled={!draft.text.trim() || tooLong || busy}
          aria-busy={busy}>
          {busy ? <span className="spinner" aria-hidden="true" /> : <Icon name="search" />}
          {busy ? words.checking : words.check}
        </button>
        <p className="hint hint-icon"><Icon name="lock" />{words.privacy}</p>
      </form>

      <ul className="actions">
        <li>
          <Link to="/recovery" className="button button-secondary touch">{all.recovery}</Link>
        </li>
        <li>
          <Link to="/how-ruko-decides" className="button button-secondary touch">{all.how}</Link>
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
