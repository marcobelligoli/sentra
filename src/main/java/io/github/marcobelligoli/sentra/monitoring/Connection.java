package io.github.marcobelligoli.sentra.monitoring;

import io.github.marcobelligoli.sentra.instagram.InstagramUser;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * A user who currently follows, or is followed by, a monitored account.
 */
@Entity
@Table(name = "instagram_connection")
public class Connection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private MonitoredAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Column(name = "user_pk", nullable = false)
    private String userPk;

    @Column(nullable = false)
    private String username;

    @Column(name = "full_name")
    private String fullName;

    @Column(nullable = false)
    private Instant since;

    protected Connection() {
    }

    Connection(MonitoredAccount account, Direction direction, InstagramUser user, Instant since) {
        this.account = account;
        this.direction = direction;
        this.userPk = user.pk();
        this.since = since;
        update(user);
    }

    void update(InstagramUser user) {
        this.username = user.username();
        this.fullName = user.fullName();
    }

    public InstagramUser toUser() {
        return new InstagramUser(userPk, username, fullName);
    }

    public Instant getSince() {
        return since;
    }

}
