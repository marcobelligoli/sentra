package io.github.marcobelligoli.sentra.config;

import io.github.marcobelligoli.sentra.instagram.InstagramCredentials;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Validated
@ConfigurationProperties("sentra")
public record SentraProperties(
        @NotEmpty(message = "configure at least one account (SENTRA_ACCOUNTS_0_USERNAME, "
                + "SENTRA_ACCOUNTS_0_INSTAGRAM_PASSWORD, SENTRA_ACCOUNTS_0_API_PASSWORD)")
        List<@Valid Account> accounts,
        @Valid @NotNull Sync sync,
        @Valid @NotNull Security security,
        @NotNull Telegram telegram,
        @NotBlank(message = "configure SENTRA_SESSION_KEY, generate one with 'openssl rand -base64 32'")
        String sessionKey) {

    public Optional<Account> account(String username) {
        return accounts.stream().filter(a -> a.username().equalsIgnoreCase(username)).findFirst();
    }

    /**
     * A monitored account. The same username identifies it on Instagram and on the API, while the two passwords are
     * kept distinct, so a leaked API password does not give access to the Instagram account.
     *
     * @param username          Instagram username, also the API username
     * @param instagramPassword password used only to log in to Instagram
     * @param apiPassword       password of the API Basic Auth
     */
    public record Account(
            @NotBlank String username,
            @NotBlank String instagramPassword,
            @NotBlank @Size(min = 16, message = "must be at least 16 characters, generate one with 'openssl rand -base64 24'")
            String apiPassword) {

        public Account {
            // Instagram usernames are case-insensitive and always stored lowercase
            username = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
        }

        @AssertTrue(message = "the API password must differ from the Instagram password")
        public boolean isApiPasswordDistinct() {
            return apiPassword == null || !Objects.equals(apiPassword, instagramPassword);
        }

        public InstagramCredentials instagramCredentials() {
            return new InstagramCredentials(username, instagramPassword);
        }

        @Override
        public String toString() {
            return "Account[username=" + username + ", instagramPassword=****, apiPassword=****]";
        }

    }

    /**
     * @param minDelay          minimum random pause between Instagram requests
     * @param maxDelay          maximum random pause between Instagram requests
     * @param minCompleteness   share of the profile counters below which a fetch is considered incomplete
     * @param minManualInterval minimum time between the last sync of an account and a manual sync of it
     */
    public record Sync(
            @NotNull Duration minDelay,
            @NotNull Duration maxDelay,
            @DecimalMin("0.0") @DecimalMax("1.0") double minCompleteness,
            @NotNull Duration minManualInterval) {
    }

    /**
     * @param maxFailedLogins failed API logins from the same address before it is blocked
     * @param lockout         how long an address stays blocked, also the window in which failures are counted
     */
    public record Security(@Min(1) int maxFailedLogins, @NotNull Duration lockout) {
    }

    public record Telegram(String botToken, String chatId) {

        public boolean enabled() {
            return StringUtils.hasText(botToken) && StringUtils.hasText(chatId);
        }

    }

}
