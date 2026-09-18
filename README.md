# sentra
A Spring Boot-based Instagram follower monitoring tool that detects follower changes and sends notifications.

## How it works

Once a day (and on demand) sentra downloads followers and followed users of each monitored account through
[instagram4j](https://github.com/instagram4j/instagram4j), stores them in PostgreSQL and compares them with the
previous sync. Users who stopped following an account are notified on Telegram.

- The first sync of an account only records the current state and sends no notifications.
- Users are matched by their Instagram id, so a username change is not reported as an unfollow.
- A fetch returning less than 95% of the counters shown on the profile is discarded, so a pagination error does not
  produce false unfollows.
- The Instagram session is stored in the database, encrypted with AES-256-GCM, and reused; a new login happens only
  when it expires. If the encryption key changes, the stored session is discarded and a new login is performed.

## Configuration

| Environment variable | Description |
|---|---|
| `SENTRA_ACCOUNTS_0_USERNAME`, `SENTRA_ACCOUNTS_0_PASSWORD` | First monitored account (`_1_` for the second, and so on) |
| `SENTRA_SESSION_KEY` | Required. Base64 32-byte key encrypting the stored Instagram session, generate it with `openssl rand -base64 32` or, in PowerShell, `[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))` |
| `SENTRA_TELEGRAM_BOT_TOKEN`, `SENTRA_TELEGRAM_CHAT_ID` | Telegram bot and chat receiving notifications; if missing, notifications are only logged |
| `SENTRA_DB_URL`, `SENTRA_DB_USERNAME`, `SENTRA_DB_PASSWORD` | PostgreSQL connection (default `jdbc:postgresql://localhost:5432/sentra`, `sentra`/`sentra`) |
| `SENTRA_SYNC_CRON` | Sync schedule, Spring cron format (default `0 0 18 * * *`, every day at 18:00) |

A local database can be started with `docker compose up -d`. The schema is created and updated at startup by
Liquibase, from the changelog in `src/main/resources/db/changelog`.

## API

Every endpoint uses HTTP Basic Auth with the Instagram credentials of a monitored account and works on that account
only. Lists reflect the last sync.

| Endpoint | Description |
|---|---|
| `GET /api/me/fans` | Users who follow you but you don't follow back |
| `GET /api/me/not-following-back` | Users you follow who don't follow you back |
| `POST /api/me/sync` | Starts a sync of your account in the background (`202`, or `409` if a sync is already running) |

Basic Auth sends the password in every request: expose the API only over HTTPS.
