package io.github.marcobelligoli.sentra.instagram.instagram4j;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import com.instagram4j.web.Instagram4j;
import com.instagram4j.web.InstagramException;
import com.instagram4j.web.endpoints.profile.Profile;
import com.instagram4j.web.paginators.ProfilePaginator;
import io.github.marcobelligoli.sentra.instagram.InstagramCredentials;
import io.github.marcobelligoli.sentra.instagram.InstagramFetchException;
import io.github.marcobelligoli.sentra.instagram.InstagramUser;
import io.github.marcobelligoli.sentra.instagram.SocialGraph;
import io.github.marcobelligoli.sentra.instagram.SocialGraphProvider;
import io.github.marcobelligoli.sentra.monitoring.Pacer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads followers and followed users through the web endpoints wrapped by instagram4j. The web session is stored and
 * reused, and a new login is performed only when Instagram reports it as expired.
 */
@Component
class Instagram4jSocialGraphProvider implements SocialGraphProvider {

	private static final Logger log = LoggerFactory.getLogger(Instagram4jSocialGraphProvider.class);

	private final InstagramSessionRepository sessions;
	private final SessionCipher cipher;
	private final Pacer pacer;

	Instagram4jSocialGraphProvider(InstagramSessionRepository sessions, SessionCipher cipher, Pacer pacer) {
		this.sessions = sessions;
		this.cipher = cipher;
		this.pacer = pacer;
	}

	@Override
	public SocialGraph fetch(InstagramCredentials account) {
		Optional<Instagram4j> restored = restore(account.username());
		if (restored.isPresent()) {
			try {
				return fetch(restored.get(), account.username());
			}
			catch (InstagramException ex) {
				if (ex.getReason() != InstagramException.Reasons.LOGIN_EXPIRED) {
					throw failure(account, ex);
				}
				log.info("Instagram session of {} expired, logging in again", account.username());
				sessions.deleteById(account.username());
			}
		}
		try {
			return fetch(login(account), account.username());
		}
		catch (InstagramException ex) {
			throw failure(account, ex);
		}
	}

	private Optional<Instagram4j> restore(String username) {
		Optional<InstagramSession> stored = sessions.findById(username);
		if (stored.isEmpty()) {
			return Optional.empty();
		}
		Optional<String> sessionId = cipher.decrypt(stored.get().getSessionId(), username + ":session_id");
		Optional<String> csrfToken = cipher.decrypt(stored.get().getCsrfToken(), username + ":csrf_token");
		if (sessionId.isEmpty() || csrfToken.isEmpty()) {
			log.warn("Stored Instagram session of {} cannot be decrypted (was SENTRA_SESSION_KEY changed?), "
					+ "logging in again", username);
			sessions.delete(stored.get());
			return Optional.empty();
		}
		return Optional.of(Instagram4j.getInstance(sessionId.get(), csrfToken.get()));
	}

	private Instagram4j login(InstagramCredentials account) throws InstagramException {
		try {
			Instagram4j client = new Instagram4j(account.username(), account.password());
			String username = account.username();
			sessions.save(new InstagramSession(username, cipher.encrypt(client.session, username + ":session_id"),
					cipher.encrypt(client.crsf, username + ":csrf_token")));
			log.info("Logged in to Instagram as {}", username);
			return client;
		}
		catch (IOException ex) {
			throw new InstagramFetchException("Instagram login failed for " + account.username(), ex);
		}
	}

	private SocialGraph fetch(Instagram4j client, String username) throws InstagramException {
		Profile profile = client.getProfile(username);
		pacer.pause();
		Set<InstagramUser> followers = collect(profile.getFollowers(null));
		pacer.pause();
		Set<InstagramUser> followings = collect(profile.getFollowings(null));
		log.debug("Fetched {} followers and {} followings for {}", followers.size(), followings.size(), username);
		return new SocialGraph(followers, followings, profile.followers, profile.followings);
	}

	private Set<InstagramUser> collect(ProfilePaginator paginator) throws InstagramException {
		Set<InstagramUser> users = new LinkedHashSet<>();
		while (paginator.hasNext()) {
			try {
				for (Profile user : paginator.next()) {
					users.add(new InstagramUser(user.pk, user.username, user.name));
				}
			}
			catch (RuntimeException ex) {
				// The paginator wraps InstagramException, unwrap it so an expired session can be told apart
				if (ex.getCause() instanceof InstagramException cause) {
					throw cause;
				}
				throw ex;
			}
			if (paginator.hasNext()) {
				pacer.pause();
			}
		}
		return users;
	}

	private static InstagramFetchException failure(InstagramCredentials account, InstagramException ex) {
		return new InstagramFetchException(
				"Instagram request failed for " + account.username() + " (" + ex.getReason() + "): " + ex.getMessage(),
				ex);
	}

}
