package io.github.marcobelligoli.sentra.config;

import io.github.marcobelligoli.sentra.instagram.InstagramCredentials;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Validated
@ConfigurationProperties("sentra")
public record SentraProperties(
        @NotEmpty(message = "configure at least one account (SENTRA_ACCOUNTS_0_USERNAME / SENTRA_ACCOUNTS_0_PASSWORD)")
        List<@Valid InstagramCredentials> accounts,
        @Valid @NotNull Sync sync,
        @NotNull Telegram telegram,
        @NotBlank(message = "configure SENTRA_SESSION_KEY, generate one with 'openssl rand -base64 32'")
        String sessionKey) {

    public Optional<InstagramCredentials> account(String username) {
        return accounts.stream().filter(a -> a.username().equalsIgnoreCase(username)).findFirst();
    }

    public record Sync(
            @NotNull Duration minDelay,
            @NotNull Duration maxDelay,
            @DecimalMin("0.0") @DecimalMax("1.0") double minCompleteness) {
    }

    public record Telegram(String botToken, String chatId) {

        public boolean enabled() {
            return StringUtils.hasText(botToken) && StringUtils.hasText(chatId);
        }

    }

}
