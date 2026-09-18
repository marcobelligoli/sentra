package io.github.marcobelligoli.sentra.notification;

import java.util.List;

import io.github.marcobelligoli.sentra.instagram.InstagramUser;

/**
 * Tells the owner of a monitored account who stopped following it, and when an account could not be checked.
 */
public interface Notifier {

	void notifyUnfollowers(String account, List<InstagramUser> unfollowers);

	void notifySyncFailure(String account, String reason);

}
