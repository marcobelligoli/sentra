package io.github.marcobelligoli.sentra.monitoring;

public enum Direction {

	/** The user follows the monitored account. */
	FOLLOWER,

	/** The monitored account follows the user. */
	FOLLOWING;

	public Direction opposite() {
		return this == FOLLOWER ? FOLLOWING : FOLLOWER;
	}

}
