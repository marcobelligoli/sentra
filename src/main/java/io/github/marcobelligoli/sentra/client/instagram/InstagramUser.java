package io.github.marcobelligoli.sentra.client.instagram;

/**
 * An Instagram user, identified by its {@code pk}: the username can change over time, the pk cannot.
 */
public record InstagramUser(String pk, String username, String fullName) {
}
