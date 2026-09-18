package io.github.marcobelligoli.sentra.monitoring;

import java.util.List;

import io.github.marcobelligoli.sentra.instagram.InstagramUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionDiffTest {

	private static final InstagramUser ANNA = new InstagramUser("1", "anna", "Anna");
	private static final InstagramUser BRUNO = new InstagramUser("2", "bruno", "Bruno");
	private static final InstagramUser CARLA = new InstagramUser("3", "carla", "Carla");

	@Test
	void detectsAddedAndRemovedUsersByPk() {
		ConnectionDiff diff = ConnectionDiff.between(List.of(ANNA, BRUNO), List.of(BRUNO, CARLA));

		assertThat(diff.added()).containsExactly(CARLA);
		assertThat(diff.removed()).containsExactly(ANNA);
		assertThat(diff.renamed()).isEmpty();
	}

	@Test
	void renamedUserIsNotAnUnfollow() {
		InstagramUser renamed = new InstagramUser("1", "anna_new", "Anna");

		ConnectionDiff diff = ConnectionDiff.between(List.of(ANNA), List.of(renamed));

		assertThat(diff.added()).isEmpty();
		assertThat(diff.removed()).isEmpty();
		assertThat(diff.renamed()).containsExactly(renamed);
	}

	@Test
	void identicalSnapshotsHaveNoChanges() {
		ConnectionDiff diff = ConnectionDiff.between(List.of(ANNA, BRUNO), List.of(BRUNO, ANNA));

		assertThat(diff.added()).isEmpty();
		assertThat(diff.removed()).isEmpty();
		assertThat(diff.renamed()).isEmpty();
	}

}
