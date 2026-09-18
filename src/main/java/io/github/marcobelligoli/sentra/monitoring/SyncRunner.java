package io.github.marcobelligoli.sentra.monitoring;

import io.github.marcobelligoli.sentra.config.SentraProperties;
import io.github.marcobelligoli.sentra.instagram.InstagramCredentials;
import io.github.marcobelligoli.sentra.notification.Notifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs syncs on schedule or on demand, never more than one at a time: parallel sessions on Instagram are more likely
 * to be flagged.
 */
@Component
public class SyncRunner {

    private static final Logger log = LoggerFactory.getLogger(SyncRunner.class);

    private final SyncService syncService;
    private final Notifier notifier;
    private final Pacer pacer;
    private final TaskExecutor executor;
    private final List<InstagramCredentials> accounts;
    private final AtomicBoolean running = new AtomicBoolean();

    public SyncRunner(SyncService syncService, Notifier notifier, Pacer pacer,
                      @Qualifier("applicationTaskExecutor") TaskExecutor executor, SentraProperties properties) {
        this.syncService = syncService;
        this.notifier = notifier;
        this.pacer = pacer;
        this.executor = executor;
        this.accounts = properties.accounts();
    }

    @Scheduled(cron = "${sentra.sync.cron}", zone = "${sentra.sync.zone}")
    public void syncAll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Scheduled sync skipped: another sync is running");
            return;
        }
        try {
            for (int i = 0; i < accounts.size(); i++) {
                if (i > 0) {
                    pacer.pause();
                }
                syncSafely(accounts.get(i));
            }
        } finally {
            running.set(false);
        }
    }

    /**
     * Starts the sync of one account in the background.
     *
     * @return {@code false} if another sync is running
     */
    public boolean trigger(InstagramCredentials account) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    syncSafely(account);
                } finally {
                    running.set(false);
                }
            });
            return true;
        } catch (RuntimeException ex) {
            running.set(false);
            throw ex;
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    private void syncSafely(InstagramCredentials account) {
        try {
            syncService.sync(account);
        } catch (RuntimeException ex) {
            log.error("Sync of {} failed: {}", account.username(), ex.getMessage(), ex);
            notifyFailure(account, ex);
        }
    }

    private void notifyFailure(InstagramCredentials account, RuntimeException failure) {
        try {
            notifier.notifySyncFailure(account.username(),
                    failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName());
        } catch (RuntimeException ex) {
            // Most likely the notification channel itself is failing
            log.error("Could not notify the sync failure of {}: {}", account.username(), ex.getMessage());
        }
    }

}
