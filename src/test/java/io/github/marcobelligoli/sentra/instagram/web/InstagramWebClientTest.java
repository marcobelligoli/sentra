package io.github.marcobelligoli.sentra.instagram.web;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.github.marcobelligoli.sentra.instagram.web.FakeInstagram.Response;
import io.github.marcobelligoli.sentra.instagram.web.InstagramApiException.Reason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class InstagramWebClientTest {

	private static final String LOGIN_PAGE = "/api/v1/web/login_page/";
	private static final String LOGIN = "/api/v1/web/accounts/login/ajax/";
	private static final String PROFILE = "/api/v1/users/mario/usernameinfo/";

	private static final InstagramWebSession SESSION = new InstagramWebSession("123%3Aabc%3A7", "csrf-2");

	private final FakeInstagram instagram;
	private final InstagramWebClient client;

	InstagramWebClientTest() throws IOException {
		instagram = new FakeInstagram();
		client = new InstagramWebClient(JsonMapper.builder().build(), instagram.uri());
		instagram.on(LOGIN_PAGE, Response.json(200, "{}", "csrftoken=csrf-1; Path=/; Secure"));
	}

	@AfterEach
	void stop() {
		instagram.close();
	}

	@Test
	void loginSendsCredentialsWithCsrfTokenAndReturnsSessionCookies() {
		instagram.on(LOGIN, Response.json(200, "{\"authenticated\":true,\"status\":\"ok\"}",
				"csrftoken=csrf-2; Path=/", "sessionid=123%3Aabc%3A7; Path=/; HttpOnly"));

		InstagramWebSession session = client.login("mario", "p@ss w&rd");

		assertThat(session).isEqualTo(SESSION);
		assertThat(session.userId()).isEqualTo("123");
		FakeInstagram.Request login = instagram.requests(LOGIN).getFirst();
		assertThat(login.method()).isEqualTo("POST");
		assertThat(login.header("x-csrftoken")).isEqualTo("csrf-1");
		assertThat(login.header("cookie")).isEqualTo("csrftoken=csrf-1");
		String form = URLDecoder.decode(login.body(), StandardCharsets.UTF_8);
		assertThat(form).contains("username=mario").containsPattern("enc_password=#PWD_INSTAGRAM_BROWSER:0:\\d+:p@ss w&rd");
	}

	@Test
	void loginNotAuthenticatedMeansBadCredentials() {
		instagram.on(LOGIN, Response.json(200, "{\"authenticated\":false,\"user\":true,\"status\":\"ok\"}"));

		assertThatExceptionOfType(InstagramApiException.class).isThrownBy(() -> client.login("mario", "wrong"))
			.satisfies(ex -> assertThat(ex.getReason()).isEqualTo(Reason.BAD_CREDENTIALS))
			.satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("wrong"));
	}

	@Test
	void loginChallengeAndTwoFactorAreRecognized() {
		instagram.on(LOGIN, Response.json(400,
				"{\"message\":\"checkpoint_required\",\"checkpoint_url\":\"/challenge/\",\"status\":\"fail\"}"));
		assertThatExceptionOfType(InstagramApiException.class).isThrownBy(() -> client.login("mario", "secret"))
			.satisfies(ex -> assertThat(ex.getReason()).isEqualTo(Reason.CHALLENGE_REQUIRED));

		instagram.on(LOGIN, Response.json(400, "{\"two_factor_required\":true,\"status\":\"fail\"}"));
		assertThatExceptionOfType(InstagramApiException.class).isThrownBy(() -> client.login("mario", "secret"))
			.satisfies(ex -> assertThat(ex.getReason()).isEqualTo(Reason.TWO_FACTOR_REQUIRED));
	}

	@Test
	void getSendsSessionCookies() {
		instagram.on(PROFILE, Response.json(200, "{\"user\":{\"id\":\"123\"},\"status\":\"ok\"}"));

		assertThat(client.get(SESSION, PROFILE).path("user").path("id").asString()).isEqualTo("123");
		assertThat(instagram.requests(PROFILE).getFirst().header("cookie"))
			.isEqualTo("sessionid=123%3Aabc%3A7; ds_user_id=123; csrftoken=csrf-2");
	}

	@Test
	void expiredSessionIsRecognized() {
		instagram.on(PROFILE, Response.json(403, "{\"message\":\"login_required\",\"status\":\"fail\"}"));
		assertThatExceptionOfType(InstagramApiException.class).isThrownBy(() -> client.get(SESSION, PROFILE))
			.satisfies(ex -> assertThat(ex.getReason()).isEqualTo(Reason.LOGIN_REQUIRED));

		instagram.on(PROFILE, new Response(302, "", Map.of("Location", List.of("/accounts/login/"))));
		assertThatExceptionOfType(InstagramApiException.class).isThrownBy(() -> client.get(SESSION, PROFILE))
			.satisfies(ex -> assertThat(ex.getReason()).isEqualTo(Reason.LOGIN_REQUIRED));
	}

	@Test
	void rateLimitIsRecognized() {
		instagram.on(PROFILE, Response.json(429, "<html>Too many requests</html>"));
		assertThatExceptionOfType(InstagramApiException.class).isThrownBy(() -> client.get(SESSION, PROFILE))
			.satisfies(ex -> assertThat(ex.getReason()).isEqualTo(Reason.RATE_LIMITED));

		instagram.on(PROFILE,
				Response.json(400, "{\"message\":\"Please wait a few minutes before you try again.\",\"status\":\"fail\"}"));
		assertThatExceptionOfType(InstagramApiException.class).isThrownBy(() -> client.get(SESSION, PROFILE))
			.satisfies(ex -> assertThat(ex.getReason()).isEqualTo(Reason.RATE_LIMITED));
	}

	@Test
	void nonJsonSuccessIsUnexpected() {
		instagram.on(PROFILE, Response.json(200, "<html>login</html>"));

		assertThatExceptionOfType(InstagramApiException.class).isThrownBy(() -> client.get(SESSION, PROFILE))
			.satisfies(ex -> assertThat(ex.getReason()).isEqualTo(Reason.UNEXPECTED_RESPONSE));
	}

}
