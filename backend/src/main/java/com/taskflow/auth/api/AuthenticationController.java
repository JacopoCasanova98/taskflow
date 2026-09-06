package com.taskflow.auth.api;

import com.taskflow.auth.api.dto.AuthenticationResponse;
import com.taskflow.auth.api.dto.LoginRequest;
import com.taskflow.auth.api.dto.RegisterRequest;
import com.taskflow.auth.application.AuthenticationResult;
import com.taskflow.auth.application.AuthenticationService;
import com.taskflow.auth.application.RefreshSessionProperties;
import com.taskflow.shared.error.ProblemDetails;
import com.taskflow.shared.security.CookieProperties;
import com.taskflow.shared.web.ApiPaths;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
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
	public static final String REFRESH_COOKIE = "TASKFLOW_REFRESH";
	private final AuthenticationService authentication;
	private final CookieProperties cookies;
	private final RefreshSessionProperties refresh;
	private final CsrfTokenRepository csrf;

	public AuthenticationController(AuthenticationService authentication, CookieProperties cookies,
			RefreshSessionProperties refresh, CsrfTokenRepository csrf) {
		this.authentication = authentication;
		this.cookies = cookies;
		this.refresh = refresh;
		this.csrf = csrf;
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

	@ExceptionHandler(BadCredentialsException.class)
	ProblemDetail invalidCredentials(HttpServletRequest request) {
		return ProblemDetails.problem(HttpStatus.UNAUTHORIZED, "Authentication failed",
				"Invalid email or password.", "INVALID_CREDENTIALS", request);
	}

	private ResponseEntity<AuthenticationResponse> success(AuthenticationResult result, HttpStatus status,
			HttpServletRequest request, HttpServletResponse response) {
		// Controller-based authentication bypasses CsrfAuthenticationStrategy: replace the cookie deliberately.
		csrf.saveToken(csrf.generateToken(request), request, response);
		ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, result.refreshToken())
				.httpOnly(true).secure(cookies.secure()).sameSite("Strict").path(ApiPaths.API + "/auth")
				.maxAge(refresh.ttl()).build();
		var user = new AuthenticationResponse.CurrentUser(result.userId(), result.email());
		return ResponseEntity.status(status).header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(new AuthenticationResponse(user, result.accessToken().value(), result.accessToken().expiresAt()));
	}
}
