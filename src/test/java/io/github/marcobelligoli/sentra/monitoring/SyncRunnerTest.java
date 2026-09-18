package io.github.marcobelligoli.sentra.monitoring;

import io.github.marcobelligoli.sentra.config.SentraProperties;
import io.github.marcobelligoli.sentra.instagram.InstagramCredentials;
import io.github.marcobelligoli.sentra.instagram.InstagramFetchException;
import io.github.marcobelligoli.sentra.notification.NotificationException;
import io.github.marcobelligoli.sentra.notification.Notifier;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import java.time.Duration;
import java.util.List;

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
    private final SentraProperties properties = new SentraProperties(List.of(MARIO, LUIGI),
            new SentraProperties.Sync(Duration.ZERO, Duration.ZERO, 0.95), new SentraProperties.Telegram(null, null), "unused");
    private final SyncRunner runner = new SyncRunner(syncService, notifier, new Pacer(properties),
            new SyncTaskExecutor(), properties);

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

        assertThat(runner.trigger(LUIGI)).isTrue();

        verify(notifier).notifySyncFailure("luigi", "rate limited");
        assertThat(runner.isRunning()).isFalse();
    }

}
