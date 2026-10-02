import { loadJournal, saveJournal, waitState } from './journal.js';

// Optional local reminder when the 24-hour wait ends. Shown by this phone only, with fixed text; there is no push
// server, so it appears while Ruko is open or the next time it is opened after the wait.

const TEXT = {
  hi: { title: 'रुको: 24 घंटे पूरे हुए', body: 'अब अपनी डायरी के जवाब फिर से पढ़ें, फिर फ़ैसला करें।' },
  en: { title: 'Ruko: 24 hours are up', body: 'Read your journal answers again, then decide.' },
};

export const canNotify = () => typeof Notification !== 'undefined';

export async function askPermission() {
  if (!canNotify()) return 'unsupported';
  if (Notification.permission !== 'default') return Notification.permission;
  try {
    return await Notification.requestPermission();
  } catch {
    return 'denied';
  }
}

async function show(lang) {
  const { title, body } = TEXT[lang] ?? TEXT.hi;
  const options = { body, lang, tag: 'ruko-journal', icon: '/icons/icon-192.png' };
  const registration = await navigator.serviceWorker?.getRegistration?.();
  if (registration) {
    await registration.showNotification(title, options);
  } else {
    new Notification(title, options);
  }
}

/** Notifies once when the wait is over (if allowed), and re-arms itself while the app stays open. */
export function watchReminder(currentLang) {
  let timer = null;
  const check = () => {
    clearTimeout(timer);
    const entry = loadJournal();
    const state = waitState(entry);
    if (state.status === 'waiting') {
      timer = setTimeout(check, Math.min(state.remainingMs + 1000, 2 ** 31 - 1));
    } else if (state.status === 'over' && !entry.notified && canNotify() && Notification.permission === 'granted') {
      saveJournal({ notified: true });
      show(currentLang()).catch(() => {});
    }
  };
  check();
  document.addEventListener('visibilitychange', check);
  window.addEventListener('ruko:journal', check);
  return () => {
    clearTimeout(timer);
    document.removeEventListener('visibilitychange', check);
    window.removeEventListener('ruko:journal', check);
  };
}
