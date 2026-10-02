import pii from '../../../shared/patterns/pii.v0.json' with { type: 'json' };

export const PLACEHOLDER = /\[(?:phone|acct|aadhaar|pan|otp)\]/giu;

export const PII_RULES = pii.rules.map((rule) => ({
  placeholder: rule.placeholder,
  keepPrefix: Boolean(rule.keep_prefix_group),
  regex: new RegExp(rule.pattern.normalize('NFKC'), `gu${rule.flags ?? ''}`),
}));

/** Same rules, in the same order, as the server's PiiMasker. Only masked text leaves the phone. */
export function maskPii(text) {
  let masked = text.replace(PLACEHOLDER, (match) => match.toUpperCase());
  for (const rule of PII_RULES) {
    masked = masked.replace(rule.regex, (match, prefix) => (rule.keepPrefix ? prefix + rule.placeholder : rule.placeholder));
  }
  return masked;
}
