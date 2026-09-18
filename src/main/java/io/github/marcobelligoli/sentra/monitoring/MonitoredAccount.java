package io.github.marcobelligoli.sentra.monitoring;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "monitored_account")
public class MonitoredAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String username;

	@Column(name = "last_sync_at")
	private Instant lastSyncAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected MonitoredAccount() {
	}

	MonitoredAccount(String username) {
		this.username = username;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	/**
	 * @return the end of the last successful sync, or {@code null} if the account has never been synced
	 */
	public Instant getLastSyncAt() {
		return lastSyncAt;
	}

	void setLastSyncAt(Instant lastSyncAt) {
		this.lastSyncAt = lastSyncAt;
	}

}
