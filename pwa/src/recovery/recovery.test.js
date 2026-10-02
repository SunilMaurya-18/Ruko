import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import drafts from '../../../shared/fixtures/complaint-drafts.v0.json' with { type: 'json' };
import { lookup, parseProperties } from '../i18n/properties.js';
import { CHOICES, MAX_WORDS, buildDraft, invalidField, rupees, todayInIndia, wordCount } from './complaint.js';
import { ALL_LINKS, recoveryPaths } from './content.js';

const catalog = Object.fromEntries(['hi', 'en'].map((lang) => [lang, parseProperties(
  readFileSync(new URL(`../../../shared/i18n/messages_${lang}.properties`, import.meta.url), 'utf8'))]));
const t = (lang, key, params) => lookup(catalog[lang], key, params);

// ruko.links.allow in the server's application.yml, which OutboundLinkPolicy enforces.
const yml = readFileSync(new URL('../../../server/src/main/resources/application.yml', import.meta.url), 'utf8');
const ALLOWED_HOSTS = /links:\s*\{\s*allow:\s*\[([^\]]+)\]/.exec(yml)[1].split(',').map((host) => host.trim());

function allowed(link) {
  if (link === 'tel:1930') return true;
  const url = new URL(link);
  return url.protocol === 'https:' && !url.username && !url.password && (url.port === '' || url.port === '443')
    && ALLOWED_HOSTS.includes(url.hostname);
}

test('every bundled link passes the outbound link policy', () => {
  assert.ok(ALLOWED_HOSTS.length >= 3);
  for (const link of ALL_LINKS) assert.ok(allowed(link.url), link.id);
});

test('both recovery paths render in both languages, with 1930 first', () => {
  for (const lang of ['hi', 'en']) {
    const [moneySent, scores] = recoveryPaths(lang, t);
    assert.equal(moneySent.id, 'cyber_fraud');
    assert.equal(scores.id, 'scores');
    assert.equal(moneySent.steps[0].link.url, 'tel:1930');
    assert.ok(moneySent.steps.some((step) => step.link?.url === 'https://cybercrime.gov.in'));
    assert.ok(scores.steps.some((step) => step.link?.url === 'https://scores.sebi.gov.in'));
    for (const path of [moneySent, scores]) {
      assert.ok(path.title);
      for (const step of path.steps) {
        assert.ok(step.text, `${lang} ${path.id}`);
        if (step.link) assert.ok(step.link.label && allowed(step.link.url));
      }
    }
  }
});

test('the phone draft is the server draft (shared fixture)', () => {
  for (const { facts, text } of drafts) {
    assert.equal(buildDraft(facts, t), text, `${facts.lang} ${facts.channel}`);
  }
});

test('every phone draft is at most 200 words', () => {
  for (const lang of ['hi', 'en']) {
    for (const channel of CHOICES.channel) {
      for (const platform of CHOICES.platform) {
        for (const payee of CHOICES.payee_id_type) {
          const text = buildDraft({ lang, date: '2026-09-01', amount: 4999, channel, platform, payee_id_type: payee }, t);
          assert.ok(wordCount(text) <= MAX_WORDS);
          assert.doesNotMatch(text, /[{}]/);
        }
      }
    }
  }
});

test('facts are checked like the server checks them', () => {
  const ok = { lang: 'hi', date: '2026-09-01', amount: 4999, channel: 'upi', platform: 'sms', payee_id_type: 'upi_id' };
  assert.equal(invalidField(ok, '2026-10-02'), null);
  assert.equal(invalidField({ ...ok, date: '2026-10-03' }, '2026-10-02'), 'date');
  assert.equal(invalidField({ ...ok, date: '1999-12-31' }, '2026-10-02'), 'date');
  assert.equal(invalidField({ ...ok, amount: 0 }, '2026-10-02'), 'amount');
  assert.equal(invalidField({ ...ok, amount: 12.5 }, '2026-10-02'), 'amount');
  assert.equal(invalidField({ ...ok, platform: 'pigeon' }, '2026-10-02'), 'platform');
});

test('amounts and today use Indian conventions', () => {
  assert.equal(rupees(999), '999');
  assert.equal(rupees(125000), '1,25,000');
  assert.equal(rupees(1_000_000_000), '1,00,00,00,000');
  assert.equal(todayInIndia(new Date('2026-10-01T19:00:00Z')), '2026-10-02');
});
