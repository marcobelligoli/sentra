package io.github.marcobelligoli.sentra.client.instagram;

import java.util.Set;

/**
 * Followers and followed users of an account, together with the counters shown on its profile, which are used to
 * detect incomplete fetches.
 */
public record SocialGraph(
        Set<InstagramUser> followers,
        Set<InstagramUser> followings,
        int declaredFollowers,
        int declaredFollowings) {
}
