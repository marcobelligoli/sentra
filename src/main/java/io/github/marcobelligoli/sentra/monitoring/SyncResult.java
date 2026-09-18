package io.github.marcobelligoli.sentra.monitoring;

import java.util.List;

import io.github.marcobelligoli.sentra.instagram.InstagramUser;

/**
 * @param baseline whether this was the first sync of the account, which records the current state without events
 */
public record SyncResult(
		String account,
		boolean baseline,
		int followers,
		int followings,
		List<InstagramUser> newFollowers,
		List<InstagramUser> unfollowers) {
}
