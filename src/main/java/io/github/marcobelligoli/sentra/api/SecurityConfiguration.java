package io.github.marcobelligoli.sentra.api;

import io.github.marcobelligoli.sentra.config.SentraProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * Basic Auth where each monitored account logs in with its Instagram username and its API password, and sees only
 * its own data. Addresses with too many failed logins are temporarily blocked.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, LoginAttemptLimiter loginAttemptLimiter) {
        return http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .addFilterBefore(new LoginAttemptFilter(loginAttemptLimiter), BasicAuthenticationFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(SentraProperties properties, PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(properties.accounts()
                .stream()
                .map(account -> User.withUsername(account.username())
                        .password(passwordEncoder.encode(account.apiPassword()))
                        .roles("ACCOUNT")
                        .build())
                .toList());
    }

}
