package com.taskflow.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("taskflow.security.cookies")
public record CookieProperties(@DefaultValue("true") boolean secure) {
}
