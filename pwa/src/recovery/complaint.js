// The complaint template on the phone: the same catalogue text and formatting as the server's ComplaintDrafter.
// Only fixed choices, a date, and an amount go in; the user adds their own words by editing the draft afterwards.

export const CHOICES = {
  channel: ['upi', 'bank_transfer', 'card', 'wallet', 'cash_deposit', 'other'],
  platform: ['whatsapp', 'telegram', 'instagram', 'facebook', 'youtube', 'sms', 'phone_call', 'website', 'app', 'other'],
  payee_id_type: ['upi_id', 'bank_account', 'phone_number', 'qr_code', 'website', 'app', 'other'],
};

export const MAX_WORDS = 200;
export const MAX_AMOUNT = 1_000_000_000;
export const EARLIEST_DATE = '2000-01-01';

/** Indian digit grouping: 1,25,000. */
export function rupees(amount) {
  const digits = String(amount);
  if (digits.length <= 3) return digits;
  const head = digits.slice(0, -3);
  let out = '';
  for (let i = 0; i < head.length; i++) {
    if (i > 0 && (head.length - i) % 2 === 0) out += ',';
    out += head[i];
  }
  return `${out},${digits.slice(-3)}`;
}

/** YYYY-MM-DD (as from <input type="date">) to DD-MM-YYYY. */
export function formatDate(iso) {
  const [year, month, day] = iso.split('-');
  return `${day}-${month}-${year}`;
}

export const wordCount = (text) => {
  const trimmed = text.trim();
  return trimmed ? trimmed.split(/\s+/).length : 0;
};

/** Today in India as YYYY-MM-DD, the latest date the server accepts. */
export function todayInIndia(now = new Date()) {
  return new Date(now.getTime() + 330 * 60_000).toISOString().slice(0, 10);
}

/** Null when the facts are complete and in range; otherwise the first field that is not. */
export function invalidField(facts, today = todayInIndia()) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(facts.date ?? '') || facts.date < EARLIEST_DATE || facts.date > today) return 'date';
  if (!Number.isInteger(facts.amount) || facts.amount < 1 || facts.amount > MAX_AMOUNT) return 'amount';
  for (const field of Object.keys(CHOICES)) {
    if (!CHOICES[field].includes(facts[field])) return field;
  }
  return null;
}

export function buildDraft({ lang, date, amount, channel, platform, payee_id_type: payee }, t) {
  return t(lang, 'complaint.draft', {
    date: formatDate(date),
    amount: rupees(amount),
    channel: t(lang, `complaint.channel.${channel}`),
    platform: t(lang, `complaint.platform.${platform}`),
    payee: t(lang, `complaint.payee.${payee}`),
  });
}
