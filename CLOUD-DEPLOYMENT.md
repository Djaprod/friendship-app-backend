# Put Artii online: Supabase + Render

Status: deployment files and database integration are prepared. No cloud accounts, paid services, database, public URL, or store products have been created by this update. The supplied debug APK is not a live-cloud release; build against your actual URL after deployment.

## What runs where

Phones → HTTPS/WSS Artii API on one Render Docker web service → private Supabase PostgreSQL schema `artii`.

Photos/videos/voice files live on a persistent Render disk; PostgreSQL stores protected attachment metadata. Chat, game, and location access is authorized by the API. Never put the database password, Auth0 client secret, Play service-account JSON, or TURN password into the Android build.

This is a single-instance first deployment. A PostgreSQL advisory lock prevents two coordinators from writing competing states. The server keeps a working cache and commits changed rows in a transaction before acknowledging writes or broadcasting. Runtime UI lists and network buffers are necessary; they are not simulated users or authoritative balances. Do not scale horizontally or edit application records directly in the database while the service runs. The coordinator holds the application dataset in memory and database commits block its mutation loop; this is not a large-scale architecture. Load-test realistic traffic before inviting a large audience. Current socket limits are 200 chat and 200 game connections; sub-second delivery depends on region, load, and network conditions.

## 1. Create your Supabase account and database

1. Sign up at https://supabase.com/dashboard and create an organization and a PostgreSQL project. Choose a region close to your intended users and Render service. Record the database password in a password manager.
2. In the project's **Connect** dialog, copy the direct connection URI, or the **Session pooler** URI on port 5432 if IPv4 is needed. Do not use the transaction pooler on port 6543: Artii's writer lock needs one stable database session.
3. This is the server-only `DATABASE_URL`. Percent-encode special characters in its password. Keep it in Render's secret environment settings, never in chat or Git.
4. Use certificate-verifying TLS. If your provider requires its CA certificate, download it from the provider dashboard, upload it as a Render secret file, and set `DATABASE_CA_FILE` to the mounted file path. Do not disable certificate verification.
5. On the first successful startup, the server applies `server/migrations/001-initial.sql`. It creates private tables for users, friends, messages, locations, game_sessions, rooms, sessions, encounters, purchases, uploads, and related records. Rows contain versionable JSONB domain records with generated relational columns, database-enforced unique usernames/emails/room codes, and foreign keys. Passwords are salted scrypt hashes, not plaintext or reversible passwords. Login session tokens are stored as SHA-256 hashes.
6. Do not add the `artii` schema to Supabase's public Data API schemas. No mobile Supabase public key is needed. Direct client database access is intentionally denied. Enable database backups and select the backup retention/PITR plan appropriate to your data.

## 2. Create your Render account and service

1. Sign up at https://dashboard.render.com. Put the contents of this **Artii** folder in a private Git repository. Do not upload `.env`, `server/data`, `private`, signing keys, or build caches.
2. Create a **Blueprint** from the repository using `render.yaml`. Alternatively create a Docker Web Service using `Dockerfile`, one instance, and health path `/health`.
3. The blueprint selects a paid Starter service and a 10 GB persistent disk mounted at `/app/server/data`. Review and accept the provider's current costs before creating it. A free ephemeral disk must not be used for attachments.
4. Set secret `DATABASE_URL`. Set `PUBLIC_ORIGIN` to the assigned `https://YOUR-SERVICE.onrender.com` origin with no trailing slash. Whitespace, surrounding quotes, and a trailing slash are normalized at startup. A missing or invalid value uses `https://friendship-app-backend-1.onrender.com` with a warning. Set the actual origin explicitly when deploying to any other hostname.
5. Set `DATA_DIR=/app/server/data`, `NODE_ENV=production`, `HOST=0.0.0.0`. Render supplies `PORT`. `TRUST_PROXY=1` is for Render's trusted ingress; disable it if exposing the process directly. Do not put an untrusted proxy in front of this setting.
6. Deploy and visit `/health`. It must report `ok: true`, `version: "0.7.0"`, and `storage: "postgres"`. A failed database migration or second writer prevents startup; it never falls back to a local JSON database in production.
7. Allow WSS on the same public origin. Keep exactly one instance; the disk and session lock intentionally prevent competing writers. A deploy can briefly interrupt sockets. Clients reconnect and fetch fresh authorized state.
8. Back up the media disk as well as PostgreSQL. Restore both from compatible recovery points. Test a restore on a separate staging project before relying on backups.

For another VPS, the existing `compose.yaml` + Caddy can provide HTTPS with the same managed PostgreSQL URI. Use the absolute container data path above. Static Vercel/Netlify site hosting cannot run this persistent Node process and local media disk as-is; use a persistent Docker/web service for the API.

## 3. Connect the Android app to the public server

1. Copy `.env.mobile.example` to `.env.mobile` in this folder.
2. Set only `ARTII_API_URL=https://YOUR-SERVICE.onrender.com`. This URL is public configuration, not a secret.
3. In PowerShell run `./Build-Artii-Cloud.ps1`. It checks that the host is healthy and uses PostgreSQL, then builds an installable debug APK at `app/build/outputs/apk/debug/app-debug.apk`.
4. Install that same APK on both physical phones. They can use different Wi-Fi/mobile networks; the PC is no longer involved.
5. For a release bundle, use `./Build-Artii-Cloud.ps1 -Release`, then configure your own Android signing key in Android Studio's **Generate Signed Bundle / APK** flow. Release builds fail when the HTTPS origin is missing. Do not distribute the development signing key as a production identity.

