// Builds shared/snapshot/nse-bse-symbols.json from exchange symbol files downloaded in a browser.
//   node scripts/import-symbols.mjs 2026-10-01 EQUITY_L.csv [bse-equity.csv]
// NSE files use a SYMBOL column; BSE files use a "Security Id" column. Nothing is fetched.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const [date, ...files] = process.argv.slice(2);
if (!/^\d{4}-\d{2}-\d{2}$/.test(date ?? '') || files.length === 0) {
  console.error('usage: node scripts/import-symbols.mjs <yyyy-mm-dd> <nse.csv> [bse.csv]');
  process.exit(1);
}

const COLUMNS = ['SYMBOL', 'SECURITY ID'];

function cells(line) {
  const out = [];
  let cell = '';
  let quoted = false;
  for (const ch of line) {
    if (ch === '"') quoted = !quoted;
    else if (ch === ',' && !quoted) {
      out.push(cell);
      cell = '';
    } else cell += ch;
  }
  out.push(cell);
  return out.map((value) => value.trim());
}

function symbolsFrom(path) {
  const [header, ...rows] = readFileSync(path, 'utf8').split(/\r?\n/).filter((line) => line.trim());
  const names = cells(header).map((name) => name.toUpperCase());
  const column = names.findIndex((name) => COLUMNS.includes(name));
  if (column < 0) {
    throw new Error(`${path}: no SYMBOL or Security Id column`);
  }
  return rows.map((row) => cells(row)[column]?.toUpperCase()).filter((s) => /^[A-Z0-9&-]{1,20}$/.test(s ?? ''));
}

const symbols = [...new Set(files.flatMap(symbolsFrom))].sort();
const out = join(dirname(fileURLToPath(import.meta.url)), '..', 'shared', 'snapshot', 'nse-bse-symbols.json');
writeFileSync(out, `${JSON.stringify({
  snapshot_date: date,
  source: {
    name: `NSE/BSE equity symbol files dated ${date}`,
    url: 'https://nsearchives.nseindia.com/content/equities/EQUITY_L.csv',
    note: `Imported from ${files.length} file(s) with scripts/import-symbols.mjs`,
  },
  symbols,
}, null, 2)}\n`);
console.log(`wrote ${symbols.length} symbols to ${out}`);
