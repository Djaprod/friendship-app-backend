# Artii 0.5.0 — Discover availability and larger media

- Discover has an animated green Public / red Private switch and an information button. The switch controls both new-person chat and call permission. Private immediately cancels pending meetings without charging a chance; current accepted meetings continue.
- A bright green light and “online” mean a focused, connected user permits Discover requests and is not busy. Otherwise, the light is off and says “offline”. Existing profile visibility rules still apply.
- Photos, videos and voice clips upload and download as streams, up to 250 MB per attachment. The alpha backend permits four concurrent uploads and a 5 GB media quota. Unsent staged uploads expire after an hour.
- Voice clips may run for 60 minutes. Conversation duration is still unlimited.
- The emoji picker bundles 3,773 unique Unicode emojis, searchable by name and grouped into categories. The Unicode license is included in app assets.

# Latest interface update

- Supplied violet, blue and pink Artii logo used in the header and Android launcher.
- Bottom navigation uses icons only, in this order: Profile, Discover, Chats, Games, Meet. Screen-reader labels remain available. Discover opens first.
- Compact `+N left` header button combines daily chances and the permanent wallet; opens Premium. Zero balance opens Premium automatically on Discover.
- Chats combines personal and group conversations, with search, a Groups filter, friend search and new-group action.
- Meet uses the full available screen for a light vector map, with floating sharing and focus controls.

# Artii 0.3 — settings, map, meetings and store

## Install and connect

Install the new Artii-alpha.apk. Connect the phone by USB, enable USB debugging, and run **Start-Artii-Phone-Test.cmd**. Keep the server window open. For the emulator, run Start-Artii-Server.cmd. The normal login form does not require a server address. Public hosting and social authentication still require the steps in AUTH-SETUP.md.

## Settings and profile

Open your profile badge in the top-right corner to reach Settings. Edit your photo or use a two-letter initials badge; choose its color, display name, unique username and a bio of up to 150 characters. The save action only becomes available for a valid changed profile. Username availability is checked while typing and checked again atomically by the server when saving. Friendships use stable internal IDs, so changing your username keeps your friends.

Privacy settings include Ghost Mode, Everyone / Friends Only / Nobody visibility for status and username search, and blocked-user management. New accounts do not receive Discover chat or video requests until they opt in. Even after opting in, each meeting requires acceptance.

Message, game-invite and friend-request preferences control foreground banners and sounds. Background OS push notifications are not connected. English and French settings/navigation labels are included; other app content remains primarily English. Cache clearing removes temporary app cache files without signing you out. Account deletion requires typing DELETE and removes the account, sessions, profile, location and owned messages; other users' messages remain available. Any Play subscription must be cancelled separately in Google Play. The settings footer reads **Made by Dp1 Production**.

## Friend map

MapLibre Native renders vector terrain using OpenFreeMap's dark style. Initials badges use the selected profile color, with a pulsing ring for updates younger than 15 seconds. Coordinates interpolate between three-second updates. Overlapping badges fan apart on screen. The carousel focuses friends; My Location requests foreground location permission, and Recenter Group frames all listed friends.

After 60 seconds without a new coordinate, a badge fades to 40% and shows last-seen time. It remains only while the sender's original sharing permission and expiry are valid. Ghost Mode, blocking, stopping sharing, or expiry removes the point immediately on the next sync. No background tracking is enabled. Map performance still needs measurement on real low-end phones and large friend groups; “lag-free” is not a verified guarantee.

## Discover meetings and chances

