package io.github.marcobelligoli.sentra.monitoring;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConnectionRepository extends JpaRepository<Connection, Long> {

	/**
	 * Finds the connections of an account as of its last sync.
	 * @param account the monitored account
	 * @param direction {@code FOLLOWER} for its followers, {@code FOLLOWING} for the users it follows
	 * @return the connections, in no particular order
	 */
	List<Connection> findByAccountAndDirection(MonitoredAccount account, Direction direction);

	/**
	 * Finds the connections in {@code direction} with no counterpart in the opposite direction: for
	 * {@code FOLLOWER} the users the account does not follow back, for {@code FOLLOWING} the users who do not follow
	 * the account back.
	 * @param account the monitored account
	 * @param direction direction of the connections to return
	 * @return the one-way connections, ordered by username
	 */
	@Query("""
			select c from Connection c
			where c.account = :account and c.direction = :direction
			and not exists (
				select 1 from Connection o
				where o.account = :account and o.direction <> :direction and o.userPk = c.userPk)
			order by c.username""")
	List<Connection> findWithoutCounterpart(MonitoredAccount account, Direction direction);

}
