package io.github.marcobelligoli.sentra;

import java.time.*;

/**
 * Clock for tests, moved forward explicitly.
 */
public final class MutableClock extends Clock {

    private Instant now = Instant.parse("2026-09-18T10:00:00Z");

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

}
