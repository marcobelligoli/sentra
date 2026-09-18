package io.github.marcobelligoli.sentra.instagram.web;

/**
 * Error returned by the Instagram web API.
 */
class InstagramApiException extends RuntimeException {

	enum Reason {
		/** The session is missing, expired or revoked: a new login is needed. */
		LOGIN_REQUIRED,
		/** Instagram asks to verify the login in the app or by email/SMS. */
		CHALLENGE_REQUIRED,
		TWO_FACTOR_REQUIRED,
		BAD_CREDENTIALS,
		RATE_LIMITED,
		UNEXPECTED_RESPONSE,
		IO
	}

	private final Reason reason;

	InstagramApiException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	InstagramApiException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	Reason getReason() {
		return reason;
	}

}
