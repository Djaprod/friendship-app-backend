# Artii

Android social app and Node.js backend, version 0.7.0-alpha.

## Deploy to Render

Use this repository with a Render **Blueprint**. The root `render.yaml` includes the Docker web service, health check, and persistent media disk. Keep the repository root as the build context: the backend also serves game assets from `app/src/main/assets/games`.

1. Create a Supabase PostgreSQL project and copy its direct or session-pooler connection URI (port 5432).
2. Connect this repository in Render and select the `main` branch and `render.yaml` blueprint.
3. Set `DATABASE_URL` privately in Render. Set `PUBLIC_ORIGIN` to the assigned HTTPS service URL, then deploy.
4. Confirm `/health` reports `ok: true` and `storage: "postgres"`.
5. Follow [CLOUD-DEPLOYMENT.md](CLOUD-DEPLOYMENT.md) to build the Android app against that URL and test on two phones.

The blueprint uses a paid Starter instance and persistent disk. Review the cost in Render. Use one server instance.

## Project contents

- `app/`: Android application and game WebView assets.
- `server/`: API, WebSockets, game logic, PostgreSQL migrations, and tests.
- [AUTH-SETUP.md](AUTH-SETUP.md): activate Google, Facebook, and Apple sign-in.
- [FEATURES-AND-SETUP.md](FEATURES-AND-SETUP.md): features and service setup.
- [START-HERE.md](START-HERE.md): local development and Android build instructions.

Only environment templates are committed. Keep credentials, signing keys, local databases, uploads, and generated builds outside Git.

## Launch status

Source and deployment configuration are prepared. Publishing this repository does not deploy the server or activate social providers, TURN calls, or Play purchases. Configure those services and complete the physical-phone checks in the deployment guide before public release.
