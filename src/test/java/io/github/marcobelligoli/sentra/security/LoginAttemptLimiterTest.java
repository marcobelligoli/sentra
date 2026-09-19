package io.github.marcobelligoli.sentra.security;

import io.github.marcobelligoli.sentra.MutableClock;
import io.github.marcobelligoli.sentra.config.TestProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptLimiterTest {

    private final MutableClock clock = new MutableClock();
    // 5 failures, 15 minutes lockout
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(TestProperties.withAccounts(), clock);

    @Test
    void blocksAfterTheMaximumFailuresAndUnblocksAfterTheLockout() {
        for (int i = 0; i < 4; i++) {
            assertThat(limiter.failed("1.2.3.4")).isFalse();
        }
        assertThat(limiter.blockedFor("1.2.3.4")).isEmpty();

        assertThat(limiter.failed("1.2.3.4")).isTrue();
        assertThat(limiter.blockedFor("1.2.3.4")).contains(Duration.ofMinutes(15));

        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.blockedFor("1.2.3.4")).contains(Duration.ofMinutes(5));

        clock.advance(Duration.ofMinutes(5));
        assertThat(limiter.blockedFor("1.2.3.4")).isEmpty();
        assertThat(limiter.failed("1.2.3.4")).isFalse();
    }

    @Test
    void failuresOutsideTheWindowAreForgotten() {
        for (int i = 0; i < 4; i++) {
            limiter.failed("1.2.3.4");
        }
        clock.advance(Duration.ofMinutes(16));

        assertThat(limiter.failed("1.2.3.4")).isFalse();
        assertThat(limiter.blockedFor("1.2.3.4")).isEmpty();
    }

    @Test
    void successResetsTheFailures() {
        for (int i = 0; i < 4; i++) {
            limiter.failed("1.2.3.4");
        }
        limiter.succeeded("1.2.3.4");

        assertThat(limiter.failed("1.2.3.4")).isFalse();
    }

    @Test
    void addressesAreIndependent() {
        for (int i = 0; i < 5; i++) {
            limiter.failed("1.2.3.4");
        }

        assertThat(limiter.blockedFor("1.2.3.4")).isPresent();
        assertThat(limiter.blockedFor("5.6.7.8")).isEmpty();
    }

}
