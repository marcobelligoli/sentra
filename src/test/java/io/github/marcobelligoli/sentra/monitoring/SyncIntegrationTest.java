package io.github.marcobelligoli.sentra.monitoring;

import io.github.marcobelligoli.sentra.instagram.*;
import io.github.marcobelligoli.sentra.monitoring.ConnectionEvent.Type;
import io.github.marcobelligoli.sentra.notification.Notifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "sentra.accounts[0].username=mario",
        "sentra.accounts[0].password=secret",
        "sentra.sync.cron=-",
        "sentra.sync.min-delay=0s",
        "sentra.sync.max-delay=0s",
        "sentra.session-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@Testcontainers(disabledWithoutDocker = true)
class SyncIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final InstagramCredentials MARIO = new InstagramCredentials("mario", "secret");

    private static final InstagramUser ANNA = new InstagramUser("1", "anna", "Anna");
    private static final InstagramUser BRUNO = new InstagramUser("2", "bruno", "Bruno");
    private static final InstagramUser CARLA = new InstagramUser("3", "carla", "Carla");
    private static final InstagramUser DARIO = new InstagramUser("4", "dario", null);

    @Autowired
    private SyncService syncService;

    @Autowired
    private MonitoredAccountRepository accounts;

    @Autowired
    private ConnectionRepository connections;

    @Autowired
    private ConnectionEventRepository events;

    @MockitoBean
    private SocialGraphProvider provider;

    @MockitoBean
    private Notifier notifier;

    @BeforeEach
    void cleanDatabase() {
        events.deleteAll();
        connections.deleteAll();
        accounts.deleteAll();
    }

    @Test
    void firstSyncIsABaselineWithoutNotifications() {
        given(provider.fetch(MARIO)).willReturn(graph(Set.of(ANNA, BRUNO), Set.of(ANNA)));

        SyncResult result = syncService.sync(MARIO);

        assertThat(result.baseline()).isTrue();
        assertThat(result.unfollowers()).isEmpty();
        assertThat(events.count()).isZero();
        verify(notifier, never()).notifyUnfollowers(any(), any());
    }

    @Test
    void laterSyncNotifiesUnfollowersAndRecordsEvents() {
        given(provider.fetch(MARIO)).willReturn(graph(Set.of(ANNA, BRUNO), Set.of(ANNA)));
        syncService.sync(MARIO);

        InstagramUser annaRenamed = new InstagramUser("1", "anna.b", "Anna");
        given(provider.fetch(MARIO)).willReturn(graph(Set.of(annaRenamed, CARLA), Set.of(annaRenamed, DARIO)));
        SyncResult result = syncService.sync(MARIO);

        assertThat(result.baseline()).isFalse();
        assertThat(result.unfollowers()).containsExactly(BRUNO);
        assertThat(result.newFollowers()).containsExactly(CARLA);
        verify(notifier).notifyUnfollowers("mario", List.of(BRUNO));
        assertThat(events.findAll()).extracting(ConnectionEvent::getDirection, ConnectionEvent::getType,
                        ConnectionEvent::getUsername)
                .containsExactlyInAnyOrder(
                        tuple(Direction.FOLLOWER, Type.REMOVED, "bruno"),
                        tuple(Direction.FOLLOWER, Type.ADDED, "carla"),
                        tuple(Direction.FOLLOWING, Type.ADDED, "dario"));
    }

    @Test
    void oneWayRelationships() {
        given(provider.fetch(MARIO)).willReturn(graph(Set.of(ANNA, BRUNO), Set.of(ANNA, CARLA)));
        syncService.sync(MARIO);
        MonitoredAccount account = accounts.findByUsername("mario").orElseThrow();

        assertThat(connections.findWithoutCounterpart(account, Direction.FOLLOWER)).extracting(Connection::toUser)
                .containsExactly(BRUNO);
        assertThat(connections.findWithoutCounterpart(account, Direction.FOLLOWING)).extracting(Connection::toUser)
                .containsExactly(CARLA);
    }

    @Test
    void incompleteFetchIsDiscarded() {
        given(provider.fetch(MARIO)).willReturn(graph(Set.of(ANNA, BRUNO), Set.of(ANNA)));
        syncService.sync(MARIO);

        given(provider.fetch(MARIO)).willReturn(new SocialGraph(Set.of(ANNA), Set.of(ANNA), 100, 1));

        assertThatExceptionOfType(InstagramFetchException.class).isThrownBy(() -> syncService.sync(MARIO));
        MonitoredAccount account = accounts.findByUsername("mario").orElseThrow();
        assertThat(connections.findByAccountAndDirection(account, Direction.FOLLOWER)).hasSize(2);
        verify(notifier, never()).notifyUnfollowers(any(), any());
    }

    private static SocialGraph graph(Set<InstagramUser> followers, Set<InstagramUser> followings) {
        return new SocialGraph(followers, followings, followers.size(), followings.size());
    }

}
