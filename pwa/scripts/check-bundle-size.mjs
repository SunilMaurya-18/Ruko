import { readdirSync, readFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

const BUDGET_BYTES = 200 * 1024;
// Assets fetched lazily after the shell is interactive (OCR language data, the font-display: swap Hindi font)
// are not part of the shell budget.
const LAZY = [/^ocr\//, /^fonts\//];

const dist = fileURLToPath(new URL('../dist/', import.meta.url));

const files = readdirSync(dist, { recursive: true, withFileTypes: true })
  .filter((entry) => entry.isFile())
  .map((entry) => relative(dist, join(entry.parentPath, entry.name)).split(sep).join('/'))
  .filter((file) => !LAZY.some((pattern) => pattern.test(file)))
  .map((file) => ({ file, gzip: gzipSync(readFileSync(join(dist, file)), { level: 9 }).length }))
  .sort((a, b) => b.gzip - a.gzip);

const total = files.reduce((sum, { gzip }) => sum + gzip, 0);
const kb = (bytes) => `${(bytes / 1024).toFixed(1)} KB`;

for (const { file, gzip } of files) {
  console.log(`${kb(gzip).padStart(10)}  ${file}`);
}
console.log(`${kb(total).padStart(10)}  total gzipped (budget ${kb(BUDGET_BYTES)})`);

if (total > BUDGET_BYTES) {
  console.error(`Shell is ${kb(total - BUDGET_BYTES)} over budget.`);
  process.exit(1);
}
