package io.github.marcobelligoli.sentra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * Rejects Basic Auth requests from blocked addresses before authentication, and records the outcome of each login.
 * Registered only in the security filter chain, not as a servlet filter.
 */
class LoginAttemptFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptFilter.class);

    private final LoginAttemptLimiter limiter;

    LoginAttemptFilter(LoginAttemptLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Requests without credentials cannot guess passwords: they are never blocked (e.g. the health check)
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            chain.doFilter(request, response);
            return;
        }

        // Behind the hosting proxy this is the client address taken from X-Forwarded-For
        String address = request.getRemoteAddr();
        Optional<Duration> blocked = limiter.blockedFor(address);
        if (blocked.isPresent()) {
            // Written directly: an error dispatch would go through authentication again and turn into a 401
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, blocked.get().toSeconds())));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"error\":\"Too many failed logins, retry later\"}");
            return;
        }

        chain.doFilter(request, response);

        if (response.getStatus() == HttpStatus.UNAUTHORIZED.value()) {
            if (limiter.failed(address)) {
                log.warn("Too many failed logins from {}: address blocked", address);
            }
        } else {
            limiter.succeeded(address);
        }
    }

}
