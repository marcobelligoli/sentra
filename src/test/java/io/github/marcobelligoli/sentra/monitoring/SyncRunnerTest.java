package io.github.marcobelligoli.sentra.monitoring;

import io.github.marcobelligoli.sentra.MutableClock;
import io.github.marcobelligoli.sentra.config.SentraProperties;
import io.github.marcobelligoli.sentra.config.TestProperties;
import io.github.marcobelligoli.sentra.instagram.InstagramCredentials;
import io.github.marcobelligoli.sentra.instagram.InstagramFetchException;
import io.github.marcobelligoli.sentra.monitoring.SyncRunner.TriggerResult;
import io.github.marcobelligoli.sentra.notification.NotificationException;
import io.github.marcobelligoli.sentra.notification.Notifier;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

class SyncRunnerTest {

    private static final InstagramCredentials MARIO = new InstagramCredentials("mario", "secret");
    private static final InstagramCredentials LUIGI = new InstagramCredentials("luigi", "other");

    private final SyncService syncService = mock(SyncService.class);
    private final Notifier notifier = mock(Notifier.class);
    private final MonitoredAccountRepository monitoredAccounts = mock(MonitoredAccountRepository.class);
    private final MutableClock clock = new MutableClock();
    // Minimum interval between manual syncs: 1 hour
    private final SentraProperties properties = TestProperties.withAccounts(MARIO, LUIGI);
    private final SyncRunner runner = new SyncRunner(syncService, notifier, new Pacer(properties),
            new SyncTaskExecutor(), monitoredAccounts, properties, clock);

    @Test
    void failureIsNotifiedAndOtherAccountsAreStillSynced() {
        given(syncService.sync(MARIO)).willThrow(new InstagramFetchException("login failed"));

        runner.syncAll();

        verify(notifier).notifySyncFailure("mario", "login failed");
        verify(syncService).sync(LUIGI);
        verify(notifier, never()).notifySyncFailure(eq("luigi"), any());
        assertThat(runner.isRunning()).isFalse();
    }

    @Test
    void failingNotifierDoesNotStopTheRun() {
        given(syncService.sync(MARIO)).willThrow(new InstagramFetchException("login failed"));
        willThrow(new NotificationException("Telegram down")).given(notifier).notifySyncFailure(any(), any());

        runner.syncAll();

        verify(syncService).sync(LUIGI);
        assertThat(runner.isRunning()).isFalse();
    }

    @Test
    void manualTriggerFailureIsNotified() {
        given(syncService.sync(LUIGI)).willThrow(new InstagramFetchException("rate limited"));

        assertThat(runner.trigger(LUIGI)).isInstanceOf(TriggerResult.Started.class);

        verify(notifier).notifySyncFailure("luigi", "rate limited");
        assertThat(runner.isRunning()).isFalse();
    }

    @Test
    void manualSyncIsRefusedWithinTheIntervalFromTheLastOne() {
        runner.trigger(LUIGI);
        clock.advance(Duration.ofMinutes(20));

        assertThat(runner.trigger(LUIGI))
                .isEqualTo(new TriggerResult.TooSoon(Instant.parse("2026-09-18T11:00:00Z")));
        verify(syncService, times(1)).sync(LUIGI);

        clock.advance(Duration.ofMinutes(40));
        assertThat(runner.trigger(LUIGI)).isInstanceOf(TriggerResult.Started.class);
        verify(syncService, times(2)).sync(LUIGI);
    }

    @Test
    void failedSyncsCountForTheInterval() {
        given(syncService.sync(LUIGI)).willThrow(new InstagramFetchException("rate limited"));
        runner.trigger(LUIGI);

        assertThat(runner.trigger(LUIGI)).isInstanceOf(TriggerResult.TooSoon.class);
    }

    @Test
    void scheduledSyncCountsForTheIntervalButIsNotLimitedByIt() {
        runner.trigger(LUIGI);

        runner.syncAll();
        verify(syncService, times(2)).sync(LUIGI);

        assertThat(runner.trigger(LUIGI)).isInstanceOf(TriggerResult.TooSoon.class);
    }

    @Test
    void intervalIsPerAccount() {
        runner.trigger(LUIGI);

        assertThat(runner.trigger(MARIO)).isInstanceOf(TriggerResult.Started.class);
    }

    @Test
    void lastSyncStoredInTheDatabaseCountsAfterARestart() {
        MonitoredAccount stored = mock(MonitoredAccount.class);
        given(stored.getLastSyncAt()).willReturn(clock.instant().minus(Duration.ofMinutes(30)));
        given(monitoredAccounts.findByUsername("luigi")).willReturn(Optional.of(stored));

        assertThat(runner.trigger(LUIGI))
                .isEqualTo(new TriggerResult.TooSoon(Instant.parse("2026-09-18T10:30:00Z")));
        verify(syncService, never()).sync(any());
    }

}
