package io.github.marcobelligoli.sentra.monitoring;

import io.github.marcobelligoli.sentra.instagram.InstagramUser;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Difference between two snapshots of users, matched by pk.
 *
 * @param added   users only in the current snapshot
 * @param removed users only in the previous snapshot
 * @param renamed users in both snapshots whose username or full name changed, with their current values
 */
record ConnectionDiff(List<InstagramUser> added, List<InstagramUser> removed, List<InstagramUser> renamed) {

    static ConnectionDiff between(Collection<InstagramUser> previous, Collection<InstagramUser> current) {
        Map<String, InstagramUser> before = byPk(previous);
        Map<String, InstagramUser> after = byPk(current);
        return new ConnectionDiff(
                after.values().stream().filter(u -> !before.containsKey(u.pk())).toList(),
                before.values().stream().filter(u -> !after.containsKey(u.pk())).toList(),
                after.values().stream()
                        .filter(u -> before.containsKey(u.pk()) && !Objects.equals(before.get(u.pk()), u))
                        .toList());
    }

    private static Map<String, InstagramUser> byPk(Collection<InstagramUser> users) {
        return users.stream().collect(Collectors.toMap(InstagramUser::pk, Function.identity(), (a, b) -> b,
                LinkedHashMap::new));
    }

}
