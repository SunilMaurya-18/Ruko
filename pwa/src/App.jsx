import { useEffect, useRef } from 'react';
import { getDraft } from './draft.js';
import { watchReminder } from './journal/reminder.js';
import { Link, usePath } from './router.jsx';
import Home from './screens/Home.jsx';
import HowRukoDecides from './screens/HowRukoDecides.jsx';
import Journal from './screens/Journal.jsx';
import NotFound from './screens/NotFound.jsx';
import Recovery from './screens/Recovery.jsx';
import Result from './screens/Result.jsx';

const ROUTES = {
  '/': { Screen: Home, lang: 'hi', title: 'रुको' },
  '/share': { Screen: Home, lang: 'hi', title: 'रुको' },
  '/result': { Screen: Result, lang: 'hi', title: 'नतीजा · रुको' },
  '/recovery': { Screen: Recovery, lang: 'hi', title: 'मदद · रुको' },
  '/journal': { Screen: Journal, lang: 'hi', title: 'रुकें और सोचें · रुको' },
  '/how-ruko-decides': { Screen: HowRukoDecides, lang: 'hi', title: 'रुको कैसे तय करता है' },
};

const NOT_FOUND = { Screen: NotFound, lang: 'hi', title: 'पेज नहीं मिला · रुको' };

const NAV = [
  { to: '/', label: 'होम' },
  { to: '/recovery', label: 'मदद' },
  { to: '/journal', label: 'डायरी' },
  { to: '/how-ruko-decides', label: 'तरीका' },
];

export default function App() {
  const path = usePath();
  const route = ROUTES[path] ?? NOT_FOUND;
  const { Screen } = route;
  const mainRef = useRef(null);
  const firstRender = useRef(true);

  useEffect(() => watchReminder(() => getDraft().lang), []);

  useEffect(() => {
    document.documentElement.lang = route.lang;
    document.title = route.title;
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    mainRef.current?.querySelector('h1')?.focus();
  }, [route]);

  return (
    <div className="app">
      <a className="skip-link touch" href="#main">मुख्य भाग पर जाएँ</a>
      <header className="app-header">
        <Link to="/" className="brand touch" aria-label="रुको होम">रुको</Link>
      </header>
      <main id="main" ref={mainRef} lang={route.lang} className="app-main">
        <Screen />
      </main>
      <nav className="app-nav" aria-label="मुख्य मेन्यू">
        <ul>
          {NAV.map((item) => (
            <li key={item.to}>
              <Link to={item.to} className="nav-link touch" aria-current={path === item.to ? 'page' : undefined}>
                {item.label}
              </Link>
            </li>
          ))}
        </ul>
      </nav>
      <footer className="app-footer" lang="hi">
        <p>स्वतंत्र प्रोटोटाइप। SEBI या NSDL का आधिकारिक ऐप नहीं। निवेश सलाह नहीं।</p>
      </footer>
    </div>
  );
}
