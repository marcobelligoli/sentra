package io.github.marcobelligoli.sentra.service;

import io.github.marcobelligoli.sentra.MutableClock;
import io.github.marcobelligoli.sentra.config.TestProperties;
import io.github.marcobelligoli.sentra.repository.ConnectionEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class EventRetentionJobTest {

    private final ConnectionEventRepository events = mock(ConnectionEventRepository.class);

    @Test
    void deletesEventsOlderThanTheRetention() {
        // Retention of 30 days, clock at 2026-09-18T10:00:00Z
        EventRetentionJob job = new EventRetentionJob(events, TestProperties.withAccounts(), new MutableClock());
        given(events.deleteOccurredBefore(Instant.parse("2026-08-19T10:00:00Z"))).willReturn(3);

        assertThat(job.deleteOldEvents()).isEqualTo(3);
    }

}
