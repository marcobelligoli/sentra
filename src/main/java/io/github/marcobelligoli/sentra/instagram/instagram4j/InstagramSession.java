package io.github.marcobelligoli.sentra.instagram.instagram4j;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "instagram_session")
class InstagramSession {

	@Id
	private String username;

	@Column(name = "session_id", nullable = false)
	private String sessionId;

	@Column(name = "csrf_token", nullable = false)
	private String csrfToken;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected InstagramSession() {
	}

	InstagramSession(String username, String sessionId, String csrfToken) {
		this.username = username;
		this.sessionId = sessionId;
		this.csrfToken = csrfToken;
		this.updatedAt = Instant.now();
	}

	String getUsername() {
		return username;
	}

	String getSessionId() {
		return sessionId;
	}

	String getCsrfToken() {
		return csrfToken;
	}

}
