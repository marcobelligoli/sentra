package io.github.marcobelligoli.sentra.controller;

import io.github.marcobelligoli.sentra.client.instagram.InstagramUser;
import io.github.marcobelligoli.sentra.config.SentraProperties;
import io.github.marcobelligoli.sentra.dto.UsersResponse;
import io.github.marcobelligoli.sentra.exception.AccountNotSyncedException;
import io.github.marcobelligoli.sentra.exception.UnknownAccountException;
import io.github.marcobelligoli.sentra.security.SecurityConfiguration;
import io.github.marcobelligoli.sentra.service.AccountService;
import io.github.marcobelligoli.sentra.service.SyncRunner;
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

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AccountController.class, properties = {
        "sentra.accounts[0].username=Mario.Rossi",
        "sentra.accounts[0].instagram-password=mario-instagram",
        "sentra.accounts[0].api-password=mario-api-password",
        "sentra.accounts[1].username=luigi",
        "sentra.accounts[1].instagram-password=luigi-instagram",
        "sentra.accounts[1].api-password=luigi-api-password",
        "sentra.session-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties(SentraProperties.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AccountService accountService;

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
        List<InstagramUser> users = List.of(new InstagramUser("42", "tizio", "Tizio"));
        given(accountService.fans("mario.rossi"))
                .willReturn(new UsersResponse("mario.rossi", Instant.now(), users.size(), users));

        mvc.perform(get("/api/me/fans").with(httpBasic("Mario.Rossi", "mario-api-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("mario.rossi"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.users[0].username").value("tizio"));
    }

    @Test
    void returnsNotFollowingBackOfTheAuthenticatedAccount() throws Exception {
        given(accountService.notFollowingBack("luigi"))
                .willReturn(new UsersResponse("luigi", Instant.now(), 0, List.of()));

        mvc.perform(get("/api/me/not-following-back").with(httpBasic("luigi", "luigi-api-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void notFoundBeforeTheFirstSync() throws Exception {
        given(accountService.fans("luigi")).willThrow(new AccountNotSyncedException("luigi"));

        mvc.perform(get("/api/me/fans").with(httpBasic("luigi", "luigi-api-password")))
                .andExpect(status().isNotFound());
    }

    @Test
    void triggersSyncOfTheAuthenticatedAccount() throws Exception {
        given(accountService.triggerSync("luigi")).willReturn(new SyncRunner.TriggerResult.Started());

        mvc.perform(post("/api/me/sync").with(httpBasic("luigi", "luigi-api-password")))
                .andExpect(status().isAccepted());

        verify(accountService).triggerSync("luigi");
    }

    @Test
    void forbiddenWhenTheAccountIsNotMonitored() throws Exception {
        given(accountService.triggerSync("luigi")).willThrow(new UnknownAccountException("luigi"));

        mvc.perform(post("/api/me/sync").with(httpBasic("luigi", "luigi-api-password")))
                .andExpect(status().isForbidden());
    }

    @Test
    void conflictWhenASyncIsRunning() throws Exception {
        given(accountService.triggerSync("luigi")).willReturn(new SyncRunner.TriggerResult.AlreadyRunning());

        mvc.perform(post("/api/me/sync").with(httpBasic("luigi", "luigi-api-password")))
                .andExpect(status().isConflict());
    }

    @Test
    void tooManyRequestsWhenTheLastSyncIsTooRecent() throws Exception {
        Instant retryAt = Instant.now().plus(Duration.ofMinutes(40));
        given(accountService.triggerSync("luigi")).willReturn(new SyncRunner.TriggerResult.TooSoon(retryAt));

        mvc.perform(post("/api/me/sync").with(httpBasic("luigi", "luigi-api-password")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("2[34]\\d\\d")))
                .andExpect(jsonPath("$.status").value("too-soon"))
                .andExpect(jsonPath("$.retryAt").value(retryAt.toString()));
    }

    @Test
    void startedResponseHasNoRetryTime() throws Exception {
        given(accountService.triggerSync("luigi")).willReturn(new SyncRunner.TriggerResult.Started());

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
        given(accountService.fans("luigi")).willThrow(new AccountNotSyncedException("luigi"));
        mvc.perform(get("/api/me/fans").with(httpBasic("luigi", "luigi-api-password")).with(from("10.0.0.4")))
                .andExpect(status().isNotFound());
    }

    @Test
    void successfulLoginResetsTheFailures() throws Exception {
        given(accountService.fans("luigi")).willThrow(new AccountNotSyncedException("luigi"));
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
