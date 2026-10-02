// Plays a spoken result: Bhashini audio from the server when it answers, otherwise the phone's own speechSynthesis
// (hi-IN / en-IN). A server failure part-way switches the rest to the phone voice. After a failure the server is
// skipped for a minute so every Listen tap does not wait on it again.

import { tts } from '../api.js';

const SERVER_PAUSE_MS = 60_000;
const audioUrls = new Map();
let serverPausedUntil = 0;
let current = null;

export function canSpeakOnPhone() {
  return typeof window !== 'undefined' && 'speechSynthesis' in window && 'SpeechSynthesisUtterance' in window;
}

export function stopSpeaking() {
  if (current) {
    current.cancelled = true;
    current.audio.pause();
    current.finish?.();
    current = null;
  }
  if (canSpeakOnPhone()) window.speechSynthesis.cancel();
}

/**
 * Speaks `segments` ({key, text}) for `result`. `onEngine` is told 'server', 'phone', or 'none' (no voice at all).
 * Resolves when speech ends or is stopped. Must be called from the tap handler so playback is allowed.
 */
export async function speak(result, segments, onEngine) {
  stopSpeaking();
  const run = { cancelled: false, audio: new Audio(), finish: null };
  current = run;
  const lang = result.language;
  let next = 0;

  if (Date.now() >= serverPausedUntil) {
    const pending = segments.map((segment) => audioFor(segment.key, lang, result.counts));
    for (; next < segments.length; next++) {
      const url = await pending[next];
      if (run.cancelled) return;
      if (!url) {
        serverPausedUntil = Date.now() + SERVER_PAUSE_MS;
        break;
      }
      if (next === 0) onEngine('server');
      if (!(await play(run, url))) break;
      if (run.cancelled) return;
    }
  }

  if (next < segments.length && !run.cancelled) {
    if (canSpeakOnPhone()) {
      onEngine('phone');
      await speakOnPhone(run, segments.slice(next).map((segment) => segment.text), lang);
    } else {
      onEngine('none');
    }
  }
  if (current === run) current = null;
}

/** Audio is catalogue speech only, so it is safe to keep for the session. Counts matter only for the band line. */
async function audioFor(key, lang, counts) {
  const id = `${lang}|${key}|${key.startsWith('band.') ? `${counts.red_flags},${counts.couldnt_verify},${counts.reassuring}` : ''}`;
  if (audioUrls.has(id)) return audioUrls.get(id);
  const blob = await tts({ scriptKey: key, lang, counts });
  if (!blob) return null;
  const url = URL.createObjectURL(blob);
  audioUrls.set(id, url);
  return url;
}

function play(run, url) {
  return new Promise((resolve) => {
    const done = (ok) => {
      run.audio.onended = null;
      run.audio.onerror = null;
      run.finish = null;
      resolve(ok);
    };
    run.finish = () => done(false);
    run.audio.onended = () => done(true);
    run.audio.onerror = () => done(false);
    run.audio.src = url;
    run.audio.play().catch(() => done(false));
  });
}

function phoneVoice(lang) {
  const tag = lang === 'hi' ? 'hi-in' : 'en-in';
  const voices = window.speechSynthesis.getVoices();
  return voices.find((voice) => voice.lang?.toLowerCase().replace('_', '-') === tag)
    ?? voices.find((voice) => voice.lang?.toLowerCase().startsWith(lang))
    ?? null;
}

function speakOnPhone(run, texts, lang) {
  return new Promise((resolve) => {
    const synth = window.speechSynthesis;
    const voice = phoneVoice(lang);
    run.finish = resolve;
    texts.forEach((text, index) => {
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = lang === 'hi' ? 'hi-IN' : 'en-IN';
      if (voice) utterance.voice = voice;
      if (index === texts.length - 1) {
        utterance.onend = () => resolve();
        utterance.onerror = () => resolve();
      }
      synth.speak(utterance);
    });
  });
}