Normal startup now opens authentication. There are no seeded people, sample friends, fake chats, or invented location pins. Each person creates a real account with a unique username. Existing local sessions will not exist in a new cloud database; log out and register there, or import test data intentionally.

## 4. Real-time and recovery behavior

- `/chat/socket`: authenticated heartbeat/focus connection; changes trigger immediate view refreshes. Message delivery and read acknowledgements remain separate server records. Offline clients re-fetch authoritative state on reconnect.
- `/games/socket`: authenticated game snapshots filtered per player; hidden roles are never broadcast to other players. Existing room codes are rejoined after reconnection. Game state, rosters, turn deadlines, and logs persist in PostgreSQL.
- Location: explicit accepted-friend audience and Ghost Mode checks, hardware updates throttled to every 3 seconds, heading stored, and existing map interpolation retained. Sharing stops in the background; it does not track people without their consent.
- Brief chat interruptions retain the active Discover meeting for the existing 90-second heartbeat grace period. Explicit close/end charges both users once after a successful handshake. Setup failure does not charge. Video calls can end when Android backgrounds the camera session.
- Daily chances: the server runs a 3-second maintenance sweep plus checks on access. Each user's rolling 24-hour epoch resets used daily chances to zero (10 or 50 cap), with no rollover. Missed reset intervals are calculated on recovery. Wallet credits never reset. An external database cron must not mutate these rows behind the coordinator's cache.
- If PostgreSQL loses its connection or a commit fails, the process exits rather than serving uncommitted cache state. Render restarts it from the last committed rows; clients reconnect. This trades availability for consistency.
- Draft text uses Android saved state while the conversation is open. Unsaved drafts are not uploaded as sent messages. This is not a full offline-first message queue.

## 5. Activate calls, social login and purchases

- For reliable calls across carrier NATs, configure a TURN relay (`TURN_URL`, `TURN_USERNAME`, `TURN_PASSWORD`) on the server. The current static-credential adapter should be replaced with short-lived TURN credentials before broad public rollout. Test on two separate mobile networks; STUN alone cannot guarantee calls.
- Follow `AUTH-SETUP.md` for Auth0 and each social provider. Email/password works without social provider credentials. Account recovery/email verification, public moderation operations, and abuse-response processes still need a launch review.
- Follow the Play Console section of `FEATURES-AND-SETUP.md`: monthly product `artii_premium_monthly`, a 3-day introductory offer for eligible accounts, and consumable `artii_boost_50`. Configure intended US prices $4.00/month and $7.99; localized pricing/eligibility come from Play.
- Mount the Play service-account JSON as a secret file and set `GOOGLE_PLAY_SERVICE_ACCOUNT_FILE`. Verification is server-side, account-bound, and idempotent. Disabled/unverified purchases never mint credits. Test verification hooks are used only by local tests; there is no public mock-credit endpoint. Subscription entitlements are reconciled periodically and fail closed at their last verified expiry.

## 6. Validate the live deployment before release

Run `npm ci` in `server`, then `npm test` and `npm run test:storage` locally. Storage tests use PGlite PostgreSQL semantics; they do not substitute for testing your hosted TLS connection and lock behavior.

Read-only hosted smoke check: set `ARTII_API_URL`, optionally `ARTII_TEST_TOKEN` from a dedicated test account, then run `node server/cloud-smoke.mjs`. Never paste the token into chat or commit it.

On two real phones:

1. Register different accounts; verify that the same username in another case is rejected.
2. Search the exact username, request/accept friendship, exchange text/media, react, delete, and confirm sent/delivered/read differences.
3. Share location with the accepted friend, then turn on Ghost Mode and confirm removal on the other phone.
4. Create and join each game. Interrupt one phone's network, reconnect, and verify turn/role recovery without revealing hidden roles.
5. Open a mutually accepted Discover chat; end it and verify exactly one chance is removed from each account. Reject a pending request and verify no charge.
6. Restart the Render service; verify accounts, friendships, messages, purchases and game rooms persist. Test uploaded media after redeploy.
7. Use Play license testers for trial, cancellation, expiry, duplicate receipt, and credit-wallet tests. Test the daily reset with a staging test clock, not by changing production database rows.

## Existing local data (optional)

Stop the old server and the hosted service. Back up `server/data/artii.json` and `server/data/media`. With the new managed database empty and server secrets set, run `node --env-file=../.env import-json.mjs /absolute/path/to/artii.json` from `server`. It refuses a nonempty destination. Copy the matching media folder onto the persistent disk. Then restart the service. Never overwrite an active cloud database with an older JSON file.

Official references: https://supabase.com/docs/guides/database/connecting-to-postgres · https://render.com/docs/websocket · https://render.com/docs/disks

Social sign-in activation is documented step by step in [AUTH-SETUP.md](AUTH-SETUP.md). Create your Auth0 and provider applications after the cloud origin is known. The callback now includes a credential-free **Open Artii** link. `npm run check:launch` checks required environment settings and identity-provider discovery. Live provider credentials/review and real phone tests remain necessary; no provider is claimed to be enabled by this package.
