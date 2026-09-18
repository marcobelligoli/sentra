package io.github.marcobelligoli.sentra.instagram.web;

import org.springframework.data.jpa.repository.JpaRepository;

interface InstagramSessionRepository extends JpaRepository<InstagramSession, String> {
}
