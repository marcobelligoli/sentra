package io.github.marcobelligoli.sentra.instagram;

public class InstagramFetchException extends RuntimeException {

    public InstagramFetchException(String message) {
        super(message);
    }

    public InstagramFetchException(String message, Throwable cause) {
        super(message, cause);
    }

}
