package io.github.marcobelligoli.sentra.controller;

import io.github.marcobelligoli.sentra.dto.SyncResponse;
import io.github.marcobelligoli.sentra.dto.UsersResponse;
import io.github.marcobelligoli.sentra.service.AccountService;
import io.github.marcobelligoli.sentra.service.SyncRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;

/**
 * Endpoints on the account of the authenticated user, based on the data of the last sync.
 */
@RestController
@RequestMapping("/api/me")
class AccountController {

    private final AccountService accountService;

    AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Users who follow the account but are not followed back.
     */
    @GetMapping("/fans")
    UsersResponse fans(Principal principal) {
        return accountService.fans(principal.getName());
    }

    /**
     * Users followed by the account who do not follow it back.
     */
    @GetMapping("/not-following-back")
    UsersResponse notFollowingBack(Principal principal) {
        return accountService.notFollowingBack(principal.getName());
    }

    /**
     * Starts a sync of the account in the background: {@code 202} if started, {@code 409} if another sync is running,
     * {@code 429} if the last sync of the account is too recent.
     */
    @PostMapping("/sync")
    ResponseEntity<SyncResponse> sync(Principal principal) {
        String username = principal.getName();
        return switch (accountService.triggerSync(username)) {
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

}
