package io.github.marcobelligoli.sentra.client.instagram.web;

import io.github.marcobelligoli.sentra.config.TestProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class SessionCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final SessionCipher cipher = cipher(KEY);

    @Test
    void roundTrip() {
        String encrypted = cipher.encrypt("session-secret", "mario:session_id");

        assertThat(encrypted).doesNotContain("session-secret");
        assertThat(cipher.decrypt(encrypted, "mario:session_id")).contains("session-secret");
    }

    @Test
    void sameValueIsEncryptedDifferentlyEachTime() {
        assertThat(cipher.encrypt("session-secret", "mario:session_id"))
                .isNotEqualTo(cipher.encrypt("session-secret", "mario:session_id"));
    }

    @Test
    void valueCannotBeMovedToAnotherContext() {
        String encrypted = cipher.encrypt("session-secret", "mario:session_id");

        assertThat(cipher.decrypt(encrypted, "luigi:session_id")).isEmpty();
        assertThat(cipher.decrypt(encrypted, "mario:csrf_token")).isEmpty();
    }

    @Test
    void differentKeyOrUnencryptedValueIsNotDecrypted() {
        String encrypted = cipher.encrypt("session-secret", "mario:session_id");

        assertThat(cipher(OTHER_KEY).decrypt(encrypted, "mario:session_id")).isEmpty();
        assertThat(cipher.decrypt("plain-session-id", "mario:session_id")).isEmpty();
    }

    @Test
    void rejectsInvalidKeys() {
        assertThatIllegalStateException().isThrownBy(() -> cipher("not base64!"))
                .withMessageContaining("SENTRA_SESSION_KEY");
        assertThatIllegalStateException()
                .isThrownBy(() -> cipher(Base64.getEncoder().encodeToString(new byte[16])))
                .withMessageContaining("32-byte");
    }

    private static SessionCipher cipher(String key) {
        return new SessionCipher(TestProperties.withSessionKey(key));
    }

}
