import { useSyncExternalStore } from 'react';

const LANG_KEY = 'ruko.lang';
const LANGS = new Set(['hi', 'en']);

/**
 * The message being checked. Held in memory only: the unmasked text never goes to storage or the network,
 * so it can still be copied on the phone (for example a UPI id or account for SEBI Check).
 * `lang` is the app language, which is also the answer language; it is the only field kept in storage.
 */
let state = {
  text: '',
  source: 'paste',
  ocrConfidence: null,
  lang: savedLang(),
  sentText: null,
  result: null,
};

function savedLang() {
  try {
    const lang = globalThis.localStorage?.getItem(LANG_KEY);
    return LANGS.has(lang) ? lang : 'hi';
  } catch {
    return 'hi';
  }
}

export function setLang(lang) {
  if (!LANGS.has(lang)) return;
  try {
    globalThis.localStorage?.setItem(LANG_KEY, lang);
  } catch {
    // Storage blocked (private mode): the choice lasts for this visit only.
  }
  updateDraft({ lang });
}

const listeners = new Set();

export function getDraft() {
  return state;
}

export function updateDraft(patch) {
  state = { ...state, ...patch };
  listeners.forEach((listener) => listener());
}

export function useDraft() {
  return useSyncExternalStore(subscribe, getDraft);
}

function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