- Discover profiles move left after three seconds with a smooth transition. Dragging, opening a profile, opening the store, an active meeting, or backgrounding pauses advancement.
- Request a chat/video meeting with a profile, or request someone at random. Both users must enable that connection type; requesting is the first explicit acceptance, and the other person accepts the invitation.
- Acceptance reserves both users in one durable Busy session. A chat counts as opened when both screens acknowledge readiness; video counts when both devices report an established media connection.
- Closing/hanging up an opened meeting charges exactly one chance to each person, even if both send an end request. Pending, declined and unsuccessful setup attempts cost nothing. Setup has a 30-second failure deadline; **opened conversations have no duration limit**.
- Explicit hang-up charges immediately on the server. A network outage cannot be detected instantly: 90 seconds without a heartbeat ends the session and charges once. The sender's app polls every three seconds while foregrounded. Leaving the app ends an active meeting.
- Standard accounts get 10 daily chances. A verified active Premium subscription/trial raises the cap to 50. Windows last exactly 24 hours from the account's first balance initialization; unused daily chances disappear, with no stacking. Upgrading raises the current cap while retaining that day's usage.
- Daily chances are used first. Verified Instant Boost credits are stored in a separate wallet that never resets or expires. Client-submitted balances or premium flags cannot grant credits.
- Friend conversations outside Discover are not metered. During a Discover meeting, other people cannot interrupt either participant with calls, messages or friend-request spam. Meeting partners can still add each other.

## Activate Google Play purchases

Purchases are intentionally disabled until the store is configured. The test build never simulates a charge or grants a client-requested free trial.

1. Create the Android app in Play Console with package **com.artii.app**, upload a signed internal-testing build, and add license testers.
2. Create subscription **artii_premium_monthly** with an auto-renewing monthly base plan priced at **US$4.00**. Create a new-customer offer **trial-3days** with a three-day free phase followed by the monthly charge. Configure eligibility in Play Console.
3. Create consumable one-time product **artii_boost_50** priced at **US$7.99**. Disable multi-quantity purchases for this pack.
4. Enable Google Play Developer API and grant an appropriate service account access to this app's purchases. Store its JSON key privately on the server. Set GOOGLE_PLAY_SERVICE_ACCOUNT_FILE to its absolute path, GOOGLE_PLAY_PACKAGE=com.artii.app and GOOGLE_PLAY_TRIAL_OFFER=trial-3days. Do not include the key in an APK or source ZIP.
5. For Docker, put the key at private/google-play.json, set the environment path to /run/secrets/google-play.json, and use the optional compose.play.yaml override. This folder is excluded from the Docker context and source packaging.
6. The storefront loads localized prices and eligible offers from Google Play. Only an eligible three-day free offer gets the active trial button. The recurring price and cancellation terms are displayed. Restore purchases retries unprocessed purchases.
7. The backend re-fetches the purchase from Google, verifies its product and account binding, and grants each token only once. Subscription state is reconciled every 15 minutes. Consumables are consumed and subscriptions acknowledged only after server verification succeeds.

Required before accepting real customer payments: test pending/cancelled purchases, renewals, trial eligibility, restores and revocations with Play's test tools; connect real-time developer notifications and refund/chargeback reconciliation; validate receipt/key operations and backups; migrate to a transactional database with unique purchase-token/username constraints. The bundled JSON backend supports **one process only**. It is an alpha, not an audited production payment system.

No hosting, Play Console app, service account, subscription product or payment has been created for you. Google/Facebook/Apple login setup is separate from Google Play billing setup.

## Verification

Server suite: node --test test.mjs games.test.mjs auth.test.mjs economy.test.mjs settings.test.mjs messaging.test.mjs

Coverage includes username claims, OAuth identity validation, privacy, deletion, consent and session isolation, daily reset/no rollover, exactly-once deductions, wallet priority, failed setup, purchase-token replay and account binding, plus all three games. Purchase/provider responses are simulated in tests. Native map, camera, call quality and live billing require real-device testing.

