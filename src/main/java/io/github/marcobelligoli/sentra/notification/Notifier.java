package io.github.marcobelligoli.sentra.notification;

import java.util.List;

import io.github.marcobelligoli.sentra.instagram.InstagramUser;

/**
 * Tells the owner of a monitored account who stopped following it, and when an account could not be checked.
 */
public interface Notifier {

	/**
	 * Notifies the users who stopped following an account since the previous sync.
	 * @param account username of the monitored account
	 * @param unfollowers users who stopped following the account, never empty
	 * @throws NotificationException if the notification cannot be delivered
	 */
	void notifyUnfollowers(String account, List<InstagramUser> unfollowers);

	/**
	 * Notifies that the sync of an account failed, so its changes could not be detected.
	 * @param account username of the monitored account
	 * @param reason description of the failure
	 * @throws NotificationException if the notification cannot be delivered
	 */
	void notifySyncFailure(String account, String reason);

}
