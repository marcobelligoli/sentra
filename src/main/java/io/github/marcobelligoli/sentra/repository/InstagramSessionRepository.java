package io.github.marcobelligoli.sentra.repository;

import io.github.marcobelligoli.sentra.entity.InstagramSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstagramSessionRepository extends JpaRepository<InstagramSession, String> {
}
