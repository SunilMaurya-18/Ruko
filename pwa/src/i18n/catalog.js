import en from '../../../shared/i18n/messages_en.properties?raw';
import hi from '../../../shared/i18n/messages_hi.properties?raw';
import { lookup, parseProperties } from './properties.js';

// The same catalogue the server reads, bundled so labels and analogies render offline.
const CATALOGS = { hi: parseProperties(hi), en: parseProperties(en) };

export function t(lang, key, params) {
  return lookup(CATALOGS[lang] ?? CATALOGS.hi, key, params);
}
