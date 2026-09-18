package io.github.marcobelligoli.sentra.instagram.web;

import io.github.marcobelligoli.sentra.config.SentraProperties;
import io.github.marcobelligoli.sentra.config.TestProperties;
import io.github.marcobelligoli.sentra.instagram.InstagramCredentials;
import io.github.marcobelligoli.sentra.instagram.InstagramFetchException;
import io.github.marcobelligoli.sentra.instagram.InstagramUser;
import io.github.marcobelligoli.sentra.instagram.SocialGraph;
import io.github.marcobelligoli.sentra.instagram.web.FakeInstagram.Response;
import io.github.marcobelligoli.sentra.monitoring.Pacer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class WebSocialGraphProviderTest {

    private static final InstagramCredentials MARIO = new InstagramCredentials("mario", "secret");
    private static final String PROFILE = "/api/v1/users/mario/usernameinfo/";
    private static final String FOLLOWERS = "/api/v1/friendships/123/followers/?count=50";
    private static final String FOLLOWING = "/api/v1/friendships/123/following/?count=50";

    private final SentraProperties properties = TestProperties.withAccounts(MARIO);
    private final SessionCipher cipher = new SessionCipher(properties);
    private final InstagramSessionRepository sessions = mock(InstagramSessionRepository.class);

    private FakeInstagram instagram;
    private WebSocialGraphProvider provider;

    @BeforeEach
    void start() throws IOException {
        instagram = new FakeInstagram();
        provider = new WebSocialGraphProvider(new InstagramWebClient(JsonMapper.builder().build(), instagram.uri()),
                sessions, cipher, new Pacer(properties));
        instagram.on("/api/v1/web/login_page/", Response.json(200, "{}", "csrftoken=csrf-1"))
                .on("/api/v1/web/accounts/login/ajax/", Response.json(200, "{\"authenticated\":true,\"status\":\"ok\"}",
                        "csrftoken=csrf-2", "sessionid=123%3Anew"))
                .on(PROFILE, Response.json(200,
                        "{\"user\":{\"id\":\"123\",\"follower_count\":3,\"following_count\":1},\"status\":\"ok\"}"))
                .on(FOLLOWERS, Response.json(200, """
                        {"users":[{"id":"1","username":"anna","full_name":"Anna"},{"pk":2,"username":"bruno","full_name":""}],
                         "next_max_id":"cursor 2","status":"ok"}"""))
                .on(FOLLOWERS + "&max_id=cursor+2", Response.json(200, """
                        {"users":[{"id":"3","username":"carla","full_name":"Carla"}],"status":"ok"}"""))
                .on(FOLLOWING, Response.json(200, """
                        {"users":[{"id":"1","username":"anna","full_name":"Anna"}],"next_max_id":null,"status":"ok"}"""));
    }

    @AfterEach
    void stop() {
        instagram.close();
    }

    @Test
    void logsInStoresTheEncryptedSessionAndFollowsPagination() {
        given(sessions.findById("mario")).willReturn(Optional.empty());

        SocialGraph graph = provider.fetch(MARIO);

        assertThat(graph.followers()).containsExactly(new InstagramUser("1", "anna", "Anna"),
                new InstagramUser("2", "bruno", null), new InstagramUser("3", "carla", "Carla"));
        assertThat(graph.followings()).containsExactly(new InstagramUser("1", "anna", "Anna"));
        assertThat(graph.declaredFollowers()).isEqualTo(3);
        assertThat(graph.declaredFollowings()).isEqualTo(1);

        ArgumentCaptor<InstagramSession> saved = ArgumentCaptor.forClass(InstagramSession.class);
        verify(sessions).save(saved.capture());
        assertThat(saved.getValue().getSessionId()).doesNotContain("123%3Anew");
        assertThat(cipher.decrypt(saved.getValue().getSessionId(), "mario:session_id")).contains("123%3Anew");
    }

    @Test
    void reusesTheStoredSessionWithoutLoggingIn() {
        given(sessions.findById("mario")).willReturn(Optional.of(stored("123%3Aold")));

        provider.fetch(MARIO);

        assertThat(instagram.requests("/api/v1/web/accounts/login/ajax/")).isEmpty();
        assertThat(instagram.requests(PROFILE).getFirst().header("cookie")).startsWith("sessionid=123%3Aold;");
        verify(sessions, never()).save(any());
    }

    @Test
    void expiredSessionTriggersANewLogin() {
        given(sessions.findById("mario")).willReturn(Optional.of(stored("123%3Aold")));
        instagram.on(PROFILE, request -> request.header("cookie").startsWith("sessionid=123%3Aold;")
                ? Response.json(403, "{\"message\":\"login_required\",\"status\":\"fail\"}")
                : Response.json(200, "{\"user\":{\"id\":\"123\",\"follower_count\":3,\"following_count\":1}}"));

        SocialGraph graph = provider.fetch(MARIO);

        assertThat(graph.followers()).hasSize(3);
        verify(sessions).deleteById("mario");
        verify(sessions).save(any());
    }

    @Test
    void repeatedCursorStopsThePagination() {
        given(sessions.findById("mario")).willReturn(Optional.of(stored("123%3Aold")));
        instagram.on(FOLLOWERS + "&max_id=cursor+2", Response.json(200, """
                {"users":[{"id":"3","username":"carla"}],"next_max_id":"cursor 2","status":"ok"}"""));

        SocialGraph graph = provider.fetch(MARIO);

        assertThat(graph.followers()).hasSize(3);
        assertThat(instagram.requests(FOLLOWERS + "&max_id=cursor+2")).hasSize(1);
    }

    @Test
    void otherErrorsAreReportedWithoutLoggingIn() {
        given(sessions.findById("mario")).willReturn(Optional.of(stored("123%3Aold")));
        instagram.on(PROFILE, Response.json(400, "{\"message\":\"challenge_required\",\"status\":\"fail\"}"));

        assertThatExceptionOfType(InstagramFetchException.class).isThrownBy(() -> provider.fetch(MARIO))
                .withMessageContaining("CHALLENGE_REQUIRED");
        assertThat(instagram.requests("/api/v1/web/accounts/login/ajax/")).isEmpty();
    }

    private InstagramSession stored(String sessionId) {
        return new InstagramSession("mario", cipher.encrypt(sessionId, "mario:session_id"),
                cipher.encrypt("csrf-old", "mario:csrf_token"));
    }

}
