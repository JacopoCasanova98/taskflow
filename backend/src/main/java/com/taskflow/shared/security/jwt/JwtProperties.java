package com.taskflow.shared.security.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("taskflow.security.jwt")
public record JwtProperties(
		String secretBase64,
		@DefaultValue("taskflow") String issuer,
		@DefaultValue("taskflow-api") String audience,
		@DefaultValue("15m") Duration accessTokenTtl
) {
	@Override
	public String toString() {
		return "JwtProperties[secretBase64=REDACTED]";
	}
}
