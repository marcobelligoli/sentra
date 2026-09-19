package io.github.marcobelligoli.sentra.service;

import io.github.marcobelligoli.sentra.client.instagram.*;
import io.github.marcobelligoli.sentra.client.notification.Notifier;
import io.github.marcobelligoli.sentra.config.SentraProperties;
import io.github.marcobelligoli.sentra.entity.Connection;
import io.github.marcobelligoli.sentra.entity.ConnectionEvent;
import io.github.marcobelligoli.sentra.entity.ConnectionEvent.Type;
import io.github.marcobelligoli.sentra.entity.Direction;
import io.github.marcobelligoli.sentra.entity.MonitoredAccount;
import io.github.marcobelligoli.sentra.repository.ConnectionEventRepository;
import io.github.marcobelligoli.sentra.repository.ConnectionRepository;
import io.github.marcobelligoli.sentra.repository.MonitoredAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Syncs one account: fetches its current followers and followings, stores the differences and notifies the
 * unfollowers.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final SocialGraphProvider provider;
    private final Notifier notifier;
    private final MonitoredAccountRepository accounts;
    private final ConnectionRepository connections;
    private final ConnectionEventRepository events;
    private final TransactionTemplate transaction;
    private final double minCompleteness;

    public SyncService(SocialGraphProvider provider, Notifier notifier, MonitoredAccountRepository accounts,
                       ConnectionRepository connections, ConnectionEventRepository events, TransactionTemplate transaction,
                       SentraProperties properties) {
        this.provider = provider;
        this.notifier = notifier;
        this.accounts = accounts;
        this.connections = connections;
        this.events = events;
        this.transaction = transaction;
        this.minCompleteness = properties.sync().minCompleteness();
    }

    public SyncResult sync(InstagramCredentials credentials) {
        SocialGraph graph = provider.fetch(credentials);
        checkComplete(credentials.username(), "followers", graph.followers().size(), graph.declaredFollowers());
        checkComplete(credentials.username(), "followings", graph.followings().size(), graph.declaredFollowings());

        SyncResult result = transaction.execute(status -> store(credentials.username(), graph));
        log.info("Synced {}: {} followers, {} followings, {} new followers, {} unfollowers{}", result.account(),
                result.followers(), result.followings(), result.newFollowers().size(), result.unfollowers().size(),
                result.baseline() ? " (first sync, no notifications)" : "");

        // Sent after the commit: a notification failure must not lose the detected changes
        if (!result.unfollowers().isEmpty()) {
            notifier.notifyUnfollowers(result.account(), result.unfollowers());
        }
        return result;
    }

    /**
     * A short list usually means a pagination problem: storing it would turn missing users into false unfollows.
     */
    private void checkComplete(String account, String what, int fetched, int declared) {
        if (fetched < Math.floor(declared * minCompleteness)) {
            throw new InstagramFetchException("Incomplete %s list for %s: fetched %d of %d, sync discarded"
                    .formatted(what, account, fetched, declared));
        }
    }

    private SyncResult store(String username, SocialGraph graph) {
        MonitoredAccount account = accounts.findByUsername(username)
                .orElseGet(() -> accounts.save(new MonitoredAccount(username)));
        boolean baseline = account.getLastSyncAt() == null;
        Instant now = Instant.now();

        ConnectionDiff followers = update(account, Direction.FOLLOWER, graph.followers(), baseline, now);
        update(account, Direction.FOLLOWING, graph.followings(), baseline, now);
        account.setLastSyncAt(now);

        return new SyncResult(username, baseline, graph.followers().size(), graph.followings().size(),
                baseline ? List.of() : followers.added(), baseline ? List.of() : followers.removed());
    }

    private ConnectionDiff update(MonitoredAccount account, Direction direction, Set<InstagramUser> current,
                                  boolean baseline, Instant now) {
        Map<String, Connection> stored = connections.findByAccountAndDirection(account, direction)
                .stream()
                .collect(Collectors.toMap(c -> c.toUser().pk(), Function.identity()));
        ConnectionDiff diff = ConnectionDiff.between(stored.values().stream().map(Connection::toUser).toList(),
                current);

        connections.deleteAll(diff.removed().stream().map(u -> stored.get(u.pk())).toList());
        connections.saveAll(diff.added().stream().map(u -> new Connection(account, direction, u, now)).toList());
        diff.renamed().forEach(u -> stored.get(u.pk()).update(u));

        if (!baseline) {
            events.saveAll(diff.added().stream()
                    .map(u -> new ConnectionEvent(account, direction, Type.ADDED, u, now))
                    .toList());
            events.saveAll(diff.removed().stream()
                    .map(u -> new ConnectionEvent(account, direction, Type.REMOVED, u, now))
                    .toList());
        }
        return diff;
    }

}
