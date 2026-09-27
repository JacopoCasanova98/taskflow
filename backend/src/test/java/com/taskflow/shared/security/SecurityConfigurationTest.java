package com.taskflow.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.shared.security.jwt.AccessTokenService;
import com.taskflow.shared.security.jwt.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigurationTest extends com.taskflow.DatabaseFreePersistenceTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SecurityProblemHandler problems;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ApplicationContext context;

	@Autowired
	private AccessTokenService tokens;

	@Autowired
	private JwtEncoder encoder;

	@Autowired
	private JwtProperties jwtProperties;

	@Autowired
	private Clock clock;

	@AfterEach
	void realFilterRetainsApplicationCookieRepository() throws Exception {
		// The csrf() test postprocessor mutates this shared filter; use the SPA bootstrap instead.
		var repositories = context.getBeansOfType(CsrfTokenRepository.class);
		assertThat(repositories).containsOnlyKeys("csrfTokenRepository");
		var repository = repositories.get("csrfTokenRepository");
		assertThat(repository).isInstanceOf(CookieCsrfTokenRepository.class);
		var chain = context.getBean(SecurityFilterChain.class);
		assertThat(chain.getFilters()).filteredOn(CsrfFilter.class::isInstance)
				.singleElement().satisfies(filter -> assertThat(
						ReflectionTestUtils.getField(filter, "tokenRepository"))
						.isSameAs(repository));
		bootstrapCsrfCookie();
	}

	private Cookie bootstrapCsrfCookie() throws Exception {
		var result = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn();
		Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
		assertThat(cookie).isNotNull();
		assertThat(cookie.getValue()).isNotBlank();
		assertThat(cookie.isHttpOnly()).isFalse();
		assertThat(cookie.getSecure()).isEqualTo(context.getBean(CookieProperties.class).secure());
		assertThat(cookie.getPath()).isEqualTo("/");
		assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
		assertThat(cookie.getDomain()).isNull();
		assertThat(result.getRequest().getSession(false)).isNull();
		return cookie;
	}

	@Test
	void validBearerTokenPassesAuthenticationBoundary() throws Exception {
		String token = tokens.issue(UUID.randomUUID()).value();
		mockMvc.perform(get("/api/security-check").header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void invalidAndExpiredBearerTokensReceiveSameSafe401() throws Exception {
		AccessTokenService expiredTokens = new AccessTokenService(encoder, jwtProperties,
				Clock.offset(clock, Duration.ofHours(-1)));
		String expired = expiredTokens.issue(UUID.randomUUID()).value();
		String valid = tokens.issue(UUID.randomUUID()).value();
		int signature = valid.lastIndexOf('.') + 1;
		String tampered = valid.substring(0, signature) + (valid.charAt(signature) == 'A' ? 'B' : 'A')
				+ valid.substring(signature + 1);
		for (String token : new String[] {"invalid-secret-token", expired, tampered}) {
			var result = mockMvc.perform(get("/api/security-check").header("Authorization", "Bearer " + token))
					.andExpect(status().isUnauthorized())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(header().string("WWW-Authenticate", "Bearer"))
					.andExpect(jsonPath("$.status").value(401))
					.andExpect(jsonPath("$.type").value("urn:taskflow:problem:authentication_required"))
					.andExpect(jsonPath("$.title").value("Authentication required"))
					.andExpect(jsonPath("$.detail").value("Authentication is required to access this resource."))
					.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
					.andExpect(jsonPath("$.instance").value("/api/security-check"))
					.andReturn();
			assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).size()).isEqualTo(6);
			assertThat(result.getResponse().getContentAsString()).doesNotContain(token, "JwtException", "trace");
		}
	}

	@Test
	void protectsUnmatchedApiPathWithSafe401WithoutBrowserLoginOrSession() throws Exception {
		var result = mockMvc.perform(get("/api/security-check").accept(MediaType.TEXT_HTML))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.type").value("urn:taskflow:problem:authentication_required"))
				.andExpect(jsonPath("$.title").value("Authentication required"))
				.andExpect(jsonPath("$.detail").value("Authentication is required to access this resource."))
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.instance").value("/api/security-check"))
				.andExpect(jsonPath("$.exception").doesNotExist())
				.andExpect(jsonPath("$.trace").doesNotExist())
				.andExpect(header().doesNotExist("Location"))
				.andExpect(header().string("WWW-Authenticate", "Bearer"))
				.andReturn();
		assertThat(result.getRequest().getSession(false)).isNull();
		assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).size()).isEqualTo(6);
	}

	@Test
	void healthRemainsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void infrastructureEndpointsAreNotExposed() throws Exception {
		for (String path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/metrics",
				"/actuator/configprops", "/actuator/heapdump", "/h2-console", "/debug"}) {
			mockMvc.perform(get(path)).andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		}
		mockMvc.perform(get("/actuator/health"))
				.andExpect(jsonPath("$.components").doesNotExist())
				.andExpect(jsonPath("$.details").doesNotExist());
	}

	@Test
	void securityHeadersRemainEnabledAndCorsDoesNotTrustArbitraryOrigins() throws Exception {
		mockMvc.perform(get("/api/auth/csrf").secure(true).header("Origin", "https://untrusted.example"))
				.andExpect(status().isNoContent())
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("X-Frame-Options", "DENY"))
				.andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"))
				.andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
		mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(header().doesNotExist("Strict-Transport-Security"));
	}

	@Test
	void doesNotProvideGeneratedLoginLogoutOrUsers() throws Exception {
		mockMvc.perform(get("/login")).andExpect(status().isNotFound());
		Cookie csrf = bootstrapCsrfCookie();
		mockMvc.perform(post("/logout").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
				.andExpect(status().isNotFound());
		assertThat(context.getBeansOfType(UserDetailsService.class).values())
				.hasSize(1).allMatch(service -> service instanceof com.taskflow.auth.security.TaskFlowUserDetailsService);
	}

	@Test
	void preservesCsrfAndRequiresAuthenticationAfterValidCsrf() throws Exception {
		String token = tokens.issue(UUID.randomUUID()).value();
		mockMvc.perform(post("/api/security-check").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(post("/api/security-check"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		Cookie csrf = bootstrapCsrfCookie();
		mockMvc.perform(post("/api/security-check").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}

	@Test
	void accessDeniedHandlerReturnsOnlySafeProblemFields() throws Exception {
		var request = new MockHttpServletRequest("GET", "/api/forbidden-test");
		var response = new MockHttpServletResponse();
		problems.handle(request, response, new AccessDeniedException("secret internal security detail"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		var json = objectMapper.readTree(response.getContentAsString());
		assertThat(json.size()).isEqualTo(6);
		assertThat(json.get("status").asInt()).isEqualTo(403);
		assertThat(json.get("type").asText()).isEqualTo("urn:taskflow:problem:access_denied");
		assertThat(json.get("title").asText()).isEqualTo("Access denied");
		assertThat(json.get("detail").asText()).isEqualTo("You do not have permission to access this resource.");
		assertThat(json.get("code").asText()).isEqualTo("ACCESS_DENIED");
		assertThat(json.get("instance").asText()).isEqualTo("/api/forbidden-test");
		assertThat(response.getContentAsString()).doesNotContain("secret", "AccessDeniedException", "trace");
	}

	@Test
	void correlationWrapsRealSecurityChainWithSafeLevels(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
		String id = "550e8400-e29b-41d4-a716-446655440000";
		try (var security = new com.taskflow.shared.logging.LogCapture(SecurityProblemHandler.class);
				var access = new com.taskflow.shared.logging.LogCapture(com.taskflow.shared.logging.RequestLoggingFilter.class)) {
			mockMvc.perform(get("/api/boards").header("X-Request-ID", id)
					.header("Authorization", "Bearer VERY_SECRET_TEST_TOKEN"))
					.andExpect(status().isUnauthorized()).andExpect(header().string("X-Request-ID", id));
			mockMvc.perform(post("/api/boards").header("X-Request-ID", id))
					.andExpect(status().isForbidden()).andExpect(header().string("X-Request-ID", id));
			assertThat(security.events()).extracting(ch.qos.logback.classic.spi.ILoggingEvent::getLevel)
					.containsExactly(ch.qos.logback.classic.Level.DEBUG, ch.qos.logback.classic.Level.WARN);
			assertThat(security.events()).allSatisfy(event -> {
				assertThat(event.getMDCPropertyMap()).containsEntry("requestId", id);
				assertThat(event.getThrowableProxy()).isNull();
			});
			assertThat(access.events()).hasSize(2);
			assertThat(output.getOut()).contains("[requestId=" + id + "]");
			assertThat(access.messages()).contains("status=401", "status=403").doesNotContain("VERY_SECRET_TEST_TOKEN");
			assertThat(security.messages()).doesNotContain("VERY_SECRET_TEST_TOKEN", "Bearer");
			assertThat(org.slf4j.MDC.get("requestId")).isNull();
		}
	}

}
