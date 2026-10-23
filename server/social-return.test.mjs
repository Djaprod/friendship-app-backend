import test from 'node:test';
import assert from 'node:assert/strict';
import {socialReturnPage} from './social-return.mjs';
test('verified callback wakes Android without putting credentials in the link',()=>{
 const page=socialReturnPage('verified');
 const nonce=page.html.match(/<script nonce="([^"]+)"/)[1];
 assert.ok(page.headers['Content-Security-Policy'].includes(`script-src 'nonce-${nonce}'`));
 assert.ok(page.html.includes("window.location.replace('artii://auth/complete')"));
 assert.ok(page.html.includes('package=com.artii.app;end'));
 assert.equal(page.headers['Cache-Control'],'no-store');
 assert.ok(!page.html.includes('token='));
 assert.notEqual(page.headers['Content-Security-Policy'],socialReturnPage('verified').headers['Content-Security-Policy']);
});
test('failed callback offers recovery without automatic navigation or reflecting input',()=>{
 const page=socialReturnPage('<script>alert(1)</script>');
 assert.ok(!page.html.includes('<script'));
 assert.ok(page.html.includes('cancelled'));
});
