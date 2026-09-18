package io.github.marcobelligoli.sentra.monitoring;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoredAccountRepository extends JpaRepository<MonitoredAccount, Long> {

	/**
	 * Finds a monitored account by its Instagram username.
	 * @param username the lowercase Instagram username
	 * @return the account, or empty if it has never been synced
	 */
	Optional<MonitoredAccount> findByUsername(String username);

}
