package io.github.marcobelligoli.sentra.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.marcobelligoli.sentra.config.SentraProperties;
import io.github.marcobelligoli.sentra.instagram.InstagramCredentials;
import io.github.marcobelligoli.sentra.instagram.InstagramUser;
import io.github.marcobelligoli.sentra.monitoring.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Endpoints on the account of the authenticated user, based on the data of the last sync.
 */
@RestController
@RequestMapping("/api/me")
class AccountController {

    private final MonitoredAccountRepository accounts;
    private final ConnectionRepository connections;
    private final SyncRunner syncRunner;
    private final SentraProperties properties;

    AccountController(MonitoredAccountRepository accounts, ConnectionRepository connections, SyncRunner syncRunner,
                      SentraProperties properties) {
        this.accounts = accounts;
        this.connections = connections;
        this.syncRunner = syncRunner;
        this.properties = properties;
    }

    /**
     * Users who follow the account but are not followed back.
     */
    @GetMapping("/fans")
    UsersResponse fans(Principal principal) {
        return oneWay(principal, Direction.FOLLOWER);
    }

    /**
     * Users followed by the account who do not follow it back.
     */
    @GetMapping("/not-following-back")
    UsersResponse notFollowingBack(Principal principal) {
        return oneWay(principal, Direction.FOLLOWING);
    }

    /**
     * Starts a sync of the account in the background: {@code 202} if started, {@code 409} if another sync is running,
     * {@code 429} if the last sync of the account is too recent.
     */
    @PostMapping("/sync")
    ResponseEntity<SyncResponse> sync(Principal principal) {
        InstagramCredentials account = properties.account(principal.getName())
                .map(SentraProperties.Account::instagramCredentials)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        String username = account.username();
        return switch (syncRunner.trigger(account)) {
            case SyncRunner.TriggerResult.Started started ->
                    ResponseEntity.accepted().body(new SyncResponse(username, "started", null));
            case SyncRunner.TriggerResult.AlreadyRunning running ->
                    ResponseEntity.status(HttpStatus.CONFLICT).body(new SyncResponse(username, "already-running", null));
            case SyncRunner.TriggerResult.TooSoon tooSoon -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(
                            Math.max(1, Duration.between(Instant.now(), tooSoon.retryAt()).toSeconds())))
                    .body(new SyncResponse(username, "too-soon", tooSoon.retryAt()));
        };
    }

    private UsersResponse oneWay(Principal principal, Direction direction) {
        MonitoredAccount account = accounts.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "The account has not been synced yet"));
        List<InstagramUser> users = connections.findWithoutCounterpart(account, direction)
                .stream()
                .map(Connection::toUser)
                .toList();
        return new UsersResponse(account.getUsername(), account.getLastSyncAt(), users.size(), users);
    }

    record UsersResponse(String account, Instant lastSyncAt, int count, List<InstagramUser> users) {
    }

    /**
     * @param retryAt when a manual sync will be allowed again, only when the status is {@code too-soon}
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SyncResponse(String account, String status, Instant retryAt) {
    }

}
