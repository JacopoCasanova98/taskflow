package com.taskflow.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigurationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SecurityProblemHandler problems;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ApplicationContext context;

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
				.andExpect(header().doesNotExist("WWW-Authenticate"))
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
	void doesNotProvideGeneratedLoginLogoutOrUsers() throws Exception {
		mockMvc.perform(get("/login")).andExpect(status().isNotFound());
		mockMvc.perform(post("/logout").with(csrf())).andExpect(status().isNotFound());
		assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
	}

	@Test
	void preservesCsrfAndRequiresAuthenticationAfterValidCsrf() throws Exception {
		mockMvc.perform(post("/api/security-check"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(post("/api/security-check").with(csrf()))
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
}
