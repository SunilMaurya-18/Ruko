// Self-hosts the Tesseract.js worker, wasm cores, and hin+eng data under public/ocr so OCR never calls a CDN
// and works offline once cached. The files are large and generated, so public/ocr is git-ignored.
import { copyFileSync, mkdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const out = fileURLToPath(new URL('../public/ocr/', import.meta.url));
const pkg = (name) => dirname(require.resolve(`${name}/package.json`));

const files = [
  [join(pkg('tesseract.js'), 'dist', 'worker.min.js'), 'worker.min.js'],
  ...['tesseract-core-lstm.wasm.js', 'tesseract-core-simd-lstm.wasm.js', 'tesseract-core-relaxedsimd-lstm.wasm.js']
    .map((file) => [join(pkg('tesseract.js-core'), file), join('core', file)]),
  ...['hin', 'eng'].map((lang) => [
    join(pkg(`@tesseract.js-data/${lang}`), '4.0.0_best_int', `${lang}.traineddata.gz`),
    join('lang', `${lang}.traineddata.gz`),
  ]),
];

for (const [from, to] of files) {
  mkdirSync(dirname(join(out, to)), { recursive: true });
  copyFileSync(from, join(out, to));
}
console.log(`copied ${files.length} OCR assets to public/ocr`);
