# GrindPvP

GrindPvP is a Fabric 1.21.11 client mod and a separate FastAPI backend. The
current codebase includes a working client dashboard, ranked-player API,
database-backed tier progression, admin tools, and deployment configuration.
It is not yet a complete public release.

## Fabric client

The client uses the Render API URL
`https://grindpvp-backend-1.onrender.com` by default. Override it for a local
backend using the game JVM property `-Dgrindpvp.apiUrl=https://your-api-host`.
Only HTTPS endpoints and localhost HTTP endpoints are allowed.

The mod opens with **O**. It supports paginated kit/overall leaderboards,
player search and profiles, a protected admin panel, saved local settings, and
short-lived asynchronous API caching. Nametags for registered verified players
can show role, overall position, tier, username, and live ping; each element
has an independent toggle in Settings. Manager names are orange and Moderator
names are cyan. Chat and tab-list names also retain role/title decoration. The
FPS value returned by Minecraft is always multiplied by 1.5 for display; this
does not change rendering performance and has no in-game setting.
Admin player actions accept Minecraft names; the backend resolves
new names through Mojang's official profile service. Admin credentials are sent only to
`POST /admin/login`; the returned bearer token stays in memory and is discarded
when the client closes or the admin logs out.

### Build

Fabric Loom currently requires JDK 25 to run; the mod bytecode targets Java 21.

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mod\gradlew.bat -p mod build
```

The distributable JAR is `mod/build/libs/grindpvp-0.1.0.jar`.

## Backend

See [backend/README.md](./backend/README.md) for local setup, API endpoints,
Render environment variables, and deployment details. SQLite is the local
development default; Render should use PostgreSQL.

## Website

The React/TypeScript website is in [Website/](./Website/) and reads the same
Render backend and PostgreSQL database as the mod. The root `render.yaml`
defines both services and the existing database: `grindpvp-backend` and
`grindpvp-website`. Connect the GitHub repository containing this project to
Render once, then create or update its Blueprint from the repository's root
`render.yaml`. Do not create a second database or a second backend for the
website.

The Blueprint expects the website origin to be
`https://grindpvp-blsu.onrender.com` and the API URL used by the mod to be
`https://grindpvp-backend-1.onrender.com`. If Render assigns a different
website URL, update the backend's `CORS_ORIGINS` value to that exact origin and
redeploy the backend. Set `ADMIN_PASSWORD` to the intended access code in
Render's backend environment settings; it is a server-side secret, not a
frontend build variable.

The API registers database tables and kit/role/title catalogs at startup.
Public player registration is intentionally disabled until the project has a
trusted Minecraft account-ownership flow. Administrators can add a player by
Minecraft name, verify the in-game name, and manage roles, titles, tiers,
matches, and statistics. UUID-based API requests remain supported for existing
integrations. Public leaderboard and name-decoration results include verified
players only.

## Implemented

- Fabric client entry point and modern dashboard screen.
- Backend health/catalog, player profiles, profile search, and paginated
  overall/per-kit leaderboards.
- PostgreSQL/SQLite persistence, match history, and admin action logs.
- Server-side tier progression, streak deranks, unique HT1 challenges, and
  deterministic overall-tier thresholds.
- Scrypt-hashed admin password, expiring opaque bearer sessions, and
  server-side authorization.
- Verified-player presentation data for client-side nametag, chat, and tab-list
  decoration, including overall position and role colors.
- A fixed 1.5x multiplier for the displayed FPS value, without a setting or
  effect on actual rendering performance.
- Local client preferences and a Render Blueprint configuration.

## Not implemented yet

The dashboard/backend foundation is not the entirety of the original master
prompt. In particular, live skin-head rendering, match/stat history screens,
rank-up and derank effects/sounds, and a production Minecraft
account-ownership verification flow still need implementation. Name lookup
checks that a Minecraft profile exists; it does not prove that the player
requesting registration owns that account.

Deploy backend source changes to the Render service before using the new
name-decoration endpoint in a published client build.
