package io.github.marcobelligoli.sentra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The username is not among the configured monitored accounts.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class UnknownAccountException extends RuntimeException {

    public UnknownAccountException(String username) {
        super("The account " + username + " is not monitored");
    }

}
