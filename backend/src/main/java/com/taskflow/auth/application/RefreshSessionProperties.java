package com.taskflow.auth.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("taskflow.security.refresh")
public record RefreshSessionProperties(@DefaultValue("30d") Duration ttl) {
	public RefreshSessionProperties {
		if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.getNano() != 0) {
			throw new IllegalArgumentException("Refresh lifetime must be a positive whole-second duration.");
		}
	}
}
