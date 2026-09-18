package io.github.marcobelligoli.sentra.monitoring;

import java.time.Instant;

import io.github.marcobelligoli.sentra.instagram.InstagramUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A change detected between two syncs, kept as history.
 */
@Entity
@Table(name = "connection_event")
public class ConnectionEvent {

	public enum Type {
		ADDED, REMOVED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id")
	private MonitoredAccount account;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Direction direction;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Type type;

	@Column(name = "user_pk", nullable = false)
	private String userPk;

	@Column(nullable = false)
	private String username;

	@Column(name = "full_name")
	private String fullName;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	protected ConnectionEvent() {
	}

	ConnectionEvent(MonitoredAccount account, Direction direction, Type type, InstagramUser user, Instant occurredAt) {
		this.account = account;
		this.direction = direction;
		this.type = type;
		this.userPk = user.pk();
		this.username = user.username();
		this.fullName = user.fullName();
		this.occurredAt = occurredAt;
	}

	public Direction getDirection() {
		return direction;
	}

	public Type getType() {
		return type;
	}

	public String getUsername() {
		return username;
	}

}
