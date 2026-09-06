package com.taskflow.shared.security;

import java.util.Map;

import com.taskflow.shared.web.ApiPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CookieProperties.class)
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemHandler problems, CsrfTokenRepository csrfTokens) throws Exception {
		return http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
						.requestMatchers(HttpMethod.GET, ApiPaths.API + "/auth/csrf").permitAll()
						.requestMatchers(HttpMethod.POST, ApiPaths.API + "/auth/register", ApiPaths.API + "/auth/login").permitAll()
						.requestMatchers(ApiPaths.API + "/**").authenticated()
						.anyRequest().permitAll())
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.requestCache(AbstractHttpConfigurer::disable)
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(Customizer.withDefaults())
						.authenticationEntryPoint(problems)
						.accessDeniedHandler(problems))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint(problems)
						.accessDeniedHandler(problems))
				.csrf(csrf -> csrf.csrfTokenRepository(csrfTokens)
						.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
						.withObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
					@Override
					public <O extends CsrfFilter> O postProcess(O filter) {
						// Resource Server adds a Bearer exemption; all unsafe API methods still require CSRF.
						filter.setRequireCsrfProtectionMatcher(CsrfFilter.DEFAULT_CSRF_MATCHER);
						return filter;
					}
				}))
				.build();
	}

	@Bean
	CsrfTokenRepository csrfTokenRepository(CookieProperties cookies) {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Strict").secure(cookies.secure()));
		return repository;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
		return new DelegatingPasswordEncoder("argon2id", Map.of("argon2id", argon2));
	}
}
