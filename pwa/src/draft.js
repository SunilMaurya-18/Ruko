import { useSyncExternalStore } from 'react';

/**
 * The message being checked. Held in memory only: the unmasked text never goes to storage or the network,
 * so it can still be copied on the phone (for example a UPI id or account for SEBI Check).
 */
let state = {
  text: '',
  source: 'paste',
  ocrConfidence: null,
  lang: 'hi',
  sentText: null,
  result: null,
};

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
