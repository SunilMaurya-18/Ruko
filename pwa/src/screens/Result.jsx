import { useState } from 'react';
import { useDraft } from '../draft.js';
import {
  BAND_HINTS,
  BAND_LABELS,
  CLASS_LABELS,
  FOOTER_LABELS,
  SEBI_CHECK_URL,
  SNAPSHOT_LABELS,
  countsLine,
} from '../labels.js';
import { Link } from '../router.jsx';

const ENTITY_LABELS = [
  ['upi_ids', 'UPI आईडी'],
  ['reg_numbers', 'SEBI रजिस्ट्रेशन नंबर'],
  ['ifsc', 'IFSC कोड'],
  ['urls', 'लिंक'],
  ['return_claims', 'मुनाफे के दावे'],
  ['app_names', 'रिमोट कंट्रोल ऐप'],
  ['qr_phrases', 'QR / स्कैन'],
];

function Evidence({ text }) {
  return (
    <blockquote className="evidence">
      <span className="visually-hidden">मैसेज में लिखा है: </span>
      “{text}”
    </blockquote>
  );
}

function CopyButton({ value }) {
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
    <button type="button" className="button button-secondary touch" onClick={copy} aria-live="polite">
      {copied ? 'कॉपी हो गया' : 'नंबर कॉपी करें'}
    </button>
  );
}

function Empty() {
  return (
    <section aria-labelledby="result-title">
      <h1 id="result-title" tabIndex={-1}>नतीजा</h1>
      <p>अभी कोई मैसेज जाँचा नहीं गया है।</p>
      <ul className="actions">
        <li>
          <Link to="/" className="button touch">मैसेज जाँचें</Link>
        </li>
        <li>
          <Link to="/recovery" className="button button-secondary touch">पैसे भेज चुके हैं? मदद लें</Link>
        </li>
      </ul>
    </section>
  );
}

export default function Result() {
  const { result, sentText } = useDraft();
  if (!result) {
    return <Empty />;
  }

  const { entities, signals, unverified, reassuring, band, content_class: contentClass, counts } = result;
  const lang = result.language;
  const found = ENTITY_LABELS.filter(([key]) => entities[key]?.length > 0);

  return (
    <section aria-labelledby="result-title">
      <h1 id="result-title" tabIndex={-1}>नतीजा</h1>

      <div className={`band band-${band}`} role="status">
        <p className="band-label">{BAND_LABELS[band]}</p>
        <p className="band-hint">{BAND_HINTS[band]}</p>
        <p className="band-counts">{countsLine(counts)}</p>
      </div>

      <p className="content-class">{CLASS_LABELS[contentClass]}</p>

      {signals.length > 0 && (
        <>
          <h2>ख़तरे के निशान</h2>
          <ol className="flags">
            {signals.map((signal) => (
              <li key={signal.id} className={`flag flag-${signal.severity}`}>
                <p className="flag-reason" lang={lang}>{signal.reason}</p>
                <Evidence text={signal.evidence} />
              </li>
            ))}
          </ol>
        </>
      )}

      {unverified.length > 0 && (
        <>
          <h2>जो रुको जाँच नहीं सका</h2>
          <ul className="flags">
            {unverified.map((item) => (
              <li key={`${item.id}-${item.item}`} className="flag flag-unverified">
                <p>
                  रजिस्ट्रेशन नंबर: <span className="mono">{item.item}</span>
                </p>
                {item.snapshot && <p>{SNAPSHOT_LABELS[item.snapshot]}</p>}
                {item.action === 'sebi_check' && (
                  <div className="row">
                    <a className="button touch" href={SEBI_CHECK_URL} target="_blank" rel="noopener noreferrer">
                      SEBI Check पर देखें
                    </a>
                    <CopyButton value={item.item} />
                  </div>
                )}
              </li>
            ))}
          </ul>
        </>
      )}

      {reassuring.length > 0 && (
        <>
          <h2>भरोसे की बातें</h2>
          <p className="hint">इनसे ख़तरे के निशान कम नहीं होते।</p>
          <ul className="flags">
            {reassuring.map((item) => (
              <li key={item.id} className="flag flag-reassurance">
                <p className="flag-reason" lang={lang}>{item.reason}</p>
                <Evidence text={item.evidence} />
              </li>
            ))}
          </ul>
        </>
      )}

      <p className="footer-note">{FOOTER_LABELS[result.footer_key]}</p>

      <ul className="actions">
        <li>
          <Link to="/recovery" className="button touch">पैसे भेज चुके हैं? मदद लें</Link>
        </li>
        <li>
          <Link to="/" className="button button-secondary touch">दूसरा मैसेज जाँचें</Link>
        </li>
      </ul>

      <details className="details">
        <summary className="touch">मिली जानकारी और भेजा गया टेक्स्ट</summary>
        {found.length === 0 && entities.phone_count === 0 ? (
          <p>कोई नंबर, लिंक या UPI आईडी नहीं मिली।</p>
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
                <dt>फ़ोन नंबर (छिपे हुए)</dt>
                <dd>{entities.phone_count}</dd>
              </div>
            )}
          </dl>
        )}
        <p className="hint">निजी नंबर छिपाकर भेजे गए। असली मैसेज सिर्फ इसी फ़ोन पर है।</p>
        <p className="sent mono">{sentText}</p>
      </details>
    </section>
  );
}
