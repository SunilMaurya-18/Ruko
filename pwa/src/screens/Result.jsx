import { useDraft } from '../draft.js';
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

export default function Result() {
  const { result, sentText } = useDraft();

  if (!result) {
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

  const { entities } = result;
  const found = ENTITY_LABELS.filter(([key]) => entities[key]?.length > 0);

  return (
    <section aria-labelledby="result-title">
      <h1 id="result-title" tabIndex={-1}>नतीजा (डीबग)</h1>

      <dl className="facts">
        <dt>band</dt>
        <dd lang="en">{result.band}</dd>
        <dt>content_class</dt>
        <dd lang="en">{result.content_class}</dd>
        <dt>engine</dt>
        <dd lang="en">{result.engine}</dd>
      </dl>

      <h2>मिली जानकारी</h2>
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

      <h2>जाँच के लिए भेजा गया टेक्स्ट</h2>
      <p className="hint">निजी नंबर छिपाकर भेजे गए। असली मैसेज सिर्फ इसी फ़ोन पर है।</p>
      <p className="sent mono">{sentText}</p>

      <ul className="actions">
        <li>
          <Link to="/" className="button touch">दूसरा मैसेज जाँचें</Link>
        </li>
      </ul>
    </section>
  );
}
