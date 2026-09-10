package com.taskflow.shared.security;

import java.util.function.Supplier;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

	private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
	private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> token) {
		xor.handle(request, response, token);
		token.get(); // Materialize the deferred cookie for the SPA.
	}

	@Override
	public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
		return (StringUtils.hasText(request.getHeader(token.getHeaderName())) ? plain : xor)
				.resolveCsrfTokenValue(request, token);
	}
}
