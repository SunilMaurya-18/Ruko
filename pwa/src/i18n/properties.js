// Minimal Java .properties reader for the shared catalogue (UTF-8, key=value, # comments, \uXXXX escapes).

const ESCAPES = { n: '\n', t: '\t', r: '\r', f: '\f' };

function unescape(text) {
  return text.replace(/\\(u[0-9a-fA-F]{4}|.)/g, (_, code) => {
    if (code.length === 5) return String.fromCharCode(parseInt(code.slice(1), 16));
    return ESCAPES[code] ?? code;
  });
}

export function parseProperties(source) {
  const out = {};
  const lines = source.split(/\r?\n/);
  for (let i = 0; i < lines.length; i++) {
    let line = lines[i].replace(/^\s+/, '');
    if (!line || line.startsWith('#') || line.startsWith('!')) continue;
    while (/(^|[^\\])(\\\\)*\\$/.test(line) && i + 1 < lines.length) {
      line = line.slice(0, -1) + lines[++i].replace(/^\s+/, '');
    }
    const match = /^((?:\\.|[^=:\s\\])+)\s*[=:\s]\s*(.*)$/.exec(line);
    if (match) {
      out[unescape(match[1])] = unescape(match[2]);
    } else {
      out[unescape(line)] = '';
    }
  }
  return out;
}

/** Looks up a catalogue key and fills {name} placeholders. Missing keys return null so callers can fall back. */
export function lookup(catalog, key, params = {}) {
  const text = catalog?.[key];
  if (text == null) return null;
  return text.replace(/\{(\w+)\}/g, (whole, name) => (name in params ? params[name] : whole));
}
