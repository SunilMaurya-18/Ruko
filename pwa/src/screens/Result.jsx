import { useEffect, useRef, useState } from 'react';
import { useDraft } from '../draft.js';
import { t } from '../i18n/catalog.js';
import Icon from '../icons.jsx';
import { SEBI_CHECK_URL, ui } from '../labels.js';
import { Link } from '../router.jsx';
import { speak, stopSpeaking } from '../voice/player.js';
import { segments } from '../voice/script.js';

const PAUSE_BANDS = new Set(['high_concern', 'some_concern']);
const BAND_ICONS = { high_concern: 'alert', some_concern: 'caution', few_flags_still_verify: 'info' };

function Listen({ result, words }) {
  const [speaking, setSpeaking] = useState(false);
  const [engine, setEngine] = useState(null);
  const runId = useRef(0);

  useEffect(() => stopSpeaking, []);

  async function toggle() {
    const id = ++runId.current;
    if (speaking) {
      stopSpeaking();
      setSpeaking(false);
      return;
    }
    setSpeaking(true);
    setEngine(null);
    const lang = result.language;
    await speak(result, segments(result, (key, params) => t(lang, key, params)), (used) => {
      if (runId.current === id) setEngine(used);
    });
    if (runId.current === id) setSpeaking(false);
  }

  return (
    <div className="listen">
      <button type="button" className="button touch" onClick={toggle} aria-describedby="listen-status">
        <Icon name={speaking ? 'stop' : 'play'} />
        {speaking ? words.stop : words.listen}
      </button>
      <p id="listen-status" className="hint" aria-live="polite">
        {engine === 'phone' ? words.phoneVoice : engine === 'none' ? words.noVoice : words.listenHint}
      </p>
    </div>
  );
}

function Evidence({ text, label }) {
  return (
    <blockquote className="evidence">
      <span className="visually-hidden">{label}</span>
      “{text}”
    </blockquote>
  );
}

function CopyButton({ value, words }) {
  const [copied, setCopied] = useState(false);
  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }
  return (
    <button type="button" className="button button-secondary touch" onClick={copy} aria-live="polite"
      aria-label={copied ? words.copied : words.copyNumber(value)}>
      {copied ? words.copied : words.copy}
    </button>
  );
}

function Empty({ lang }) {
  const words = ui(lang);
  return (
    <section aria-labelledby="result-title" lang={lang}>
      <h1 id="result-title" tabIndex={-1}>{words.result}</h1>
      <p>{words.empty}</p>
      <ul className="actions">
        <li>
          <Link to="/" className="button touch">{words.check}</Link>
        </li>
        <li>
          <Link to="/recovery" className="button button-secondary touch">{words.recovery}</Link>
        </li>
      </ul>
    </section>
  );
}

