import { Link } from '../router.jsx';

export default function NotFound() {
  return (
    <section aria-labelledby="not-found-title">
      <h1 id="not-found-title" tabIndex={-1}>पेज नहीं मिला</h1>
      <ul className="actions">
        <li>
          <Link to="/" className="button touch">होम पर जाएँ</Link>
        </li>
      </ul>
    </section>
  );
}
