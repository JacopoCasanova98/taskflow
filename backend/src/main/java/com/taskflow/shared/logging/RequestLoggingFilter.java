package com.taskflow.shared.logging;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 1)
public class RequestLoggingFilter extends OncePerRequestFilter {
	public static final String HEADER = "X-Request-ID";
	public static final String MDC_KEY = "requestId";
	private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);
	private static final Pattern UUID_PATTERN = Pattern.compile(
			"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
	private static final Pattern API_PATH = Pattern.compile(
			"/api(?:/(?:auth|csrf|register|login|logout|refresh|me|boards|columns|tasks|search|statistics|order|placement|"
					+ UUID_PATTERN.pattern() + "))*/*");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String supplied = request.getHeader(HEADER);
		String requestId = supplied != null && supplied.length() == 36 && UUID_PATTERN.matcher(supplied).matches()
				? supplied : UUID.randomUUID().toString();
		long started = System.nanoTime();
		boolean failed = false;
		MDC.put(MDC_KEY, requestId);
		try {
			response.setHeader(HEADER, requestId);
			try {
				chain.doFilter(request, response);
			} catch (IOException | ServletException | RuntimeException | Error exception) {
				failed = true;
				throw exception;
			}
		} finally {
			try {
				String path = request.getRequestURI();
				if (path.equals("/api") || path.startsWith("/api/")) {
					// Unknown/malformed routes may contain PII or log injection; retain only known API paths.
					String safePath = path.length() <= 512 && API_PATH.matcher(path).matches() ? path : "/api/[redacted]";
					String method = request.getMethod();
					LOG.info("event=http_request_completed method={} path={} status={} durationMs={}",
							method.matches("[A-Z]{1,16}") ? method : "OTHER", safePath,
							failed ? 500 : response.getStatus(),
							TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
				}
			} finally {
				MDC.remove(MDC_KEY);
			}
		}
	}
}
