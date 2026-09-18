package io.github.marcobelligoli.sentra.config;

import io.github.marcobelligoli.sentra.instagram.InstagramCredentials;

import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Builds {@link SentraProperties} for unit tests, without delays between Instagram requests.
 */
public final class TestProperties {

    public static final String SESSION_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private TestProperties() {
    }

    public static SentraProperties withAccounts(InstagramCredentials... accounts) {
        return with(SESSION_KEY, accounts);
    }

    public static SentraProperties withSessionKey(String sessionKey) {
        return with(sessionKey);
    }

    private static SentraProperties with(String sessionKey, InstagramCredentials... accounts) {
        List<SentraProperties.Account> configured = Arrays.stream(accounts)
                .map(a -> new SentraProperties.Account(a.username(), a.password(), "api-password-of-" + a.username()))
                .toList();
        return new SentraProperties(configured, new SentraProperties.Sync(Duration.ZERO, Duration.ZERO, 0.95),
                new SentraProperties.Security(5, Duration.ofMinutes(15)), new SentraProperties.Telegram(null, null),
                sessionKey);
    }

}
