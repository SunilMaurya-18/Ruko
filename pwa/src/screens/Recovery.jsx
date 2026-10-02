import { useId, useState } from 'react';
import { complaintDraft } from '../api.js';
import { useDraft } from '../draft.js';
import { t } from '../i18n/catalog.js';
import { CHOICES, MAX_AMOUNT, buildDraft, invalidField, todayInIndia } from '../recovery/complaint.js';
import { recoveryPaths } from '../recovery/content.js';

const WORDS = {
  hi: {
    complaint: 'शिकायत का ड्राफ़्ट',
    complaintHint: 'नीचे चुनें कि क्या हुआ। ड्राफ़्ट इसी फ़ोन पर बनता है; बाकी बातें आप खुद जोड़ें। आपका लिखा हुआ कहीं नहीं भेजा जाता।',
    date: 'पैसे किस तारीख को भेजे?',
    amount: 'कितने रुपये भेजे?',
    channel: 'किस तरीके से भेजे?',
    platform: 'ऑफ़र कहाँ मिला?',
    payee: 'पैसे किस पर भेजे?',
    choose: 'चुनें',
    make: 'ड्राफ़्ट बनाएँ',
    making: 'ड्राफ़्ट बन रहा है…',
    draft: 'आपका ड्राफ़्ट (बदल सकते हैं)',
    fill: 'भेजने से पहले [ ] वाली जगहों में अपनी जानकारी भरें।',
    copy: 'कॉपी करें',
    copied: 'कॉपी हो गया',
    share: 'शेयर करें',
    invalid: {
      date: 'आज या उससे पहले की तारीख चुनें।',
      amount: `रकम 1 से ${MAX_AMOUNT.toLocaleString('en-IN')} रुपये के बीच, बिना पैसे (दशमलव) के लिखें।`,
      channel: 'पैसे भेजने का तरीका चुनें।',
      platform: 'ऑफ़र कहाँ मिला, यह चुनें।',
      payee_id_type: 'पैसे किस पर भेजे, यह चुनें।',
    },
  },
  en: {
    complaint: 'Complaint draft',
    complaintHint: 'Choose what happened. The draft is made on this phone; add the rest yourself. Nothing you type is sent anywhere.',
    date: 'On what date did you pay?',
    amount: 'How many rupees did you pay?',
    channel: 'How did you pay?',
    platform: 'Where did the offer reach you?',
    payee: 'What did you pay to?',
    choose: 'Choose',
    make: 'Make the draft',
    making: 'Making the draft…',
    draft: 'Your draft (you can edit it)',
    fill: 'Fill in the [ ] places before you send it.',
    copy: 'Copy',
    copied: 'Copied',
    share: 'Share',
    invalid: {
      date: 'Choose today or an earlier date.',
      amount: `Enter a whole number of rupees from 1 to ${MAX_AMOUNT.toLocaleString('en-IN')}.`,
      channel: 'Choose how you paid.',
      platform: 'Choose where the offer reached you.',
      payee_id_type: 'Choose what you paid to.',
    },
  },
};

function StepLink({ link }) {
  if (link.url.startsWith('tel:')) {
    return <a className="button touch" href={link.url}>{link.label}</a>;
  }
  return (
    <a className="button button-secondary touch" href={link.url} target="_blank" rel="noopener noreferrer">
      {link.label}
    </a>
  );
}

function Select({ id, label, field, value, onChange, lang, words }) {
  return (
    <>
      <label htmlFor={id} className="field-label">{label}</label>
      <select id={id} className="select touch" value={value} onChange={(e) => onChange(field, e.target.value)}>
        <option value="">{words.choose}</option>
        {CHOICES[field].map((choice) => (
          <option key={choice} value={choice}>{t(lang, `complaint.${field === 'payee_id_type' ? 'payee' : field}.${choice}`)}</option>
        ))}
      </select>
    </>
  );
}

