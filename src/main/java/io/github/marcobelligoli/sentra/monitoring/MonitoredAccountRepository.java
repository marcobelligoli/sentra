package io.github.marcobelligoli.sentra.monitoring;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoredAccountRepository extends JpaRepository<MonitoredAccount, Long> {

	Optional<MonitoredAccount> findByUsername(String username);

}
