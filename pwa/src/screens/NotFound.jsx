import { useDraft } from '../draft.js';
import { ui } from '../labels.js';
import { Link } from '../router.jsx';

export default function NotFound() {
  const { lang } = useDraft();
  const words = ui(lang).app;
  return (
    <section aria-labelledby="not-found-title" lang={lang}>
      <h1 id="not-found-title" tabIndex={-1}>{words.notFound}</h1>
      <ul className="actions">
        <li>
          <Link to="/" className="button touch">{words.goHome}</Link>
        </li>
      </ul>
    </section>
  );
}
