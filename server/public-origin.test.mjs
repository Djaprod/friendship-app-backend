import test from 'node:test';
import assert from 'node:assert/strict';
import {configurePublicOrigin, DEFAULT_PUBLIC_ORIGIN} from './public-origin.mjs';

test('normalizes HTTPS origins before downstream services read the environment', () => {
  for (const value of ['https://app.example', ' https://app.example/\n', '"https://app.example/"', "'https://app.example/'"]) {
    const env = {PUBLIC_ORIGIN: value};
    assert.equal(configurePublicOrigin(env, () => assert.fail('Unexpected fallback')), 'https://app.example');
    assert.equal(env.PUBLIC_ORIGIN, 'https://app.example');
  }
});

test('missing and unsafe values use the approved Render origin without leaking supplied values', () => {
  for (const value of [undefined, '', ' ', 'not-a-url', 'http://app.example', 'https://user:secret@app.example', 'https://app.example/path', 'https://app.example/?secret=yes', 'https://app.example/#fragment']) {
    const env = {PUBLIC_ORIGIN: value};
    const warnings = [];
    assert.equal(configurePublicOrigin(env, message => warnings.push(message)), DEFAULT_PUBLIC_ORIGIN);
    assert.equal(env.PUBLIC_ORIGIN, DEFAULT_PUBLIC_ORIGIN);
    assert.equal(warnings.length, 1);
    assert.ok(!warnings[0].includes('secret'));
  }
});
