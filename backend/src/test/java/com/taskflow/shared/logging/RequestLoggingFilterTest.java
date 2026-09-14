package com.taskflow.shared.logging;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.ServletException;

class RequestLoggingFilterTest {
	private final RequestLoggingFilter filter = new RequestLoggingFilter();

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"not-a-uuid", "1-1-1-1-1", " injected ", "bad\nrequestId", "550e8400-e29b-41d4-a716-446655440000 "})
	void replacesInvalidOrMissingIdsWithoutChangingResponse(String supplied) throws Exception {
		checkId(supplied, false);
	}

	@Test
	void acceptsCanonicalClientUuid() throws Exception {
		checkId("550e8400-e29b-41d4-a716-446655440000", true);
	}

	@Test
	void rejectsUnboundedInput() throws Exception {
		checkId("x".repeat(10000), false);
	}

	private void checkId(String supplied, boolean accepted) throws Exception {
		var request = new MockHttpServletRequest("GET", "/api/boards");
		if (supplied != null) request.addHeader(RequestLoggingFilter.HEADER, supplied);
		var response = new MockHttpServletResponse();
		MDC.put("unrelated", "preserved");
		try {
			filter.doFilter(request, response, (req, res) -> {
				String id = response.getHeader(RequestLoggingFilter.HEADER);
				assertThat(UUID.fromString(id).toString()).isEqualTo(id);
				assertThat(MDC.get("requestId")).isEqualTo(id);
				if (accepted) assertThat(id).isEqualTo(supplied);
				else assertThat(id).isNotEqualTo(supplied);
				response.setStatus(204);
			});
			assertThat(response.getStatus()).isEqualTo(204);
			assertThat(MDC.get("requestId")).isNull();
			assertThat(MDC.get("unrelated")).isEqualTo("preserved");
		} finally {
			MDC.clear();
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {200, 400, 404, 409, 500})
	void completionContainsOnlySafeFieldsAndMdc(int status) throws Exception {
		String path = "/api/boards/550e8400-e29b-41d4-a716-446655440000/tasks/search";
		var request = new MockHttpServletRequest("GET", path);
		request.setQueryString("q=SECRET_SEARCH_TEXT");
		request.addHeader("Authorization", "Bearer VERY_SECRET_TEST_TOKEN");
		request.addHeader("Cookie", "VERY_SECRET_COOKIE");
		request.setContent("VERY_SECRET_BODY".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		var response = new MockHttpServletResponse();
		try (var logs = new LogCapture(RequestLoggingFilter.class)) {
			filter.doFilter(request, response, (req, res) -> response.setStatus(status));
			assertThat(logs.events()).hasSize(1);
			var event = logs.events().getFirst();
			assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.INFO);
			assertThat(event.getFormattedMessage()).contains("event=http_request_completed", "method=GET",
					"path=" + path, "status=" + status).matches(".*durationMs=\\d+$");
			assertThat(event.getMDCPropertyMap()).containsEntry("requestId", response.getHeader("X-Request-ID"));
			assertThat(logs.messages()).doesNotContain("SECRET", "Authorization", "Cookie", "?q=");
			assertThat(event.getThrowableProxy()).isNull();
		}
		assertThat(MDC.get("requestId")).isNull();
	}

	@Test
	void escapingFailureStillLogsOnceWithoutStackAndClearsMdc() {
		var request = new MockHttpServletRequest("POST", "/api/tasks");
		var response = new MockHttpServletResponse();
		try (var logs = new LogCapture(RequestLoggingFilter.class)) {
			assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
				throw new ServletException("deterministic failure");
			})).isInstanceOf(ServletException.class);
			assertThat(logs.events()).hasSize(1);
			assertThat(logs.messages()).contains("status=500").doesNotContain("deterministic failure");
			assertThat(logs.events().getFirst().getThrowableProxy()).isNull();
		}
		assertThat(MDC.get("requestId")).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"/swagger-ui/index.html", "/v3/api-docs", "/favicon.ico", "/actuator/health"})
	void nonApiRequestsReceiveCorrelationWithoutAccessNoise(String path) throws Exception {
		try (var logs = new LogCapture(RequestLoggingFilter.class)) {
			var response = new MockHttpServletResponse();
			filter.doFilter(new MockHttpServletRequest("GET", path), response, (req, res) -> {});
			assertThat(response.getHeader("X-Request-ID")).isNotNull();
			assertThat(logs.events()).isEmpty();
		}
	}

	@Test
	void unknownPathCannotIntroduceUserContent() throws Exception {
		try (var logs = new LogCapture(RequestLoggingFilter.class)) {
			filter.doFilter(new MockHttpServletRequest("GET", "/api/boards/PRIVATE_EMAIL@example.com"),
					new MockHttpServletResponse(), (req, res) -> {});
			assertThat(logs.messages()).contains("path=/api/[redacted]").doesNotContain("PRIVATE_EMAIL");
		}
	}
}
