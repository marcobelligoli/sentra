package io.github.marcobelligoli.sentra.instagram;

/**
 * Source of the followers and followed users of an Instagram account.
 */
public interface SocialGraphProvider {

    /**
     * Fetches the complete lists of followers and followed users of an account, with the counters shown on its
     * profile.
     *
     * @param account credentials of the account to read, used to log in when needed
     * @return the current followers and followed users of the account
     * @throws InstagramFetchException if the data cannot be retrieved
     */
    SocialGraph fetch(InstagramCredentials account);

}
