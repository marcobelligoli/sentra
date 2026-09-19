package io.github.marcobelligoli.sentra.service;

import io.github.marcobelligoli.sentra.client.instagram.InstagramCredentials;
import io.github.marcobelligoli.sentra.client.instagram.InstagramUser;
import io.github.marcobelligoli.sentra.config.SentraProperties;
import io.github.marcobelligoli.sentra.dto.UsersResponse;
import io.github.marcobelligoli.sentra.entity.Connection;
import io.github.marcobelligoli.sentra.entity.Direction;
import io.github.marcobelligoli.sentra.entity.MonitoredAccount;
import io.github.marcobelligoli.sentra.exception.AccountNotSyncedException;
import io.github.marcobelligoli.sentra.exception.UnknownAccountException;
import io.github.marcobelligoli.sentra.repository.ConnectionRepository;
import io.github.marcobelligoli.sentra.repository.MonitoredAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Operations on a single monitored account, based on the data of its last sync.
 */
@Service
public class AccountService {

    private final MonitoredAccountRepository accounts;
    private final ConnectionRepository connections;
    private final SyncRunner syncRunner;
    private final SentraProperties properties;

    public AccountService(MonitoredAccountRepository accounts, ConnectionRepository connections, SyncRunner syncRunner,
                          SentraProperties properties) {
        this.accounts = accounts;
        this.connections = connections;
        this.syncRunner = syncRunner;
        this.properties = properties;
    }

    /**
     * Users who follow the account but are not followed back.
     *
     * @throws AccountNotSyncedException if the account has never been synced
     */
    @Transactional(readOnly = true)
    public UsersResponse fans(String username) {
        return oneWay(username, Direction.FOLLOWER);
    }

    /**
     * Users followed by the account who do not follow it back.
     *
     * @throws AccountNotSyncedException if the account has never been synced
     */
    @Transactional(readOnly = true)
    public UsersResponse notFollowingBack(String username) {
        return oneWay(username, Direction.FOLLOWING);
    }

    /**
     * Starts a sync of the account in the background, with its Instagram credentials.
     *
     * @throws UnknownAccountException if the username is not a configured account
     */
    public SyncRunner.TriggerResult triggerSync(String username) {
        InstagramCredentials credentials = properties.account(username)
                .map(SentraProperties.Account::instagramCredentials)
                .orElseThrow(() -> new UnknownAccountException(username));
        return syncRunner.trigger(credentials);
    }

    private UsersResponse oneWay(String username, Direction direction) {
        MonitoredAccount account = accounts.findByUsername(username)
                .orElseThrow(() -> new AccountNotSyncedException(username));
        List<InstagramUser> users = connections.findWithoutCounterpart(account, direction)
                .stream()
                .map(Connection::toUser)
                .toList();
        return new UsersResponse(account.getUsername(), account.getLastSyncAt(), users.size(), users);
    }

}
