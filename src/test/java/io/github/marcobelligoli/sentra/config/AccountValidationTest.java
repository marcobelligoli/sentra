package io.github.marcobelligoli.sentra.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AccountValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validAccount() {
        assertThat(validator.validate(new SentraProperties.Account("mario", "instagram", "a-long-api-password"))).isEmpty();
    }

    @Test
    void apiPasswordIsRequired() {
        assertThat(messages(new SentraProperties.Account("mario", "instagram", null))).isNotEmpty();
    }

    @Test
    void apiPasswordMustDifferFromTheInstagramPassword() {
        String shared = "the-same-long-password";

        assertThat(messages(new SentraProperties.Account("mario", shared, shared)))
                .anyMatch(m -> m.contains("must differ from the Instagram password"));
    }

    @Test
    void apiPasswordMustBeLongEnough() {
        assertThat(messages(new SentraProperties.Account("mario", "instagram", "short")))
                .anyMatch(m -> m.contains("at least 16 characters"));
    }

    @Test
    void usernameIsLowercaseAndPasswordsAreMasked() {
        SentraProperties.Account account = new SentraProperties.Account(" Mario.Rossi ", "ig-s3cret", "api-s3cret-password");

        assertThat(account.username()).isEqualTo("mario.rossi");
        assertThat(account.toString()).doesNotContain("ig-s3cret", "api-s3cret-password");
    }

    private Set<String> messages(SentraProperties.Account account) {
        Set<ConstraintViolation<SentraProperties.Account>> violations = validator.validate(account);
        return violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.toSet());
    }

}
