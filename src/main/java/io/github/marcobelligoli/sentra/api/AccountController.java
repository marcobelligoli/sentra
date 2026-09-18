package io.github.marcobelligoli.sentra.api;

import io.github.marcobelligoli.sentra.config.SentraProperties;
import io.github.marcobelligoli.sentra.instagram.InstagramCredentials;
import io.github.marcobelligoli.sentra.instagram.InstagramUser;
import io.github.marcobelligoli.sentra.monitoring.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
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
     * Starts a sync of the account in the background.
     */
    @PostMapping("/sync")
    ResponseEntity<SyncResponse> sync(Principal principal) {
        InstagramCredentials account = properties.account(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (!syncRunner.trigger(account)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A sync is already running");
        }
        return ResponseEntity.accepted().body(new SyncResponse(account.username(), "started"));
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

    record SyncResponse(String account, String status) {
    }

}
