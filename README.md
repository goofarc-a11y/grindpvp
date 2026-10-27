# GrindPvP

GrindPvP consists of a Fabric 1.21.11 client mod and a Spring Boot backend. Use Java 21 for both.

## Build

From this directory:

```powershell
gradle :mod:build
gradle :backend:test
gradle :backend:bootJar
```

The mod jar is written to `mod/build/libs/`. Install it on clients with Fabric Loader 0.20.1 and Fabric API 0.141.6+1.21.11. The menu opens with **G** by default; change the binding under Minecraft's Controls menu.

## Run the backend

The backend uses a local H2 database by default and listens on port 8080:

```powershell
gradle :backend:bootRun
```

For a server deployment, set `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `PORT`, and `GRINDPVP_ADMIN_CODE` in the environment. `DATABASE_URL` must be a JDBC URL, for example `jdbc:postgresql://localhost:5432/grindpvp`. The initial admin code is `1203`; change it before exposing the API to a public network and serve the API over HTTPS. `GRINDPVP_SERVER_TOKEN` is only needed if you control a Minecraft server and want that server to register mod presence; manual duel reporting does not require access to the Minecraft server.

### Deploy on Render

Create a **Docker** Web Service from the repository root. The root `Dockerfile` uses `render-backend.gradle` to build and run only the Spring Boot backend; it supports both the original `backend/src` layout and the flattened `main/java` layout. Do not use `yarn` or enter separate Build/Start Commands. Set `GRINDPVP_ADMIN_CODE` as a Render environment variable. For persistent shared production data, also configure a PostgreSQL database and set `DATABASE_URL` to its JDBC URL, plus `DATABASE_USER` and `DATABASE_PASSWORD`. Render provides `PORT` automatically.

The mod connects to `https://grindpvp-1.onrender.com` by default. To use another backend, set the Minecraft JVM property `-Dgrindpvp.backend-url=https://your-backend.example`.

## Admin API

Every write endpoint requires the `X-Admin-Code` header. The Admin tab in the mod can assign a role to a player who already has a profile and submit match results. Roles are `HELPER`, `MOD`, `ADMIN`, `OWNER`, `VIP`, `CUPWINNER`, and `CUPMASTER`; new profiles default to `MEMBER`.

When you play on a server you do not control, submit the result in the mod's Admin tab using the kit, both Minecraft usernames, and match count. The backend resolves Java usernames through Mojang and creates profiles on the first submitted result. No Minecraft server plugin, server token, or server access is required for duel reports. The API accepts:

```json
{
  "kit": "UHC",
  "winnerName": "Winner",
  "loserName": "Loser",
  "matchesPlayed": 3
}
```

`matchesPlayed` adds that many wins/losses. Beating a higher-tier opponent promotes the winner to the opponent's tier; losing to a lower-tier opponent demotes the loser by one step. A same-tier win advances the winner one step when fewer than two players have a higher tier in that kit. HT1 is kept to a single player. Defeating HT1 promotes the winner to HT1 and moves the former HT1 player to LT1. Overall tier is the nearest configured tier to the mean tier points across all 14 kits.

## API routes

- `GET /api/players/{uuid}` and `GET /api/players/by-name?name=...`
- `GET /api/leaderboards/{kit}` (kit names are enum values, e.g. `SWORD`, `OVERALL`)
- `POST /api/server/players` (server token required; register a player on join)
- `PATCH /api/admin/roles`
- `POST /api/admin/results`