function ComplaintForm({ lang, words }) {
  const ids = { date: useId(), amount: useId(), channel: useId(), platform: useId(), payee: useId(), draft: useId() };
  const [facts, setFacts] = useState({ date: '', amount: '', channel: '', platform: '', payee_id_type: '' });
  const [invalid, setInvalid] = useState(null);
  const [busy, setBusy] = useState(false);
  const [text, setText] = useState('');
  const [copied, setCopied] = useState(false);
  const today = todayInIndia();
  const canShare = typeof navigator !== 'undefined' && Boolean(navigator.share);

  const update = (field, value) => {
    setFacts((current) => ({ ...current, [field]: value }));
    setInvalid(null);
  };

  const make = async (event) => {
    event.preventDefault();
    const amount = /^\d+$/.test(facts.amount.trim()) ? Number(facts.amount.trim()) : Number.NaN;
    const complete = { ...facts, lang, amount };
    const problem = invalidField(complete, today);
    if (problem) {
      setInvalid(problem);
      return;
    }
    setBusy(true);
    const fromServer = navigator.onLine === false ? null : await complaintDraft(complete);
    setText(fromServer ?? buildDraft(complete, t));
    setCopied(false);
    setBusy(false);
  };

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  const share = async () => {
    try {
      await navigator.share({ text });
    } catch {
      // Cancelled or not allowed; the text is still in the box to copy.
    }
  };

  return (
    <section aria-labelledby="complaint-title" className="card">
      <h2 id="complaint-title">{words.complaint}</h2>
      <p className="hint">{words.complaintHint}</p>
      <form className="check-form complaint-form" onSubmit={make} noValidate>
        <label htmlFor={ids.date} className="field-label">{words.date}</label>
        <input id={ids.date} className="input touch" type="date" min="2000-01-01" max={today} value={facts.date}
          onChange={(e) => update('date', e.target.value)} aria-invalid={invalid === 'date' || undefined} />

        <label htmlFor={ids.amount} className="field-label">{words.amount}</label>
        <input id={ids.amount} className="input touch" inputMode="numeric" pattern="[0-9]*" autoComplete="off"
          value={facts.amount} onChange={(e) => update('amount', e.target.value)} aria-invalid={invalid === 'amount' || undefined} />

        <Select id={ids.channel} label={words.channel} field="channel" value={facts.channel} onChange={update} lang={lang} words={words} />
        <Select id={ids.platform} label={words.platform} field="platform" value={facts.platform} onChange={update} lang={lang} words={words} />
        <Select id={ids.payee} label={words.payee} field="payee_id_type" value={facts.payee_id_type} onChange={update} lang={lang} words={words} />

        {invalid && <p className="error" role="alert">{words.invalid[invalid]}</p>}
        <button type="submit" className="button touch" disabled={busy} aria-busy={busy}>{busy ? words.making : words.make}</button>
      </form>

      {text && (
        <div className="check-form complaint-form">
          <label htmlFor={ids.draft} className="field-label">{words.draft}</label>
          <p className="hint">{words.fill}</p>
          <textarea id={ids.draft} className="transcript" rows={14} value={text} lang={lang} spellCheck={false}
            onChange={(e) => { setText(e.target.value); setCopied(false); }} />
          <div className="row">
            <button type="button" className="button touch" onClick={copy} aria-live="polite">{copied ? words.copied : words.copy}</button>
            {canShare && <button type="button" className="button button-secondary touch" onClick={share}>{words.share}</button>}
          </div>
        </div>
      )}
    </section>
  );
}

export default function Recovery() {
  const { lang } = useDraft();
  const words = WORDS[lang] ?? WORDS.hi;
  const paths = recoveryPaths(lang, t);

  return (
    <section aria-labelledby="recovery-title" lang={lang}>
      <h1 id="recovery-title" tabIndex={-1}>{t(lang, 'recovery.title')}</h1>
      <p className="lead">{t(lang, 'recovery.intro')}</p>

      {paths.map((path) => (
        <section key={path.id} aria-labelledby={`recovery-${path.id}`} className="recovery-path card">
          <h2 id={`recovery-${path.id}`}>{path.title}</h2>
          <ol className="steps">
            {path.steps.map((step) => (
              <li key={step.text} className="step">
                <p>{step.text}</p>
                {step.link && <StepLink link={step.link} />}
              </li>
            ))}
          </ol>
        </section>
      ))}

      <ComplaintForm lang={lang} words={words} />
    </section>
  );
}
