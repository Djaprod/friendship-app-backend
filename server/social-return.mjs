import {randomBytes} from 'node:crypto';

// The link only wakes the app. Identity and session retrieval still require its saved proof.
export function socialReturnPage(status) {
  const nonce=randomBytes(18).toString('base64');
  const verified=status==='verified';
  return {
    headers:{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','Referrer-Policy':'no-referrer','Content-Security-Policy':`default-src 'none'; script-src 'nonce-${nonce}'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'`,'X-Content-Type-Options':'nosniff'},
    html:`<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><title>Returning to Artii</title></head><body style="background:#10162c;color:#f4efff;font:18px system-ui;padding:32px"><h1>Artii</h1><p>${verified?'Signing you in to Artii…':'Sign-in was cancelled or could not be verified. Return to Artii to try again.'}</p><p><a style="color:#f28ec7" href="intent://auth/complete#Intent;scheme=artii;package=com.artii.app;end">Return to Artii</a></p><p>If your browser does not open the app, switch back to Artii using your recent apps. Your sign-in will finish there automatically.</p>${verified?`<script nonce="${nonce}">window.location.replace('artii://auth/complete');</script>`:''}</body></html>`
  };
}
