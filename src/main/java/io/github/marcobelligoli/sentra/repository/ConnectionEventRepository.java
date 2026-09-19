package io.github.marcobelligoli.sentra.repository;

import io.github.marcobelligoli.sentra.entity.ConnectionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface ConnectionEventRepository extends JpaRepository<ConnectionEvent, Long> {

    /**
     * Deletes the events that occurred before a given instant, of all the accounts.
     *
     * @param threshold events that occurred strictly before this instant are deleted
     * @return the number of deleted events
     */
    @Transactional
    @Modifying
    @Query("delete from ConnectionEvent e where e.occurredAt < :threshold")
    int deleteOccurredBefore(Instant threshold);

}
