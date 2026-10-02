import { createHash } from 'node:crypto';
import { readdirSync, readFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

const root = fileURLToPath(new URL('.', import.meta.url));
const publicDir = join(root, 'public');

function publicFiles() {
  return readdirSync(publicDir, { recursive: true, withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => join(entry.parentPath, entry.name));
}

/** Emits sw.js from sw/sw.js with the built asset list, so the whole shell is precached on install. */
function serviceWorker() {
  return {
    name: 'ruko-service-worker',
    apply: 'build',
    enforce: 'post',
    generateBundle(_options, bundle) {
      const hash = createHash('sha256');
      // OCR assets are megabytes; the service worker caches them on first use instead of on install.
      const fromPublic = publicFiles()
        .map((file) => `/${relative(publicDir, file).split(sep).join('/')}`)
        .filter((path) => !path.startsWith('/ocr/'));
      fromPublic.forEach((path) => hash.update(readFileSync(join(publicDir, path))));
      const fromBundle = Object.keys(bundle).map((file) => `/${file}`);
      const precache = ['/', ...new Set([...fromBundle, ...fromPublic])].sort();
      hash.update(JSON.stringify(precache));

      const source = readFileSync(join(root, 'sw', 'sw.js'), 'utf8')
        .replace('__VERSION__', hash.digest('hex').slice(0, 12))
        .replace('__PRECACHE__', JSON.stringify(precache));
      this.emitFile({ type: 'asset', fileName: 'sw.js', source });
    },
  };
}

export default defineConfig({
  plugins: [react(), serviceWorker()],
  server: {
    proxy: { '/api': `http://localhost:${process.env.PORT ?? 8081}` },
    fs: { allow: ['..'] },
  },
  build: {
    target: 'es2020',
  },
});
