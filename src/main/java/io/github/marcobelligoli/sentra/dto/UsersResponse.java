package io.github.marcobelligoli.sentra.dto;

import io.github.marcobelligoli.sentra.client.instagram.InstagramUser;

import java.time.Instant;
import java.util.List;

/**
 * Users in a one-way relationship with a monitored account, as of its last sync.
 */
public record UsersResponse(String account, Instant lastSyncAt, int count, List<InstagramUser> users) {
}
