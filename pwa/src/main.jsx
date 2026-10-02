import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.jsx';
import { updateDraft } from './draft.js';
import { sharedTextFrom } from './share.js';
import './styles.css';

if (window.location.pathname === '/share') {
  const shared = sharedTextFrom(new URLSearchParams(window.location.search));
  // The query string holds the unmasked message; drop it from the address bar and history.
  window.history.replaceState(null, '', '/');
  if (shared) {
    updateDraft({ text: shared, source: 'share', ocrConfidence: null });
  }
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js');
  });
}
