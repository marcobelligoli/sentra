package io.github.marcobelligoli.sentra.api;

import java.util.List;
import java.util.Optional;

import io.github.marcobelligoli.sentra.config.SentraProperties;
import io.github.marcobelligoli.sentra.instagram.InstagramCredentials;
import io.github.marcobelligoli.sentra.monitoring.Connection;
import io.github.marcobelligoli.sentra.monitoring.ConnectionRepository;
import io.github.marcobelligoli.sentra.monitoring.Direction;
import io.github.marcobelligoli.sentra.monitoring.MonitoredAccount;
import io.github.marcobelligoli.sentra.monitoring.MonitoredAccountRepository;
import io.github.marcobelligoli.sentra.monitoring.SyncRunner;
import io.github.marcobelligoli.sentra.instagram.InstagramUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountController.class, properties = {
		"sentra.accounts[0].username=Mario.Rossi",
		"sentra.accounts[0].password=secret",
		"sentra.accounts[1].username=luigi",
		"sentra.accounts[1].password=other",
		"sentra.session-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" })
@Import(SecurityConfiguration.class)
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
		mvc.perform(get("/api/me/fans").with(httpBasic("mario.rossi", "wrong"))).andExpect(status().isUnauthorized());
	}

	@Test
	void returnsFansOfTheAuthenticatedAccount() throws Exception {
		MonitoredAccount account = mock(MonitoredAccount.class);
		given(account.getUsername()).willReturn("mario.rossi");
		given(accounts.findByUsername("mario.rossi")).willReturn(Optional.of(account));
		Connection fan = mock(Connection.class);
		given(fan.toUser()).willReturn(new InstagramUser("42", "tizio", "Tizio"));
		given(connections.findWithoutCounterpart(account, Direction.FOLLOWER)).willReturn(List.of(fan));

		mvc.perform(get("/api/me/fans").with(httpBasic("Mario.Rossi", "secret")))
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

		mvc.perform(get("/api/me/not-following-back").with(httpBasic("luigi", "other")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.count").value(0));
	}

	@Test
	void notFoundBeforeTheFirstSync() throws Exception {
		given(accounts.findByUsername("luigi")).willReturn(Optional.empty());

		mvc.perform(get("/api/me/fans").with(httpBasic("luigi", "other"))).andExpect(status().isNotFound());
	}

	@Test
	void triggersSyncOfTheAuthenticatedAccount() throws Exception {
		given(syncRunner.trigger(any())).willReturn(true);

		mvc.perform(post("/api/me/sync").with(httpBasic("luigi", "other"))).andExpect(status().isAccepted());

		verify(syncRunner).trigger(eq(new InstagramCredentials("luigi", "other")));
	}

	@Test
	void conflictWhenASyncIsRunning() throws Exception {
		given(syncRunner.trigger(any())).willReturn(false);

		mvc.perform(post("/api/me/sync").with(httpBasic("luigi", "other"))).andExpect(status().isConflict());
	}

}
