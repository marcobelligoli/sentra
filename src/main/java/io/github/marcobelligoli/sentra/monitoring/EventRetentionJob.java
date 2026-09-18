package io.github.marcobelligoli.sentra.monitoring;

import io.github.marcobelligoli.sentra.config.SentraProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Periodically deletes the history of changes older than the configured retention. The current followers and
 * followed users are never deleted.
 */
@Component
public class EventRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(EventRetentionJob.class);

    private final ConnectionEventRepository events;
    private final Duration maxAge;
    private final Clock clock;

    @Autowired
    public EventRetentionJob(ConnectionEventRepository events, SentraProperties properties) {
        this(events, properties, Clock.systemUTC());
    }

    EventRetentionJob(ConnectionEventRepository events, SentraProperties properties, Clock clock) {
        this.events = events;
        this.maxAge = properties.retention().eventMaxAge();
        this.clock = clock;
    }

    /**
     * Deletes the events older than the retention.
     *
     * @return the number of deleted events
     */
    @Scheduled(cron = "${sentra.retention.cron}", zone = "${sentra.sync.zone}")
    public int deleteOldEvents() {
        Instant threshold = clock.instant().minus(maxAge);
        int deleted = events.deleteOccurredBefore(threshold);
        log.info("Deleted {} events older than {} (before {})", deleted, maxAge, threshold);
        return deleted;
    }

}
