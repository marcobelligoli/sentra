package io.github.marcobelligoli.sentra.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * @param retryAt when a manual sync will be allowed again, only when the status is {@code too-soon}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SyncResponse(String account, String status, Instant retryAt) {
}
