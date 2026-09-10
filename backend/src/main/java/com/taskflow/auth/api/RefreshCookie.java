package com.taskflow.auth.api;

import java.time.Duration;
import com.taskflow.auth.application.RefreshSessionProperties;
import com.taskflow.shared.security.CookieProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookie {
	public static final String NAME = "TASKFLOW_REFRESH";
	private final CookieProperties cookies;
	private final RefreshSessionProperties refresh;

	public RefreshCookie(CookieProperties cookies, RefreshSessionProperties refresh) {
		this.cookies = cookies;
		this.refresh = refresh;
	}

	public ResponseCookie issue(String raw) { return build(raw, refresh.ttl()); }
	public ResponseCookie clear() { return build("", Duration.ZERO); }

	private ResponseCookie build(String value, Duration maxAge) {
		return ResponseCookie.from(NAME, value).httpOnly(true).secure(cookies.secure())
				.sameSite("Strict").path("/api/auth").maxAge(maxAge).build();
	}
}
