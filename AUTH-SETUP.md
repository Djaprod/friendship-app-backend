# Artii social sign-in activation

The Android and server flows are implemented. Public Google, Facebook and Apple sign-in is not activated until your provider applications are registered, configured, and tested. An APK cannot supply developer identities or approve provider consent screens on your behalf.

First complete [CLOUD-DEPLOYMENT.md](CLOUD-DEPLOYMENT.md) to create Supabase and Render and obtain a public HTTPS origin.

## Auth0 account and Artii application

1. Create an account at https://manage.auth0.com and create a production tenant in an appropriate region.
2. Create an application named **Artii Server** of type **Regular Web Application**. This is the confidential backend OAuth client. Use Authorization Code and RS256 ID tokens.
3. Add the exact Allowed Callback URL `https://YOUR-ARTII-SERVICE.onrender.com/auth/social/callback` (or your own HTTPS origin). This is Artii's server callback, not the mobile return URI.
4. Store the tenant domain, Client ID and Client Secret in Render's secret settings:
   - `AUTH_ISSUER=https://YOUR-TENANT.REGION.auth0.com/`
   - `AUTH_CLIENT_ID` and `AUTH_CLIENT_SECRET` from this application.
5. In Authentication → Social, create the connections below, enable each for **Artii Server**, and copy its actual connection name. The usual names are `google-oauth2`, `facebook`, and `apple`.
6. Do not put the Auth0 client secret or provider keys in `.env.mobile`, the Android source, or chat.

## Google

1. Create a Google Cloud project at https://console.cloud.google.com and configure Google Auth Platform branding, audience, and consent details for Artii.
2. Create an OAuth client of type **Web application**. This integration uses the system browser and Artii's confidential server; it is not a native Google SDK login.
3. Use the authorized redirect URI shown by the Auth0 Google connection, normally `https://YOUR-AUTH0-DOMAIN/login/callback`. This differs from Artii's callback above.
4. Enter your Google OAuth Client ID and Client Secret into the Auth0 Google connection. Use your own production credentials, not Auth0's shared developer keys.
5. Publish the consent configuration for your intended public audience and complete any verification Google requests. A testing-only audience does not enable everyone who installs the APK.
6. Enable the connection for Artii Server and set `AUTH_GOOGLE_CONNECTION=google-oauth2` (or its actual name) in Render.
7. Use Auth0's **Try Connection**, then test from Artii on a physical phone using an account outside your developer team.

Official guide: https://auth0.com/docs/authenticate/identity-providers/social-identity-providers/google

## Facebook

1. Create an app for Artii at https://developers.facebook.com/apps and configure the Facebook Login product/use case available for your application.
2. Use the callback/redirect URI shown by Auth0's Facebook social connection, normally `https://YOUR-AUTH0-DOMAIN/login/callback`.
3. Enter the Facebook App ID and App Secret in the Auth0 connection. Enable the connection for Artii Server and set `AUTH_FACEBOOK_CONNECTION=facebook` (or its actual name) in Render.
4. Complete the app's required public details, privacy policy, data-deletion instructions, and any provider review/verification requested by Meta. Move the app to its public/live state; development-only tester access is insufficient for general APK users.
5. Test with an account that is not an app administrator or tester, then test Artii's first-time username prompt and returning login.

Use the current connection wizard in Auth0 and Meta's dashboard for their exact required settings; these depend on your app configuration.

## Apple on Android

Artii uses Apple's web sign-in through the Android system browser. You need your Apple developer identity and a Sign in with Apple configuration for web/other platforms.

1. Set up your account at https://developer.apple.com/account/ and create a Sign in with Apple-enabled primary App ID as required by Apple.
2. Register a **Services ID** for the browser flow and its verified domain/return URL shown by the Auth0 Apple connection.
3. Create the Sign in with Apple private key. Keep its `.p8` file private. Configure the Services ID, Team ID, Key ID and private key in Auth0's Apple social connection.
4. Enable the connection for Artii Server and set `AUTH_APPLE_CONNECTION=apple` (or its actual name) in Render.
5. Test both first authorization and returning login. Test Apple's Hide My Email. Artii associates the verified issuer/subject identity and does not assume a public email is present.

Official Apple setup: https://developer.apple.com/documentation/signinwithapple/configuring-your-environment-for-sign-in-with-apple

## Finish and verify

1. Redeploy Render with the provider settings. `GET /auth/config` should report all three providers as `true`. This means configured, not proof that provider review or a real login succeeded.
2. Run `npm run check:launch` from `server` with the real server environment. It checks required settings and OIDC discovery; it does not validate all provider credentials or replace interactive login tests.
3. From an Android phone, tap each provider. The system browser performs account selection, consent or MFA as required. Artii cannot bypass provider security checks.
4. The browser shows **Open Artii to continue**. It returns through `artii://auth/complete`, with no credentials in the URL. The original app flow obtains its result using its secret proof. If Android killed the app, start a fresh attempt.
5. First-time users choose an available unique username and confirm the age requirement. Returning users receive their existing account. Test cancellation, duplicate username, network interruption, and sign-out/re-login.
6. Build against your actual public origin using `Build-Artii-Cloud.ps1`. The release option checks provider/TURN/purchase configuration before building, but live tests are still required. No localhost address or developer account setup should be required on a normal user's phone.

## Security and verification status

The flow uses state, nonce, PKCE, signed RS256 issuer/audience/expiry validation and a short-lived proof-bound handoff. Existing accounts are never silently linked by email. Password login uses salted scrypt; app sessions use Android Keystore encryption, and the server stores session hashes.

Automated tests cover forged/expired tokens, wrong audience/nonce, unavailable providers, first social signup, username uniqueness, and proof-bound single-use completion. These tests simulate the provider response. No live Google, Facebook, Apple, Supabase or Render account is configured in the delivered project.

For public availability of all features, also complete the cloud guide's TURN, Play Console, signing, backups, and two-phone checks. Google Play billing must be tested using a distribution/install path supported by Play; arbitrary sideloaded APKs are not guaranteed to support paid products on every phone.
