package io.github.marcobelligoli.sentra.api;

import io.github.marcobelligoli.sentra.config.SentraProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Basic Auth where each monitored account logs in with its own Instagram credentials and sees only its own data.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
			.httpBasic(Customizer.withDefaults())
			.csrf(csrf -> csrf.disable())
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
				.password(passwordEncoder.encode(account.password()))
				.roles("ACCOUNT")
				.build())
			.toList());
	}

}
