package com.taskflow.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
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

@Tag(name = "Authentication", description = "Access tokens and the HttpOnly refresh-cookie lifecycle. "
		+ "Swagger UI is documentation/testing assistance, not the canonical SPA authentication implementation. "
		+ "It does not automatically manage TaskFlow refresh/CSRF state. Bootstrap with GET /api/auth/csrf and "
		+ "supply the current XSRF-TOKEN cookie value in X-XSRF-TOKEN for unsafe requests; update it after cookie rotation. "
		+ "Cookie-dependent Try it out requires browser cookie state and explicit CSRF state; seamless lifecycle execution is not guaranteed.")
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

	@Operation(operationId = "authMe", summary = "Get the current user",
			description = "Requires a Bearer access token. Returns SESSION_INVALID if its user no longer exists.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Success", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CurrentUserResponse.class))),
		@ApiResponse(responseCode = "401", ref = "#/components/responses/CurrentSessionRequired")
	})
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

	@Operation(operationId = "authCsrf", summary = "Bootstrap CSRF state",
			description = "Materializes the readable XSRF-TOKEN cookie when needed. No response body and no Bearer requirement. Send its current value in X-XSRF-TOKEN for unsafe requests.")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "No content", content = @Content)
	})
	@GetMapping("/csrf")
	ResponseEntity<Void> csrf() { return ResponseEntity.noContent().build(); }

	@Operation(operationId = "authRegister", summary = "Register an account",
			description = "No Bearer requirement. Creates the user, returns a short-lived access token and sets the HttpOnly TASKFLOW_REFRESH cookie (Path=/api/auth, SameSite=Strict; Secure by default). Rotates the XSRF-TOKEN cookie.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
		@ApiResponse(responseCode = "409", ref = "#/components/responses/EmailAlreadyRegistered")
	})
	@PostMapping("/register")
	ResponseEntity<AuthenticationResponse> register(@Valid @RequestBody RegisterRequest body,
			HttpServletRequest request, HttpServletResponse response) {
		return success(authentication.register(body.email(), body.password()), HttpStatus.CREATED, request, response);
	}

	@Operation(operationId = "authLogin", summary = "Sign in",
			description = "No Bearer requirement. Returns an access token, sets the HttpOnly TASKFLOW_REFRESH cookie and rotates XSRF-TOKEN. An existing refresh cookie is handled by the session lifecycle.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
		@ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials")
	})
	@PostMapping("/login")
	ResponseEntity<AuthenticationResponse> login(@Valid @RequestBody LoginRequest body,
			@Parameter(hidden = true) @CookieValue(name = REFRESH_COOKIE, required = false) String presentedRefresh,
			HttpServletRequest request, HttpServletResponse response) {
		return success(authentication.login(body.email(), body.password(), presentedRefresh), HttpStatus.OK, request, response);
	}

	@Operation(operationId = "authRefresh", summary = "Refresh the session",
			description = "Uses the HttpOnly TASKFLOW_REFRESH cookie, not Bearer authentication. Rotates refresh and CSRF cookies and returns a new access token. Missing, expired or unusable refresh state returns SESSION_INVALID and clears the refresh cookie.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Success", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthenticationResponse.class))),
		@ApiResponse(responseCode = "401", ref = "#/components/responses/SessionInvalid")
	})
	@PostMapping("/refresh")
	ResponseEntity<?> refresh(@Parameter(hidden = true) @CookieValue(name = REFRESH_COOKIE, required = false) String raw,
			HttpServletRequest request, HttpServletResponse response) {
		var result = lifecycle.refresh(raw);
		if (result.isPresent()) { return success(result.get(), HttpStatus.OK, request, response); }
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
				.body(ProblemDetails.problem(HttpStatus.UNAUTHORIZED, "Session unavailable",
						"Your session is no longer valid. Please sign in again.", "SESSION_INVALID", request));
	}

	@Operation(operationId = "authLogout", summary = "Sign out",
			description = "No Bearer requirement. Revokes the presented refresh session when available, clears the refresh cookie and replaces XSRF-TOKEN. Missing refresh cookie is accepted. Existing access tokens retain their normal expiry.")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "No content", content = @Content)
	})
	@PostMapping("/logout")
	ResponseEntity<Void> logout(@Parameter(hidden = true) @CookieValue(name = REFRESH_COOKIE, required = false) String raw,
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
