// Draws the Ruko "stop" icon (white bar on a red disc) as PNGs without image dependencies.
import { mkdirSync, writeFileSync } from 'node:fs';
import { crc32, deflateSync } from 'node:zlib';

const RED = [0xb3, 0x26, 0x1e];
const WHITE = [0xff, 0xff, 0xff];
const SAMPLES = 4;

function draw(size, { maskable }) {
  const pixels = Buffer.alloc(size * size * 4);
  const c = size / 2;
  const radius = maskable ? Infinity : size * 0.48;
  const barHalfW = size * (maskable ? 0.25 : 0.28);
  const barHalfH = size * (maskable ? 0.06 : 0.07);

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let red = 0;
      let white = 0;
      for (let sy = 0; sy < SAMPLES; sy++) {
        for (let sx = 0; sx < SAMPLES; sx++) {
          const px = x + (sx + 0.5) / SAMPLES - c;
          const py = y + (sy + 0.5) / SAMPLES - c;
          if (Math.abs(px) <= barHalfW && Math.abs(py) <= barHalfH) white++;
          else if (px * px + py * py <= radius * radius) red++;
        }
      }
      const total = SAMPLES * SAMPLES;
      const alpha = (red + white) / total;
      const i = (y * size + x) * 4;
      for (let ch = 0; ch < 3; ch++) {
        pixels[i + ch] = alpha === 0 ? 0 : Math.round((RED[ch] * red + WHITE[ch] * white) / (red + white));
      }
      pixels[i + 3] = Math.round(alpha * 255);
    }
  }
  return encodePng(size, pixels);
}

function encodePng(size, rgba) {
  const raw = Buffer.alloc(size * (size * 4 + 1));
  for (let y = 0; y < size; y++) {
    raw[y * (size * 4 + 1)] = 0;
    rgba.copy(raw, y * (size * 4 + 1) + 1, y * size * 4, (y + 1) * size * 4);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr.set([8, 6, 0, 0, 0], 8);
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([length, body, crc]);
}

const out = new URL('../public/icons/', import.meta.url);
mkdirSync(out, { recursive: true });
writeFileSync(new URL('icon-192.png', out), draw(192, { maskable: false }));
writeFileSync(new URL('icon-512.png', out), draw(512, { maskable: false }));
writeFileSync(new URL('icon-maskable-512.png', out), draw(512, { maskable: true }));
