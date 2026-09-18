package io.github.marcobelligoli.sentra.notification;

import io.github.marcobelligoli.sentra.instagram.InstagramUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMessagesTest {

    @Test
    void singleUnfollower() {
        List<String> messages = NotificationMessages.unfollowers("mario",
                List.of(new InstagramUser("1", "tizio", "Tizio Rossi")), 4000);

        assertThat(messages).containsExactly("Account @mario: @tizio (Tizio Rossi) ha smesso di seguirti");
    }

    @Test
    void multipleUnfollowersWithoutFullName() {
        List<String> messages = NotificationMessages.unfollowers("mario",
                List.of(new InstagramUser("1", "tizio", null), new InstagramUser("2", "caio", "")), 4000);

        assertThat(messages).containsExactly("""
                Account @mario: 2 persone hanno smesso di seguirti
                • @tizio
                • @caio""");
    }

    @Test
    void syncFailure() {
        assertThat(NotificationMessages.syncFailure("mario", "login failed", 4000))
                .isEqualTo("⚠️ Account @mario: controllo dei follower non riuscito\nlogin failed");
    }

    @Test
    void longSyncFailureIsTruncated() {
        assertThat(NotificationMessages.syncFailure("mario", "x".repeat(5000), 100)).hasSize(100).endsWith("…");
    }

    @Test
    void longListIsSplitWithinTheLimit() {
        List<InstagramUser> users = IntStream.range(0, 300)
                .mapToObj(i -> new InstagramUser(String.valueOf(i), "user_" + i, "Full Name " + i))
                .toList();

        List<String> messages = NotificationMessages.unfollowers("mario", users, 1000);

        assertThat(messages).hasSizeGreaterThan(1).allSatisfy(m -> assertThat(m).hasSizeLessThanOrEqualTo(1000));
        assertThat(String.join("\n", messages)).contains("@user_0 ", "@user_299 ");
    }

}
