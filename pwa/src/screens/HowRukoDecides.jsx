import { useEffect, useState } from 'react';
import { howRukoDecides } from '../api.js';
import { useDraft } from '../draft.js';
import { t } from '../i18n/catalog.js';

const BANDS = ['high_concern', 'some_concern', 'few_flags_still_verify', 'not_enough_to_judge'];

const WORDS = {
  hi: { bands: 'नतीजे के स्तर', signals: 'रुको किन निशानों को देखता है', limits: 'रुको क्या नहीं करता', snapshot: 'SEBI सूची', loading: 'जानकारी आ रही है…', offline: 'इंटरनेट नहीं है, इसलिए निशानों की सूची अभी नहीं दिख रही।' },
  en: { bands: 'Result levels', signals: 'The signs Ruko looks for', limits: 'What Ruko does not do', snapshot: 'SEBI list', loading: 'Loading…', offline: 'You are offline, so the list of signs cannot be shown right now.' },
};

/** Without the server, the page still shows the bundled intro, levels, and limits; only the signal list needs it. */
function offlinePage(lang) {
  const limits = [];
  for (let i = 1; t(lang, `how.limit.${i}`); i++) {
    limits.push(t(lang, `how.limit.${i}`));
  }
  return {
    title: t(lang, 'how.title'),
    intro: t(lang, 'how.intro'),
    bands: BANDS.map((band) => ({ band, label: t(lang, `band.${band}`) })),
    bands_note: t(lang, 'how.bands'),
    signals: null,
    limits,
    snapshot_note: null,
  };
}

export default function HowRukoDecides() {
  const { lang } = useDraft();
  const [state, setState] = useState({ lang: null, page: null });

  useEffect(() => {
    let live = true;
    howRukoDecides(lang).then((page) => {
      if (live) setState({ lang, page });
    });
    return () => {
      live = false;
    };
  }, [lang]);

  const loading = state.lang !== lang;
  const page = !loading && state.page ? state.page : offlinePage(lang);
  const words = WORDS[lang] ?? WORDS.hi;

  return (
    <section aria-labelledby="how-title" lang={lang}>
      <h1 id="how-title" tabIndex={-1}>{page.title}</h1>
      <p className="lead">{page.intro}</p>

      <div className="card">
        <h2>{words.bands}</h2>
        <ul className="plain-list">
          {page.bands.map((line) => <li key={line.band}>{line.label}</li>)}
        </ul>
        <p className="hint">{page.bands_note}</p>
      </div>

      <h2>{words.signals}</h2>
      {page.signals ? (
        <ul className="flags">
          {page.signals.map((signal) => (
            <li key={signal.id} className={`flag flag-${signal.severity}`}>
              <p className="flag-reason">{signal.reason}</p>
              <p className="hint mono">{signal.id}</p>
            </li>
          ))}
        </ul>
      ) : (
        <p className="hint" role="status">{loading ? words.loading : words.offline}</p>
      )}

      <div className="card">
        <h2>{words.limits}</h2>
        <ul className="plain-list">
          {page.limits.map((limit) => <li key={limit}>{limit}</li>)}
        </ul>
      </div>

      {page.snapshot_note && (
        <div className="card">
          <h2>{words.snapshot}</h2>
          <p>{page.snapshot_note}</p>
        </div>
      )}
    </section>
  );
}
