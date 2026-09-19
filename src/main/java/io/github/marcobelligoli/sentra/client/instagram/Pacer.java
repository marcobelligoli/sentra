package io.github.marcobelligoli.sentra.client.instagram;

import io.github.marcobelligoli.sentra.config.SentraProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Waits a random amount of time between Instagram requests, so the traffic looks less automated.
 */
@Component
public class Pacer {

    private final long minMillis;
    private final long maxMillis;

    public Pacer(SentraProperties properties) {
        this.minMillis = properties.sync().minDelay().toMillis();
        this.maxMillis = Math.max(minMillis, properties.sync().maxDelay().toMillis());
    }

    public void pause() {
        long millis = ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1);
        if (millis == 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pacing Instagram requests", ex);
        }
    }

}
