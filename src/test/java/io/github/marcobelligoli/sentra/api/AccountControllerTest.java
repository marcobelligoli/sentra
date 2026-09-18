package io.github.marcobelligoli.sentra.api;

import io.github.marcobelligoli.sentra.config.SentraProperties;
import io.github.marcobelligoli.sentra.instagram.InstagramCredentials;
import io.github.marcobelligoli.sentra.instagram.InstagramUser;
import io.github.marcobelligoli.sentra.monitoring.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountController.class, properties = {
        "sentra.accounts[0].username=Mario.Rossi",
        "sentra.accounts[0].instagram-password=mario-instagram",
        "sentra.accounts[0].api-password=mario-api-password",
        "sentra.accounts[1].username=luigi",
        "sentra.accounts[1].instagram-password=luigi-instagram",
        "sentra.accounts[1].api-password=luigi-api-password",
        "sentra.session-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@Import({SecurityConfiguration.class, LoginAttemptLimiter.class})
@EnableConfigurationProperties(SentraProperties.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MonitoredAccountRepository accounts;

    @MockitoBean
    private ConnectionRepository connections;

    @MockitoBean
    private SyncRunner syncRunner;

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/me/fans")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        mvc.perform(get("/api/me/fans").with(httpBasic("mario.rossi", "wrong")).with(from("10.0.0.1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTheInstagramPassword() throws Exception {
        mvc.perform(get("/api/me/fans").with(httpBasic("mario.rossi", "mario-instagram")).with(from("10.0.0.2")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsFansOfTheAuthenticatedAccount() throws Exception {
        MonitoredAccount account = mock(MonitoredAccount.class);
        given(account.getUsername()).willReturn("mario.rossi");
        given(accounts.findByUsername("mario.rossi")).willReturn(Optional.of(account));
        Connection fan = mock(Connection.class);
        given(fan.toUser()).willReturn(new InstagramUser("42", "tizio", "Tizio"));
        given(connections.findWithoutCounterpart(account, Direction.FOLLOWER)).willReturn(List.of(fan));

        mvc.perform(get("/api/me/fans").with(httpBasic("Mario.Rossi", "mario-api-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("mario.rossi"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.users[0].username").value("tizio"));
    }

    @Test
    void notFollowingBackUsesTheOtherDirection() throws Exception {
        MonitoredAccount account = mock(MonitoredAccount.class);
        given(accounts.findByUsername("luigi")).willReturn(Optional.of(account));
        given(connections.findWithoutCounterpart(account, Direction.FOLLOWING)).willReturn(List.of());

        mvc.perform(get("/api/me/not-following-back").with(httpBasic("luigi", "luigi-api-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void notFoundBeforeTheFirstSync() throws Exception {
        given(accounts.findByUsername("luigi")).willReturn(Optional.empty());

        mvc.perform(get("/api/me/fans").with(httpBasic("luigi", "luigi-api-password")))
                .andExpect(status().isNotFound());
    }

    @Test
    void triggersSyncWithTheInstagramCredentialsOfTheAuthenticatedAccount() throws Exception {
        given(syncRunner.trigger(any())).willReturn(new SyncRunner.TriggerResult.Started());

        mvc.perform(post("/api/me/sync").with(httpBasic("luigi", "luigi-api-password")))
                .andExpect(status().isAccepted());

        verify(syncRunner).trigger(eq(new InstagramCredentials("luigi", "luigi-instagram")));
    }

    @Test
    void conflictWhenASyncIsRunning() throws Exception {
        given(syncRunner.trigger(any())).willReturn(new SyncRunner.TriggerResult.AlreadyRunning());

        mvc.perform(post("/api/me/sync").with(httpBasic("luigi", "luigi-api-password")))
                .andExpect(status().isConflict());
    }

    @Test
    void tooManyRequestsWhenTheLastSyncIsTooRecent() throws Exception {
        Instant retryAt = Instant.now().plus(Duration.ofMinutes(40));
        given(syncRunner.trigger(any())).willReturn(new SyncRunner.TriggerResult.TooSoon(retryAt));

        mvc.perform(post("/api/me/sync").with(httpBasic("luigi", "luigi-api-password")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("2[34]\\d\\d")))
                .andExpect(jsonPath("$.status").value("too-soon"))
                .andExpect(jsonPath("$.retryAt").value(retryAt.toString()));
    }

    @Test
    void startedResponseHasNoRetryTime() throws Exception {
        given(syncRunner.trigger(any())).willReturn(new SyncRunner.TriggerResult.Started());

        mvc.perform(post("/api/me/sync").with(httpBasic("luigi", "luigi-api-password")))
                .andExpect(jsonPath("$.status").value("started"))
                .andExpect(jsonPath("$.retryAt").doesNotExist());
    }

    @Test
    void addressIsBlockedAfterTooManyFailedLogins() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/me/fans").with(httpBasic("luigi", "wrong-" + i)).with(from("10.0.0.3")))
                    .andExpect(status().isUnauthorized());
        }

        // Even the right password is refused while the address is blocked
        mvc.perform(get("/api/me/fans").with(httpBasic("luigi", "luigi-api-password")).with(from("10.0.0.3")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // Requests without credentials are not blocked, they just need to authenticate
        mvc.perform(get("/api/me/fans").with(from("10.0.0.3"))).andExpect(status().isUnauthorized());
        // Other addresses are not affected
        given(accounts.findByUsername("luigi")).willReturn(Optional.empty());
        mvc.perform(get("/api/me/fans").with(httpBasic("luigi", "luigi-api-password")).with(from("10.0.0.4")))
                .andExpect(status().isNotFound());
    }

    @Test
    void successfulLoginResetsTheFailures() throws Exception {
        given(accounts.findByUsername("luigi")).willReturn(Optional.empty());
        for (int i = 0; i < 4; i++) {
            mvc.perform(get("/api/me/fans").with(httpBasic("luigi", "wrong")).with(from("10.0.0.5")));
        }
        mvc.perform(get("/api/me/fans").with(httpBasic("luigi", "luigi-api-password")).with(from("10.0.0.5")))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/me/fans").with(httpBasic("luigi", "wrong")).with(from("10.0.0.5")))
                .andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

}
