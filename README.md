# sentra

A Spring Boot-based Instagram follower monitoring tool that detects follower changes and sends notifications.

## How it works

Once a day (and on demand) sentra downloads followers and followed users of each monitored account through the
private web API of instagram.com, stores them in PostgreSQL and compares them with the previous sync. Users who stopped
following an account are notified on Telegram.

The Instagram client (`instagram.web` package) is implemented with the JDK HTTP client, without third-party Instagram
libraries, and uses four calls: login page (CSRF token), login, profile info and the paginated followers/following
lists. This API is unofficial and can change without notice.

- The first sync of an account only records the current state and sends no notifications.
- Users are matched by their Instagram id, so a username change is not reported as an unfollow.
- A fetch returning less than 95% of the counters shown on the profile is discarded, so a pagination error does not
  produce false unfollows.
- The Instagram session is stored in the database, encrypted with AES-256-GCM, and reused; a new login happens only
  when it expires. If the encryption key changes, the stored session is discarded and a new login is performed.

## Configuration

| Environment variable                                        | Description                                                                                                                                                                                                                  |
|-------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SENTRA_ACCOUNTS_0_USERNAME`, `SENTRA_ACCOUNTS_0_PASSWORD`  | First monitored account (`_1_` for the second, and so on)                                                                                                                                                                    |
| `SENTRA_SESSION_KEY`                                        | Required. Base64 32-byte key encrypting the stored Instagram session, generate it with `openssl rand -base64 32` or, in PowerShell, `[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))` |
| `SENTRA_TELEGRAM_BOT_TOKEN`, `SENTRA_TELEGRAM_CHAT_ID`      | Telegram bot and chat receiving notifications; if missing, notifications are only logged                                                                                                                                     |
| `SENTRA_DB_URL`, `SENTRA_DB_USERNAME`, `SENTRA_DB_PASSWORD` | PostgreSQL connection (default `jdbc:postgresql://localhost:5432/sentra`, `sentra`/`sentra`)                                                                                                                                 |
| `SENTRA_SYNC_CRON`                                          | Sync schedule, Spring cron format (default `0 0 18 * * *`, every day at 18:00)                                                                                                                                               |
| `SENTRA_SYNC_ZONE`                                          | Time zone of the sync schedule (default `Europe/Rome`), independent of the time zone of the server                                                                                                                           |
| `PORT`                                                      | HTTP port (default `8080`)                                                                                                                                                                                                   |

A local database can be started with `docker compose up -d`. The schema is created and updated at startup by
Liquibase, from the changelog in `src/main/resources/db/changelog`.

## API

Every endpoint uses HTTP Basic Auth with the Instagram credentials of a monitored account and works on that account
only. Lists reflect the last sync.

| Endpoint                         | Description                                                                                    |
|----------------------------------|------------------------------------------------------------------------------------------------|
| `GET /api/me/fans`               | Users who follow you but you don't follow back                                                 |
| `GET /api/me/not-following-back` | Users you follow who don't follow you back                                                     |
| `POST /api/me/sync`              | Starts a sync of your account in the background (`202`, or `409` if a sync is already running) |

Basic Auth sends the password in every request: expose the API only over HTTPS.

## Deployment

The `Dockerfile` builds the application and produces a layered image running as a non-root user on port 8080:

```shell
docker build -t sentra .
docker run -p 8080:8080 --env-file sentra.env sentra
```

Tests are skipped in the image build because they need Docker: run `./mvnw test` before building. On a hosting service,
let the service terminate HTTPS and make sure the plain HTTP port of the container is not publicly reachable. The
application trusts the `X-Forwarded-*` headers of the proxy.
