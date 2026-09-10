package com.taskflow.shared.error;

import java.net.URI;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

public final class ProblemDetails {

	private ProblemDetails() {
	}

	public static ProblemDetail problem(HttpStatusCode status, String title, String detail,
			String code, HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setType(URI.create("urn:taskflow:problem:" + code.toLowerCase(Locale.ROOT)));
		problem.setInstance(URI.create(request.getRequestURI()));
		problem.setProperty("code", code);
		return problem;
	}
}
