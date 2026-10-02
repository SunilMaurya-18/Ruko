// Self-hosts Tiro Devanagari Hindi (regular, Devanagari + Latin subsets) under public/fonts so Hindi text never
// calls Google Fonts: the CSP allows only 'self', the app must work offline, and a font CDN would see every visit.
// The files are generated, so public/fonts is git-ignored.
import { copyFileSync, mkdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const out = fileURLToPath(new URL('../public/fonts/', import.meta.url));
const pkg = dirname(require.resolve('@fontsource/tiro-devanagari-hindi/package.json'));

const files = [
  ...['devanagari', 'latin'].map((subset) => {
    const file = `tiro-devanagari-hindi-${subset}-400-normal.woff2`;
    return [join(pkg, 'files', file), file];
  }),
  [join(pkg, 'LICENSE'), 'tiro-devanagari-hindi-OFL.txt'],
];

mkdirSync(out, { recursive: true });
for (const [from, to] of files) {
  copyFileSync(from, join(out, to));
}
console.log(`copied ${files.length} font assets to public/fonts`);
