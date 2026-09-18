package io.github.marcobelligoli.sentra.api;

import io.github.marcobelligoli.sentra.config.SentraProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts failed logins per client address and blocks an address after too many failures within the lockout window.
 */
@Component
class LoginAttemptLimiter {

    // Bounds the memory used when failures come from many different addresses
    private static final int MAX_TRACKED_ADDRESSES = 10_000;

    private record Attempts(int failures, Instant windowStart, Instant blockedUntil) {
    }

    private final int maxFailures;
    private final Duration lockout;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    @Autowired
    LoginAttemptLimiter(SentraProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginAttemptLimiter(SentraProperties properties, Clock clock) {
        this.maxFailures = properties.security().maxFailedLogins();
        this.lockout = properties.security().lockout();
        this.clock = clock;
    }

    /**
     * @return how long the address is still blocked, or empty if it can try to log in
     */
    Optional<Duration> blockedFor(String address) {
        Attempts current = attempts.get(address);
        if (current == null || current.blockedUntil() == null) {
            return Optional.empty();
        }
        Duration remaining = Duration.between(clock.instant(), current.blockedUntil());
        if (remaining.isNegative() || remaining.isZero()) {
            attempts.remove(address, current);
            return Optional.empty();
        }
        return Optional.of(remaining);
    }

    /**
     * @return {@code true} if this failure blocked the address
     */
    boolean failed(String address) {
        Instant now = clock.instant();
        if (attempts.size() >= MAX_TRACKED_ADDRESSES) {
            removeExpired(now);
        }
        Attempts updated = attempts.compute(address, (key, current) -> {
            if (current == null || current.windowStart().plus(lockout).isBefore(now)) {
                current = new Attempts(0, now, null);
            }
            int failures = current.failures() + 1;
            return new Attempts(failures, current.windowStart(), failures >= maxFailures ? now.plus(lockout) : null);
        });
        return updated.blockedUntil() != null && updated.failures() == maxFailures;
    }

    void succeeded(String address) {
        attempts.remove(address);
    }

    private void removeExpired(Instant now) {
        attempts.values().removeIf(a -> a.blockedUntil() != null ? a.blockedUntil().isBefore(now)
                : a.windowStart().plus(lockout).isBefore(now));
    }

}
