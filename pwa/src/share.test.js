import assert from 'node:assert/strict';
import { test } from 'node:test';
import { sharedTextFrom } from './share.js';

const params = (query) => new URLSearchParams(query);

test('uses the shared text', () => {
  assert.equal(sharedTextFrom(params('text=Guaranteed%205%25%20daily')), 'Guaranteed 5% daily');
});

test('appends a separately shared link once', () => {
  assert.equal(sharedTextFrom(params('text=Join%20now&url=https://x.example')), 'Join now\nhttps://x.example');
  assert.equal(sharedTextFrom(params('text=Join%20https://x.example&url=https://x.example')), 'Join https://x.example');
});

test('falls back to the title when there is no text', () => {
  assert.equal(sharedTextFrom(params('title=VIP%20tips')), 'VIP tips');
});

test('returns empty for an empty share', () => {
  assert.equal(sharedTextFrom(params('')), '');
});
