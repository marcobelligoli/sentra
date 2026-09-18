package io.github.marcobelligoli.sentra.monitoring;

import io.github.marcobelligoli.sentra.config.SentraProperties;
import io.github.marcobelligoli.sentra.instagram.InstagramCredentials;
import io.github.marcobelligoli.sentra.notification.Notifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Runs syncs on schedule or on demand, never more than one at a time: parallel sessions on Instagram are more likely
 * to be flagged. Manual syncs of an account are also spaced by a minimum interval, to limit the requests to Instagram.
 */
@Component
public class SyncRunner {

    private static final Logger log = LoggerFactory.getLogger(SyncRunner.class);

    /**
     * Outcome of a request to start a manual sync.
     */
    public sealed interface TriggerResult {

        record Started() implements TriggerResult {
        }

        record AlreadyRunning() implements TriggerResult {
        }

        /**
         * @param retryAt when a manual sync of the account will be allowed again
         */
        record TooSoon(Instant retryAt) implements TriggerResult {
        }

    }

    private final SyncService syncService;
    private final Notifier notifier;
    private final Pacer pacer;
    private final TaskExecutor executor;
    private final MonitoredAccountRepository monitoredAccounts;
    private final List<InstagramCredentials> accounts;
    private final Duration minManualInterval;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();
    // Start of the last sync attempt of each account, successful or not
    private final Map<String, Instant> lastAttempts = new ConcurrentHashMap<>();

    @Autowired
    public SyncRunner(SyncService syncService, Notifier notifier, Pacer pacer,
                      @Qualifier("applicationTaskExecutor") TaskExecutor executor,
                      MonitoredAccountRepository monitoredAccounts, SentraProperties properties) {
        this(syncService, notifier, pacer, executor, monitoredAccounts, properties, Clock.systemUTC());
    }

    SyncRunner(SyncService syncService, Notifier notifier, Pacer pacer, TaskExecutor executor,
               MonitoredAccountRepository monitoredAccounts, SentraProperties properties, Clock clock) {
        this.syncService = syncService;
        this.notifier = notifier;
        this.pacer = pacer;
        this.executor = executor;
        this.monitoredAccounts = monitoredAccounts;
        this.accounts = properties.accounts().stream().map(SentraProperties.Account::instagramCredentials).toList();
        this.minManualInterval = properties.sync().minManualInterval();
        this.clock = clock;
    }

    /**
     * Syncs all the accounts, one after the other. Not subject to the minimum interval of manual syncs.
     */
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
     * Starts the sync of one account in the background, unless another sync is running or the last sync of the
     * account is more recent than the minimum interval of manual syncs.
     */
    public TriggerResult trigger(InstagramCredentials account) {
        if (running.get()) {
            return new TriggerResult.AlreadyRunning();
        }
        Optional<Instant> retryAt = nextManualSync(account.username()).filter(clock.instant()::isBefore);
        if (retryAt.isPresent()) {
            return new TriggerResult.TooSoon(retryAt.get());
        }
        if (!running.compareAndSet(false, true)) {
            return new TriggerResult.AlreadyRunning();
        }
        try {
            executor.execute(() -> {
                try {
                    syncSafely(account);
                } finally {
                    running.set(false);
                }
            });
            return new TriggerResult.Started();
        } catch (RuntimeException ex) {
            running.set(false);
            throw ex;
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * The last sync is the latest between the attempts of this process and the last successful sync stored in the
     * database, which survives restarts.
     */
    private Optional<Instant> nextManualSync(String username) {
        Instant lastAttempt = lastAttempts.get(username);
        Instant lastSync = monitoredAccounts.findByUsername(username).map(MonitoredAccount::getLastSyncAt).orElse(null);
        return Stream.of(lastAttempt, lastSync)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .map(last -> last.plus(minManualInterval));
    }

    private void syncSafely(InstagramCredentials account) {
        lastAttempts.put(account.username(), clock.instant());
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
