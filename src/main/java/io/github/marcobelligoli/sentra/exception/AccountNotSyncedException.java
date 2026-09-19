package io.github.marcobelligoli.sentra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The account is configured but has no data yet, because it has never been synced.
 */
@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "The account has not been synced yet")
public class AccountNotSyncedException extends RuntimeException {

    public AccountNotSyncedException(String username) {
        super("The account " + username + " has not been synced yet");
    }

}
