package io.github.marcobelligoli.sentra.instagram.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import io.github.marcobelligoli.sentra.instagram.InstagramCredentials;
import io.github.marcobelligoli.sentra.instagram.InstagramFetchException;
import io.github.marcobelligoli.sentra.instagram.InstagramUser;
import io.github.marcobelligoli.sentra.instagram.SocialGraph;
import io.github.marcobelligoli.sentra.instagram.SocialGraphProvider;
import io.github.marcobelligoli.sentra.instagram.web.InstagramApiException.Reason;
import io.github.marcobelligoli.sentra.monitoring.Pacer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Reads followers and followed users through the Instagram web API. The session is stored encrypted and reused, and
 * a new login is performed only when Instagram reports it as expired.
 */
@Component
class WebSocialGraphProvider implements SocialGraphProvider {

	private static final Logger log = LoggerFactory.getLogger(WebSocialGraphProvider.class);

	static final int PAGE_SIZE = 50;

	private final InstagramWebClient client;
	private final InstagramSessionRepository sessions;
	private final SessionCipher cipher;
	private final Pacer pacer;

	WebSocialGraphProvider(InstagramWebClient client, InstagramSessionRepository sessions, SessionCipher cipher,
			Pacer pacer) {
		this.client = client;
		this.sessions = sessions;
		this.cipher = cipher;
		this.pacer = pacer;
	}

	@Override
	public SocialGraph fetch(InstagramCredentials account) {
		try {
			Optional<InstagramWebSession> restored = restore(account.username());
			if (restored.isPresent()) {
				try {
					return fetch(restored.get(), account.username());
				}
				catch (InstagramApiException ex) {
					if (ex.getReason() != Reason.LOGIN_REQUIRED) {
						throw ex;
					}
					log.info("Instagram session of {} expired, logging in again", account.username());
					sessions.deleteById(account.username());
				}
			}
			return fetch(login(account), account.username());
		}
		catch (InstagramApiException ex) {
			throw new InstagramFetchException(
					"Instagram request failed for " + account.username() + " (" + ex.getReason() + "): "
							+ ex.getMessage(),
					ex);
		}
	}

	private Optional<InstagramWebSession> restore(String username) {
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
		return Optional.of(new InstagramWebSession(sessionId.get(), csrfToken.get()));
	}

	private InstagramWebSession login(InstagramCredentials account) {
		String username = account.username();
		InstagramWebSession session = client.login(username, account.password());
		sessions.save(new InstagramSession(username, cipher.encrypt(session.sessionId(), username + ":session_id"),
				cipher.encrypt(session.csrfToken(), username + ":csrf_token")));
		log.info("Logged in to Instagram as {}", username);
		return session;
	}

	private SocialGraph fetch(InstagramWebSession session, String username) {
		JsonNode profile = client
			.get(session, "/api/v1/users/" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "/usernameinfo/")
			.path("user");
		String pk = id(profile).orElseThrow(() -> new InstagramApiException(Reason.UNEXPECTED_RESPONSE,
				"The profile of " + username + " has no id"));
		pacer.pause();
		Set<InstagramUser> followers = collect(session, pk, "followers");
		pacer.pause();
		Set<InstagramUser> followings = collect(session, pk, "following");
		log.debug("Fetched {} followers and {} followings for {}", followers.size(), followings.size(), username);
		return new SocialGraph(followers, followings, profile.path("follower_count").asInt(0),
				profile.path("following_count").asInt(0));
	}

	private Set<InstagramUser> collect(InstagramWebSession session, String pk, String edge) {
		Set<InstagramUser> users = new LinkedHashSet<>();
		Set<String> seenCursors = new HashSet<>();
		String cursor = null;
		while (true) {
			String path = "/api/v1/friendships/" + pk + "/" + edge + "/?count=" + PAGE_SIZE
					+ (cursor == null ? "" : "&max_id=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8));
			JsonNode page = client.get(session, path);
			JsonNode list = page.path("users");
			for (JsonNode user : list) {
				id(user).ifPresent(id -> users.add(new InstagramUser(id, user.path("username").asString(""),
						text(user, "full_name").orElse(null))));
			}
			Optional<String> next = text(page, "next_max_id");
			// A repeated cursor would loop forever: stop, the completeness check will reject a short list
			if (next.isEmpty() || list.isEmpty() || !seenCursors.add(next.get())) {
				return users;
			}
			cursor = next.get();
			pacer.pause();
		}
	}

	private static Optional<String> id(JsonNode user) {
		return text(user, "id").or(() -> text(user, "pk_id")).or(() -> text(user, "pk"));
	}

	private static Optional<String> text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull()) {
			return Optional.empty();
		}
		String text = value.asString("");
		return text.isBlank() ? Optional.empty() : Optional.of(text);
	}

}
