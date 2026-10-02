// Track D pause journal. Lives in this phone's localStorage only: there is no journal API route, nothing here calls
// the network, and reminders carry fixed text, never what the user wrote.

export const KEY = 'ruko.journal.v1';
export const WAIT_MS = 24 * 60 * 60 * 1000;
export const HORIZONS = ['days', 'months', 'year_plus', 'not_sure'];
export const AFFORD = ['yes', 'no', 'not_sure'];

const EMPTY = { why: '', horizon: '', afford: '', wait_until: null, notified: false };

const store = () => globalThis.localStorage;

export function loadJournal(storage = store()) {
  try {
    const saved = JSON.parse(storage.getItem(KEY) ?? 'null');
    if (!saved || typeof saved !== 'object') return { ...EMPTY };
    return {
      why: typeof saved.why === 'string' ? saved.why : '',
      horizon: HORIZONS.includes(saved.horizon) ? saved.horizon : '',
      afford: AFFORD.includes(saved.afford) ? saved.afford : '',
      wait_until: Number.isFinite(saved.wait_until) ? saved.wait_until : null,
      notified: saved.notified === true,
    };
  } catch {
    return { ...EMPTY };
  }
}

export function saveJournal(entry, storage = store()) {
  const next = { ...loadJournal(storage), ...entry };
  try {
    storage.setItem(KEY, JSON.stringify(next));
  } catch {
    // Private mode or full storage: the journal still works for this visit.
  }
  return next;
}

export function startWait(now = Date.now(), storage = store()) {
  return saveJournal({ wait_until: now + WAIT_MS, notified: false }, storage);
}

export function clearJournal(storage = store()) {
  try {
    storage.removeItem(KEY);
  } catch {
    // Nothing stored.
  }
  return { ...EMPTY };
}

/** `waiting` until the 24 hours are over, then `over` until the user clears or restarts the wait. */
export function waitState(entry, now = Date.now()) {
  if (entry.wait_until == null) return { status: 'none', remainingMs: 0 };
  const remainingMs = entry.wait_until - now;
  return remainingMs > 0 ? { status: 'waiting', remainingMs } : { status: 'over', remainingMs: 0 };
}
