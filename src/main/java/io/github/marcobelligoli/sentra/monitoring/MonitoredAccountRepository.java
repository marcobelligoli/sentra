package io.github.marcobelligoli.sentra.monitoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MonitoredAccountRepository extends JpaRepository<MonitoredAccount, Long> {

    /**
     * Finds a monitored account by its Instagram username.
     *
     * @param username the lowercase Instagram username
     * @return the account, or empty if it has never been synced
     */
    Optional<MonitoredAccount> findByUsername(String username);

}
