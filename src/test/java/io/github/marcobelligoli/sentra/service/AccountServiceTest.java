package io.github.marcobelligoli.sentra.service;

import io.github.marcobelligoli.sentra.client.instagram.InstagramCredentials;
import io.github.marcobelligoli.sentra.client.instagram.InstagramUser;
import io.github.marcobelligoli.sentra.config.TestProperties;
import io.github.marcobelligoli.sentra.dto.UsersResponse;
import io.github.marcobelligoli.sentra.entity.Connection;
import io.github.marcobelligoli.sentra.entity.Direction;
import io.github.marcobelligoli.sentra.entity.MonitoredAccount;
import io.github.marcobelligoli.sentra.exception.AccountNotSyncedException;
import io.github.marcobelligoli.sentra.exception.UnknownAccountException;
import io.github.marcobelligoli.sentra.repository.ConnectionRepository;
import io.github.marcobelligoli.sentra.repository.MonitoredAccountRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class AccountServiceTest {

    private static final InstagramCredentials LUIGI = new InstagramCredentials("luigi", "luigi-instagram");

    private final MonitoredAccountRepository accounts = mock(MonitoredAccountRepository.class);
    private final ConnectionRepository connections = mock(ConnectionRepository.class);
    private final SyncRunner syncRunner = mock(SyncRunner.class);
    private final AccountService service = new AccountService(accounts, connections, syncRunner,
            TestProperties.withAccounts(LUIGI));

    @Test
    void fansAreTheFollowersWithoutCounterpart() {
        Instant lastSync = Instant.parse("2026-09-18T10:00:00Z");
        MonitoredAccount account = account("luigi", lastSync);
        Connection fan = mock(Connection.class);
        given(fan.toUser()).willReturn(new InstagramUser("42", "tizio", "Tizio"));
        given(connections.findWithoutCounterpart(account, Direction.FOLLOWER)).willReturn(List.of(fan));

        UsersResponse response = service.fans("luigi");

        assertThat(response).isEqualTo(new UsersResponse("luigi", lastSync, 1,
                List.of(new InstagramUser("42", "tizio", "Tizio"))));
    }

    @Test
    void notFollowingBackUsesTheOtherDirection() {
        MonitoredAccount account = account("luigi", null);
        given(connections.findWithoutCounterpart(account, Direction.FOLLOWING)).willReturn(List.of());

        assertThat(service.notFollowingBack("luigi").count()).isZero();
        verify(connections).findWithoutCounterpart(account, Direction.FOLLOWING);
    }

    @Test
    void accountNeverSyncedIsReported() {
        given(accounts.findByUsername("luigi")).willReturn(Optional.empty());

        assertThatExceptionOfType(AccountNotSyncedException.class).isThrownBy(() -> service.fans("luigi"));
    }

    @Test
    void syncUsesTheInstagramCredentialsOfTheAccount() {
        given(syncRunner.trigger(LUIGI)).willReturn(new SyncRunner.TriggerResult.Started());

        assertThat(service.triggerSync("luigi")).isInstanceOf(SyncRunner.TriggerResult.Started.class);
        verify(syncRunner).trigger(LUIGI);
    }

    @Test
    void syncOfAnUnknownAccountIsRefused() {
        assertThatExceptionOfType(UnknownAccountException.class).isThrownBy(() -> service.triggerSync("mario"));
        verify(syncRunner, never()).trigger(any());
    }

    private MonitoredAccount account(String username, Instant lastSyncAt) {
        MonitoredAccount account = mock(MonitoredAccount.class);
        given(account.getUsername()).willReturn(username);
        given(account.getLastSyncAt()).willReturn(lastSyncAt);
        given(accounts.findByUsername(username)).willReturn(Optional.of(account));
        return account;
    }

}
