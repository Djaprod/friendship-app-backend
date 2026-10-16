# Artii — Android alpha 0.7.0

**Put Artii online:** [CLOUD-DEPLOYMENT.md](CLOUD-DEPLOYMENT.md). Cloud accounts and the public URL are still required.

**Latest features and store setup:** [FEATURES-AND-SETUP.md](FEATURES-AND-SETUP.md).

**Login update:** Start with [AUTH-SETUP.md](AUTH-SETUP.md). For a USB-connected phone, run **Start-Artii-Phone-Test.cmd**; the app selects its test address automatically. Google, Facebook and Apple require the provider setup documented there.

Discover now animates to the next profile every three seconds, pausing during dragging, while a profile is open, and when the app is backgrounded.

Artii is a native Android app with a looping Discover feed, profile pictures, friend requests, private and group text chat, one-to-one video calling, a 2D friend map, and three online games. This is a development alpha, not a published social network.

The app theme uses **violet, pink and dark blue**. Ludo retains its blue, yellow, green and red player colors.

## Open and run

1. Open Android Studio, choose **Open**, and select this **Artii** folder (the folder containing `settings.gradle.kts`).
2. Let Gradle sync. The project uses Android SDK 35, Android Gradle Plugin 9.0.1, Gradle 9.1.0 and Kotlin/Compose compiler 2.2.10. Android Studio's installed Java runtime works with this setup.
3. Select a phone or an Android emulator and press **Run**. Android 8.0 or later is supported.
4. Sign in to load real profiles. To communicate or play with other people, start the server below and create an account using **Connect**.

You can also install the supplied debug APK on an Android phone. It is signed for testing; it is not a Play Store release.

## Start a private test community

Run `Start-Artii-Server.cmd` from this folder. It uses Node.js 22 or later, with a fallback to the Node runtime installed with Codex on this PC. No npm packages are needed.

The server listens on **127.0.0.1:8787** by default. Keep that terminal open while testing. Press **Ctrl+C** to stop it. Accounts, messages and matches are saved in `server/data/artii.json`.

For custom local setups, use **Advanced test connection** in Artii's **Connect** screen:

- Android Studio emulator: use **http://10.0.2.2:8787**.
- Phone connected by USB: run `adb reverse tcp:8787 tcp:8787`, then use **http://127.0.0.1:8787**.
- Friends on the same trusted Wi-Fi: run `Start-Artii-Server.ps1 -Lan`, then enter **http://YOUR-PC-IP:8787** on each phone. The script prints available local IPv4 addresses. Windows may ask to allow local network access. HTTP is enabled only in the debug app; use it only for test accounts on a trusted network.

Each person creates their own account. Discover shows the profiles on that server. Send and accept a friend request to open private chat, create groups, or call. Profile pictures can be selected from the phone. The alpha is configured for adults 18 and over.

## Play with friends

Open **Games**, choose a game, and share the four-letter room code. Other players use **Join room**. The host configures the lobby and starts the match. **Your rooms** lets you resume saved matches. Reconnecting to a running room restores its authoritative server state.

### Ludo

- 2–4 players, with optional bots. The host assigns blue, yellow, green and red, in the same layout as your supplied board.
- One standard six-sided die; a six brings a token out of the yard and grants another turn. Captures also grant another turn.
- Star squares, starting squares and colored home lanes are safe. An exact roll is required to finish.
- All four tokens must finish to rank. Play continues for the remaining positions.
- A 20-second deadline auto-rolls and makes a legal move. Bots choose a legal move automatically.
- No blockades, triple-six penalty, or double-dice variant was specified, so those variants are not enabled.

### Undercover

- 3–10 players; 1–2 Undercover players and optionally 1 Mr. White. Civilians must start in the majority.
- All **90 exact word pairs across six themes** from your ZIP are included in `server/words.json`.
- Private reveal card, 45-second one-word hint turns, 30-second discussion, then a 60-second secret ballot.
- Highest vote total is eliminated. A tie eliminates nobody and another round starts.
- Civilians win after all impostors are eliminated. Impostors win at parity with the civilians.

### Werewolf

- 5–15 players. The host selects a valid wolf count; one Seer, one Doctor, and the rest are Villagers.
- Wolves act for up to 30 seconds, the Seer for 20 seconds, then the Doctor for 20 seconds. Phases advance early when all eligible players act.
- Day discussion lasts 60 seconds; voting lasts 60 seconds. Tied wolf targets cause no night elimination; tied town votes cause no execution.
- Private wolf chat, private Seer result, Doctor protection, and separate ghost chat are enforced by the server.
- Villagers win when no wolves remain; wolves win at parity. Other players' secret assignments and ballots are never included in your live state.

The game panels use a bundled web interface inside Android, with WebSocket synchronization. The app's navigation, Discover feed, chats, map, profile and calling controls are native Kotlin/Jetpack Compose.

## Location and calls

- **Meet** uses a real, pannable and zoomable 2D map with OpenStreetMap attribution. Map tiles need internet access. Initial demo markers are clearly labeled samples in Port Louis.
- Sharing is off by default. Choose accepted friends and a duration. Sharing stops when Artii leaves the foreground; stale points fade after 60 seconds and remain only until sharing permission expires or is revoked. Background tracking is not included.
- One-to-one video calls use WebRTC, microphone/camera permissions and server-side signaling. For reliable connections across different networks, configure a TURN relay using `TURN_URL`, `TURN_USERNAME`, and `TURN_PASSWORD` on the server. The default STUN-only configuration is not sufficient on every network.
- Incoming calls are received while Artii is open. Push notifications, background calls, group calls, media attachments and voice notes are not yet implemented.

## Before a public launch

The included server runs locally. Global availability requires an HTTPS/WSS host and domain. The release app disallows HTTP. Keep the database private; chat is not end-to-end encrypted. Video transport is handled by WebRTC.

The alpha uses one server process and a local JSON database. Before a larger launch, move to a production database and add email verification/recovery, push notifications, operational moderation, backups, abuse controls and deployment monitoring. Reports are recorded for the server operator; no staffed moderation service is connected. Calls still need testing between real phones on different networks.

Do not expose the development server directly to the internet. Use a reverse proxy with HTTPS, WebSocket upgrade support, and a production TURN service. No hosting account, paid service, domain or Play Store listing has been created.

## Build and tests

- Android: `Build-Artii.ps1` or `gradlew.bat :app:assembleDebug :app:lintDebug` with Android Studio's Java runtime.
- Server: from `server`, run `node --test test.mjs games.test.mjs auth.test.mjs`.
- The tests cover account access, friendship requirements, conversation isolation, location recipients/expiry, WebSocket joining/reconnection, Ludo movement/captures/ranking/timeouts, secret-role isolation, voting, Doctor protection and ghost chat.
- The browser game checks also open all three games at phone sizes and check for JavaScript errors and horizontal overflow.

## Reference documents

Game requirements came from **All artii games.zip** supplied in this chat. The Ludo board is redrawn in code to match the reference's layout; the promotional token image is not embedded.

Technical references: [Android pager](https://developer.android.com/develop/ui/compose/layouts/pager), [Android Gradle Plugin 9.0](https://developer.android.com/build/releases/agp-9-0-0-release-notes), [WebRTC Android library](https://github.com/webrtc-sdk/android), [OpenStreetMap attribution](https://www.openstreetmap.org/copyright).
