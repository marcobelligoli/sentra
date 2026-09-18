package io.github.marcobelligoli.sentra.instagram.instagram4j;

import org.springframework.data.jpa.repository.JpaRepository;

interface InstagramSessionRepository extends JpaRepository<InstagramSession, String> {
}
