package com.taskflow.shared.security;

import java.util.Map;

import com.taskflow.shared.web.ApiPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemHandler problems) throws Exception {
		return http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
						.requestMatchers(ApiPaths.API + "/**").authenticated()
						.anyRequest().permitAll())
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.requestCache(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint(problems)
						.accessDeniedHandler(problems))
				// Keep CSRF; complete Angular SPA integration with the first public unsafe auth endpoints.
				.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
		return new DelegatingPasswordEncoder("argon2id", Map.of("argon2id", argon2));
	}
}
