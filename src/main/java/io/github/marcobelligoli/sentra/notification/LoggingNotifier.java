package io.github.marcobelligoli.sentra.notification;

import java.util.List;

import io.github.marcobelligoli.sentra.instagram.InstagramUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback used when no notification channel is configured.
 */
class LoggingNotifier implements Notifier {

	private static final Logger log = LoggerFactory.getLogger(LoggingNotifier.class);

	@Override
	public void notifyUnfollowers(String account, List<InstagramUser> unfollowers) {
		NotificationMessages.unfollowers(account, unfollowers, Integer.MAX_VALUE).forEach(log::info);
	}

	@Override
	public void notifySyncFailure(String account, String reason) {
		log.warn(NotificationMessages.syncFailure(account, reason, Integer.MAX_VALUE));
	}

}
