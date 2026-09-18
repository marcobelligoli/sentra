package io.github.marcobelligoli.sentra.notification;

import io.github.marcobelligoli.sentra.instagram.InstagramUser;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the text of notifications, split into messages no longer than {@code maxLength} characters.
 */
public final class NotificationMessages {

    private NotificationMessages() {
    }

    public static List<String> unfollowers(String account, List<InstagramUser> unfollowers, int maxLength) {
        if (unfollowers.size() == 1) {
            return List.of("Account @" + account + ": " + describe(unfollowers.getFirst()) + " ha smesso di seguirti");
        }
        String header = "Account @" + account + ": " + unfollowers.size() + " persone hanno smesso di seguirti";
        List<String> messages = new ArrayList<>();
        StringBuilder current = new StringBuilder(header);
        for (InstagramUser user : unfollowers) {
            String line = "\n• " + describe(user);
            if (current.length() + line.length() > maxLength) {
                messages.add(current.toString());
                current = new StringBuilder(header + " (continua)");
            }
            current.append(line);
        }
        messages.add(current.toString());
        return messages;
    }

    public static String syncFailure(String account, String reason, int maxLength) {
        String message = "⚠️ Account @" + account + ": controllo dei follower non riuscito\n" + reason;
        return message.length() <= maxLength ? message : message.substring(0, maxLength - 1) + "…";
    }

    private static String describe(InstagramUser user) {
        return StringUtils.hasText(user.fullName()) ? "@" + user.username() + " (" + user.fullName() + ")"
                : "@" + user.username();
    }

}
