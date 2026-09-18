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
- Every detected change is kept as history for 30 days, then deleted by a daily job; the current followers and
  followed users are never deleted.

## Configuration

| Environment variable                                        | Description                                                                                                                                                                                                                  |
|-------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SENTRA_ACCOUNTS_0_USERNAME`                                | Instagram username of the first monitored account (`_1_` for the second, and so on), also its API username |
| `SENTRA_ACCOUNTS_0_INSTAGRAM_PASSWORD`                      | Instagram password, used only to log in to Instagram |
| `SENTRA_ACCOUNTS_0_API_PASSWORD`                            | Basic Auth password of the API: at least 16 characters, different from the Instagram password, generate it with `openssl rand -base64 24` |
| `SENTRA_SESSION_KEY`                                        | Required. Base64 32-byte key encrypting the stored Instagram session, generate it with `openssl rand -base64 32` or, in PowerShell, `[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))` |
| `SENTRA_TELEGRAM_BOT_TOKEN`, `SENTRA_TELEGRAM_CHAT_ID`      | Telegram bot and chat receiving notifications; if missing, notifications are only logged                                                                                                                                     |
| `SENTRA_DB_URL`, `SENTRA_DB_USERNAME`, `SENTRA_DB_PASSWORD` | PostgreSQL connection (default `jdbc:postgresql://localhost:5432/sentra`, `sentra`/`sentra`)                                                                                                                                 |
| `SENTRA_SYNC_CRON`                                          | Sync schedule, Spring cron format (default `0 0 18 * * *`, every day at 18:00)                                                                                                                                               |
| `SENTRA_SYNC_ZONE`                                          | Time zone of the sync schedule (default `Europe/Rome`), independent of the time zone of the server                                                                                                                           |
| `SENTRA_EVENT_MAX_AGE`                                     | Age after which the events of the change history are deleted (default `30d`) |
| `SENTRA_RETENTION_CRON`                                     | Schedule of the deletion of old events, in the time zone of `SENTRA_SYNC_ZONE` (default `0 30 3 * * *`, every day at 03:30) |
| `PORT`                                                      | HTTP port (default `8080`)                                                                                                                                                                                                   |

A local database for development can be started with `docker compose up -d`: it listens only on `127.0.0.1` and
uses the development credentials `sentra`/`sentra`, so do not use it in production. The schema is created and updated at startup by
Liquibase, from the changelog in `src/main/resources/db/changelog`.

## API

All the paths are under the `/sentra` context path. Every endpoint except the health check uses HTTP Basic Auth
with the Instagram username and the API password of a monitored account, and works on that account only. The Instagram password is never accepted by the API. Lists reflect the last sync.

After 5 failed logins within 15 minutes, the client address is blocked for 15 minutes (`429 Too Many Requests` with
`Retry-After`), configurable with `sentra.security.max-failed-logins` and `sentra.security.lockout`. Requests
without credentials are never blocked.

| Endpoint                         | Description                                                                                    |
|----------------------------------|------------------------------------------------------------------------------------------------|
| `GET /sentra/api/me/fans`        | Users who follow you but you don't follow back                                                 |
| `GET /sentra/api/me/not-following-back` | Users you follow who don't follow you back                                                     |
| `POST /sentra/api/me/sync`       | Starts a sync of your account in the background: `202` if started, `409` if a sync is already running, `429` with `Retry-After` and `retryAt` if the last sync of the account is less than 1 hour old (`sentra.sync.min-manual-interval`) |
| `GET /sentra/actuator/health`  | Public, no authentication: `200 {"status":"UP"}`, or `503 {"status":"DOWN"}` if the database is unreachable. Use it as health check of the hosting service |

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
