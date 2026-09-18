package io.github.marcobelligoli.sentra.instagram;

/**
 * Source of the followers and followed users of an Instagram account.
 */
public interface SocialGraphProvider {

	/**
	 * @throws InstagramFetchException if the data cannot be retrieved
	 */
	SocialGraph fetch(InstagramCredentials account);

}
