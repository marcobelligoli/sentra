package io.github.marcobelligoli.sentra.monitoring;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectionEventRepository extends JpaRepository<ConnectionEvent, Long> {
}
