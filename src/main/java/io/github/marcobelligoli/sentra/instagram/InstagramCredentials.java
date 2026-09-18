package io.github.marcobelligoli.sentra.instagram;

import java.util.Locale;

import jakarta.validation.constraints.NotBlank;

public record InstagramCredentials(@NotBlank String username, @NotBlank String password) {

	public InstagramCredentials {
		// Instagram usernames are case-insensitive and always stored lowercase
		username = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
	}

	@Override
	public String toString() {
		return "InstagramCredentials[username=" + username + ", password=****]";
	}

}
