package com.taskflow.auth.api;

import com.taskflow.auth.api.dto.AuthenticationResponse;
import com.taskflow.auth.api.dto.LoginRequest;
import com.taskflow.auth.api.dto.RegisterRequest;
import com.taskflow.auth.application.AuthenticationResult;
import com.taskflow.auth.api.dto.CurrentUserResponse;
import com.taskflow.auth.application.CurrentUserService;
import com.taskflow.auth.application.AuthenticationService;
import com.taskflow.auth.application.RefreshSessionLifecycle;
import com.taskflow.shared.error.ProblemDetails;
import com.taskflow.shared.web.ApiPaths;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.API + "/auth")
public class AuthenticationController {
	public static final String REFRESH_COOKIE = RefreshCookie.NAME;
	private final AuthenticationService authentication;
	private final RefreshCookie cookies;
	private final RefreshSessionLifecycle lifecycle;
	private final CsrfTokenRepository csrf;
	private final CurrentUserService currentUser;

	public AuthenticationController(AuthenticationService authentication, RefreshCookie cookies,
			RefreshSessionLifecycle lifecycle, CsrfTokenRepository csrf, CurrentUserService currentUser) {
		this.authentication = authentication;
		this.cookies = cookies;
		this.lifecycle = lifecycle;
		this.csrf = csrf;
		this.currentUser = currentUser;
	}

	@GetMapping("/me")
	ResponseEntity<?> me(HttpServletRequest request) {
		var user = currentUser.currentUser();
		if (user.isPresent()) {
			return ResponseEntity.ok(new CurrentUserResponse(user.get().id(), user.get().email()));
		}
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(ProblemDetails.problem(HttpStatus.UNAUTHORIZED, "Session unavailable",
						"Your session is no longer valid. Please sign in again.", "SESSION_INVALID", request));
	}

	@GetMapping("/csrf")
	ResponseEntity<Void> csrf() { return ResponseEntity.noContent().build(); }

	@PostMapping("/register")
	ResponseEntity<AuthenticationResponse> register(@Valid @RequestBody RegisterRequest body,
			HttpServletRequest request, HttpServletResponse response) {
		return success(authentication.register(body.email(), body.password()), HttpStatus.CREATED, request, response);
	}

	@PostMapping("/login")
	ResponseEntity<AuthenticationResponse> login(@Valid @RequestBody LoginRequest body,
			@CookieValue(name = REFRESH_COOKIE, required = false) String presentedRefresh,
			HttpServletRequest request, HttpServletResponse response) {
		return success(authentication.login(body.email(), body.password(), presentedRefresh), HttpStatus.OK, request, response);
	}

	@PostMapping("/refresh")
	ResponseEntity<?> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String raw,
			HttpServletRequest request, HttpServletResponse response) {
		var result = lifecycle.refresh(raw);
		if (result.isPresent()) { return success(result.get(), HttpStatus.OK, request, response); }
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
				.body(ProblemDetails.problem(HttpStatus.UNAUTHORIZED, "Session unavailable",
						"Your session is no longer valid. Please sign in again.", "SESSION_INVALID", request));
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String raw,
			HttpServletRequest request, HttpServletResponse response) {
		try {
			lifecycle.logout(raw);
		} finally {
			response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
		}
		csrf.saveToken(null, request, response);
		csrf.saveToken(csrf.generateToken(request), request, response);
		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler(BadCredentialsException.class)
	ProblemDetail invalidCredentials(HttpServletRequest request) {
		return ProblemDetails.problem(HttpStatus.UNAUTHORIZED, "Authentication failed",
				"Invalid email or password.", "INVALID_CREDENTIALS", request);
	}

	private ResponseEntity<AuthenticationResponse> success(AuthenticationResult result, HttpStatus status,
			HttpServletRequest request, HttpServletResponse response) {
		// Controller-based authentication bypasses CsrfAuthenticationStrategy: replace the cookie deliberately.
		csrf.saveToken(csrf.generateToken(request), request, response);
		var cookie = cookies.issue(result.refreshToken());
		var user = new CurrentUserResponse(result.userId(), result.email());
		return ResponseEntity.status(status).header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(new AuthenticationResponse(user, result.accessToken().value(), result.accessToken().expiresAt()));
	}
}