export default function Result() {
  const { result, sentText, lang: appLang } = useDraft();
  if (!result) {
    return <Empty lang={appLang} />;
  }

  const lang = result.language;
  const words = ui(lang);
  const { entities, signals, unverified, reassuring, band, content_class: contentClass, counts, cards } = result;
  const cardFor = Object.fromEntries(cards.filter((card) => card.signal_id).map((card) => [card.signal_id, card.text]));
  const notice = cards.find((card) => !card.signal_id);
  const analogy = result.analogy_key ? t(lang, result.analogy_key) : null;
  const u14Card = unverified.length > 0 ? cardFor[unverified[0].id] : null;
  const found = Object.entries(words.entities).filter(([key]) => entities[key]?.length > 0);

  return (
    <section aria-labelledby="result-title" lang={lang}>
      <h1 id="result-title" tabIndex={-1}>{words.result}</h1>

      {appLang !== lang && (
        <div className="notice" lang={appLang}>
          <p>{ui(appLang).otherLanguage}</p>
          <Link to="/" className="button button-secondary touch">{ui(appLang).check}</Link>
        </div>
      )}

      <div className={`band band-${band}`} role="status">
        <span className="band-icon"><Icon name={BAND_ICONS[band] ?? 'unknown'} /></span>
        <div className="band-text">
          <p className="band-label">{t(lang, `band.${band}`)}</p>
          <p className="band-hint">{t(lang, `band.${band}.hint`)}</p>
          <p className="band-counts">{words.counts(counts)}</p>
        </div>
      </div>

      {result.engine === 'on_device' && <p className="notice">{words.onDevice}</p>}

      <Listen result={result} words={words} />

      <p className="content-class">{t(lang, `class.${contentClass}`)}</p>

      {notice && <p className="notice">{notice.text}</p>}

      {PAUSE_BANDS.has(band) && (
        <div className="pause">
          <h2 className="with-icon"><Icon name="clock" />{words.pause}</h2>
          <p>{words.pauseHint}</p>
          <Link to="/journal" className="button touch">{words.pauseLink}</Link>
        </div>
      )}

      {signals.length > 0 && (
        <>
          <h2>{words.flags}</h2>
          <ol className="flags">
            {signals.map((signal) => (
              <li key={signal.id} className={`flag flag-${signal.severity}`}>
                <p className="flag-reason">{cardFor[signal.id] ?? signal.reason}</p>
                <Evidence text={signal.evidence} label={words.evidence} />
              </li>
            ))}
          </ol>
        </>
      )}

      {analogy && (
        <>
          <h2>{words.analogy}</h2>
          <p className="analogy">{analogy}</p>
        </>
      )}

      {unverified.length > 0 && (
        <>
          <h2>{words.unverified}</h2>
          {u14Card && <p>{u14Card}</p>}
          <ul className="flags">
            {unverified.map((item) => (
              <li key={`${item.id}-${item.item}`} className="flag flag-unverified">
                <p>
                  {words.regNumber}: <span className="mono">{item.item}</span>
                </p>
                {item.snapshot && <p>{t(lang, `snapshot.${item.snapshot}`)}</p>}
                {item.action === 'sebi_check' && (
                  <div className="row">
                    <a className="button touch" href={SEBI_CHECK_URL} target="_blank" rel="noopener noreferrer"
                      aria-label={`${words.sebiCheck}, ${words.opensSite}`}>
                      {words.sebiCheck}
                    </a>
                    <CopyButton value={item.item} words={words} />
                  </div>
                )}
              </li>
            ))}
          </ul>
        </>
      )}

      {reassuring.length > 0 && (
        <>
          <h2>{words.reassuring}</h2>
          <p className="hint">{words.reassuringHint}</p>
          <ul className="flags">
            {reassuring.map((item) => (
              <li key={item.id} className="flag flag-reassurance">
                <p className="flag-reason">{cardFor[item.id] ?? item.reason}</p>
                <Evidence text={item.evidence} label={words.evidence} />
              </li>
            ))}
          </ul>
        </>
      )}

      <p className="footer-note">{t(lang, `footer.${result.footer_key}`)}</p>

      <ul className="actions">
        <li>
          <Link to="/recovery" className="button touch">{words.recovery}</Link>
        </li>
        <li>
          <Link to="/" className="button button-secondary touch">{words.again}</Link>
        </li>
        <li>
          <Link to="/how-ruko-decides" className="button button-secondary touch">{words.how}</Link>
        </li>
      </ul>

      <details className="details card">
        <summary className="touch">{words.details}</summary>
        {found.length === 0 && entities.phone_count === 0 ? (
          <p>{words.nothingFound}</p>
        ) : (
          <dl className="facts">
            {found.map(([key, label]) => (
              <div key={key} className="fact">
                <dt>{label}</dt>
                {entities[key].map((value) => <dd key={value} className="mono">{value}</dd>)}
              </div>
            ))}
            {entities.phone_count > 0 && (
              <div className="fact">
                <dt>{words.hiddenPhones}</dt>
                <dd>{entities.phone_count}</dd>
              </div>
            )}
          </dl>
        )}
        <p className="hint">{words.maskedNote}</p>
        <p className="sent mono">{sentText}</p>
      </details>
    </section>
  );
}
