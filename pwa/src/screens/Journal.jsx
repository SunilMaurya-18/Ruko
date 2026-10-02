import { useEffect, useId, useState } from 'react';
import { useDraft } from '../draft.js';
import { AFFORD, HORIZONS, clearJournal, loadJournal, saveJournal, startWait, waitState } from '../journal/journal.js';
import { askPermission, canNotify } from '../journal/reminder.js';
import { Link } from '../router.jsx';

const WORDS = {
  hi: {
    title: 'रुकें और सोचें',
    intro: 'पैसे भेजने से पहले इन तीन सवालों के जवाब लिखें। जवाब सिर्फ इसी फ़ोन पर रहते हैं; रुको इन्हें कहीं नहीं भेजता।',
    why: 'आप पैसे क्यों भेजना चाहते हैं?',
    horizon: 'यह पैसा कितने समय के लिए है?',
    horizons: { days: 'कुछ दिन', months: 'कुछ महीने', year_plus: 'एक साल या ज़्यादा', not_sure: 'पता नहीं' },
    afford: 'अगर यह पूरा पैसा चला जाए, तो क्या आप सह पाएँगे?',
    affords: { yes: 'हाँ', no: 'नहीं', not_sure: 'पता नहीं' },
    cannotAfford: 'अगर यह पैसा जाने से घर में मुश्किल होगी, तो अभी न भेजें। पहले परिवार के किसी सदस्य से बात करें।',
    wait: '24 घंटे रुकें',
    waiting: (time) => `${time} के बाद अपने जवाब फिर से पढ़ें, फिर फ़ैसला करें।`,
    over: '24 घंटे पूरे हुए। अपने जवाब फिर से पढ़ें, फिर फ़ैसला करें।',
    remind: 'इस फ़ोन पर याद दिलाएँ',
    remindHint: 'याद दिलाने की सूचना तभी दिखेगी जब रुको खुला हो या आप इसे दोबारा खोलें।',
    reminderOn: 'याद दिलाना चालू है।',
    reminderOff: 'इस फ़ोन पर सूचना की अनुमति नहीं है। तय समय पर रुको खोलकर देखें।',
    clear: 'जवाब मिटाएँ',
    saved: 'जवाब इसी फ़ोन पर सहेजे गए।',
    help: 'पैसे भेज चुके हैं? मदद लें',
  },
  en: {
    title: 'Pause and think',
    intro: 'Before you send money, answer these three questions. Your answers stay on this phone only; Ruko never sends them anywhere.',
    why: 'Why do you want to send this money?',
    horizon: 'How long is this money meant for?',
    horizons: { days: 'A few days', months: 'A few months', year_plus: 'A year or more', not_sure: 'Not sure' },
    afford: 'If all of this money were lost, could you manage?',
    affords: { yes: 'Yes', no: 'No', not_sure: 'Not sure' },
    cannotAfford: 'If losing this money would hurt your household, do not send it now. Talk to someone in your family first.',
    wait: 'Wait 24 hours',
    waiting: (time) => `After ${time}, read your answers again, then decide.`,
    over: '24 hours are up. Read your answers again, then decide.',
    remind: 'Remind me on this phone',
    remindHint: 'The reminder shows while Ruko is open, or the next time you open it.',
    reminderOn: 'Reminder is on.',
    reminderOff: 'Notifications are not allowed on this phone. Open Ruko at that time to check.',
    clear: 'Clear my answers',
    saved: 'Answers saved on this phone.',
    help: 'Already sent money? Get help',
  },
};

const formatTime = (ms, lang) => new Date(ms).toLocaleString(lang === 'en' ? 'en-IN' : 'hi-IN',
  { weekday: 'short', hour: 'numeric', minute: '2-digit' });

export default function Journal() {
  const { lang } = useDraft();
  const words = WORDS[lang] ?? WORDS.hi;
  const ids = { why: useId(), horizon: useId(), afford: useId() };
  const [entry, setEntry] = useState(loadJournal);
  const [permission, setPermission] = useState(canNotify() ? Notification.permission : 'unsupported');
  const [now, setNow] = useState(Date.now);
  const wait = waitState(entry, now);

  useEffect(() => {
    if (wait.status !== 'waiting') return undefined;
    const timer = setTimeout(() => setNow(Date.now()), Math.min(wait.remainingMs + 1000, 60_000));
    return () => clearTimeout(timer);
  }, [wait.status, wait.remainingMs]);

  const update = (patch) => setEntry(saveJournal(patch));

  const startWaiting = () => {
    setEntry(startWait());
    setNow(Date.now());
    window.dispatchEvent(new Event('ruko:journal'));
  };

  const remind = async () => {
    setPermission(await askPermission());
    window.dispatchEvent(new Event('ruko:journal'));
  };

  return (
    <section aria-labelledby="journal-title" lang={lang}>
      <h1 id="journal-title" tabIndex={-1}>{words.title}</h1>
      <p className="lead">{words.intro}</p>

      <form className="check-form" onSubmit={(e) => e.preventDefault()} noValidate>
        <label htmlFor={ids.why} className="field-label">{words.why}</label>
        <textarea id={ids.why} className="transcript" rows={4} value={entry.why} lang={lang}
          onChange={(e) => update({ why: e.target.value })} autoComplete="off" />

        <fieldset className="lang-choice" id={ids.horizon}>
          <legend>{words.horizon}</legend>
          {HORIZONS.map((value) => (
            <label key={value} className="radio touch">
              <input type="radio" name="horizon" value={value} checked={entry.horizon === value}
                onChange={() => update({ horizon: value })} />
              {words.horizons[value]}
            </label>
          ))}
        </fieldset>

        <fieldset className="lang-choice" id={ids.afford}>
          <legend>{words.afford}</legend>
          {AFFORD.map((value) => (
            <label key={value} className="radio touch">
              <input type="radio" name="afford" value={value} checked={entry.afford === value}
                onChange={() => update({ afford: value })} />
              {words.affords[value]}
            </label>
          ))}
        </fieldset>
        {entry.afford === 'no' && <p className="notice">{words.cannotAfford}</p>}
        <p className="hint" role="status">{(entry.why || entry.horizon || entry.afford) && words.saved}</p>
      </form>

      <div className="pause">
        {wait.status === 'none' && (
          <button type="button" className="button touch" onClick={startWaiting}>{words.wait}</button>
        )}
        {wait.status === 'waiting' && <p role="status">{words.waiting(formatTime(entry.wait_until, lang))}</p>}
        {wait.status === 'over' && (
          <>
            <p role="status">{words.over}</p>
            <button type="button" className="button button-secondary touch" onClick={startWaiting}>{words.wait}</button>
          </>
        )}
        {wait.status === 'waiting' && permission !== 'unsupported' && (
          permission === 'granted' ? <p className="hint">{words.reminderOn}</p>
            : permission === 'denied' ? <p className="hint">{words.reminderOff}</p>
              : (
                <>
                  <button type="button" className="button button-secondary touch" onClick={remind}>{words.remind}</button>
                  <p className="hint">{words.remindHint}</p>
                </>
              )
        )}
      </div>

      <ul className="actions">
        <li>
          <button type="button" className="button button-secondary touch" onClick={() => setEntry(clearJournal())}>
            {words.clear}
          </button>
        </li>
        <li>
          <Link to="/recovery" className="button button-secondary touch">{words.help}</Link>
        </li>
      </ul>
    </section>
  );
}