References: [MapLibre Native](https://maplibre.org/maplibre-native/android/examples/getting-started/), [OpenFreeMap](https://openfreemap.org/quick_start/), [Play Billing integration](https://developer.android.com/google/play/billing/integrate), [subscription verification](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2/get).

## Live messaging update

- Chat header shows the friend's unique username, green online status or localized last-seen time. Presence respects Everyone / Friends Only / Nobody. Multiple devices keep a person online while any authenticated device remains focused.
- Authenticated WebSocket events refresh conversations immediately; delivery receipts are sent only after the client processes message payloads. Read receipts require the focused, open conversation. Three-second polling remains a reconnect fallback.
- Long-press (or mouse right-click) a bubble for six reactions, Delete for Me, or sender-only Delete for Everyone. Local hiding persists on this device. Global deletion removes text and media from server storage and leaves a tombstone.
- The native emoji tray includes 3,773 unique Unicode 15.1 emoji sequences, with search and category tabs. Emoji appearance depends on the device font. Android does not offer a universal public API to force every keyboard vendor's own emoji tab; the app tray works independently of the chosen keyboard.
- Photos/videos use the system document picker. Supported: JPEG, PNG, WebP and MP4, up to 250 MB each. Media is fetched through authenticated conversation checks, with no public media URL. The alpha server has a 5 GB media quota.
- Hold the microphone to record; release to send. Voice clips use AAC/M4A, a millisecond timer and captured amplitude waveform, with a 60-minute recording cap. Leaving the app cancels recording; playback stops in the background. Conversation duration remains unlimited.
- Sender bubbles use soft green, incoming bubbles slate. The thread scrolls to the newest message automatically.

Live event publication was verified under one second on the local integration test; internet latency is not guaranteed. Abrupt network loss is detected after a 15-second heartbeat timeout; last seen records when the server detects loss. Normal focus loss is reported immediately. Closed/background apps do not have a Firebase push handler yet and receive pending messages when they reconnect. Audio, video playback, gesture behavior, and narrow-screen layouts still require real-device testing. Messages are access-controlled but are not end-to-end encrypted. Restart the updated server along with installing the new APK.

## Emoji panel update — 0.5.1

The chat emoji panel uses larger 30sp emojis in 48dp-wide, 50dp-high touch cells, a responsive taller tray, on-demand search, and a compact scrollable category bar. The composer uses a white outline smiley instead of a colored emoji. All 3,773 emoji entries remain available. This UI update remains compatible with server 0.5.0.


## Visual refresh — 0.6.0

All main screens share a navy background, lightweight vector ribbons, violet/pink/blue accents, rounded bordered panels, and clearer typography. The supplied 512px Artii icon is used for branding and the launcher.

- Discover keeps a circular portrait inside each large swipe card. Cards scroll internally on short screens. Availability and the compact chance counter keep their existing behavior.
- Profile settings are organized into Profile, Privacy, Notifications, and Preferences tabs. Profile drafts remain intact while switching tabs; privacy and notification settings save immediately.
- Chats and conversation headers use the shared palette. Sender bubbles remain green; receipt, presence, reaction, deletion, and media behavior are unchanged.
- Games, all lobbies, the live Ludo board frame, rules, and game chat use refreshed panels and controls. Game logic is unchanged.
- Meet keeps its light vector map with dark readable controls and larger friend cards.
- Login and registration use the shared background and panels. Premium offers stack vertically for readable phone layouts.

The screenshots are visual references, not screenshots embedded as app pages. UI controls stay interactive. Decorative artwork is implemented with lightweight gradients and vectors; the reference illustrations are not reproduced pixel for pixel. Social authentication and paid purchases still require the documented provider setup.

## Cloud backend — 0.7.0

See [CLOUD-DEPLOYMENT.md](CLOUD-DEPLOYMENT.md) for Supabase and Render account setup, automatic private-schema migration, persistent attachment storage, and the cloud Android build. Production no longer permits JSON storage; the local JSON adapter is retained only for development/testing and explicit imports. Signed-out screens no longer fabricate accounts, friends, chats, or map coordinates. Deploying and testing the real public service still requires your cloud accounts and HTTPS URL.
