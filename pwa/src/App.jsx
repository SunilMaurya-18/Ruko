import { useEffect, useRef } from 'react';
import { getDraft, setLang, useDraft } from './draft.js';
import Icon from './icons.jsx';
import { watchReminder } from './journal/reminder.js';
import { LANG_OPTIONS, ui } from './labels.js';
import { Link, usePath } from './router.jsx';
import Home from './screens/Home.jsx';
import HowRukoDecides from './screens/HowRukoDecides.jsx';
import Journal from './screens/Journal.jsx';
import NotFound from './screens/NotFound.jsx';
import Recovery from './screens/Recovery.jsx';
import Result from './screens/Result.jsx';

const ROUTES = {
  '/': Home,
  '/share': Home,
  '/result': Result,
  '/recovery': Recovery,
  '/journal': Journal,
  '/how-ruko-decides': HowRukoDecides,
};

const NAV = ['/', '/recovery', '/journal', '/how-ruko-decides'];
const NAV_ICONS = { '/': 'home', '/recovery': 'help', '/journal': 'journal', '/how-ruko-decides': 'method' };

function LanguageSwitch({ lang, label }) {
  return (
    <div className="lang-switch" role="group" aria-label={label}>
      {LANG_OPTIONS.map(([value, name]) => (
        <button key={value} type="button" lang={value} className="lang-option touch" aria-pressed={lang === value}
          onClick={() => setLang(value)}>
          {name}
        </button>
      ))}
    </div>
  );
}

export default function App() {
  const path = usePath();
  const { lang } = useDraft();
  const Screen = ROUTES[path] ?? NotFound;
  const words = ui(lang).app;
  const title = words.titles[path === '/share' ? '/' : path] ?? words.titles.notFound;
  const mainRef = useRef(null);
  const firstRender = useRef(true);

  useEffect(() => watchReminder(() => getDraft().lang), []);

  useEffect(() => {
    document.documentElement.lang = lang;
    document.title = title;
  }, [lang, title]);

  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    mainRef.current?.querySelector('h1')?.focus();
  }, [path]);

  return (
    <div className="app">
      <a className="skip-link touch" href="#main">{words.skip}</a>
      <header className="app-header">
        <Link to="/" className="brand touch" aria-label={words.home}>
          <span className="brand-mark"><Icon name="shield" /></span>
          {words.brand}
        </Link>
        <LanguageSwitch lang={lang} label={words.language} />
      </header>
      <main id="main" ref={mainRef} className="app-main">
        <Screen />
      </main>
      <nav className="app-nav" aria-label={words.menu}>
        <ul>
          {NAV.map((to) => (
            <li key={to}>
              <Link to={to} className="nav-link touch" aria-current={path === to ? 'page' : undefined}>
                <Icon name={NAV_ICONS[to]} />
                <span>{words.nav[to]}</span>
              </Link>
            </li>
          ))}
        </ul>
      </nav>
      <footer className="app-footer">
        <p>{words.footer}</p>
      </footer>
    </div>
  );
}
