package com.taskflow.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CookieSecurityTest {
	@Test
	void cookieSecurityDefaultsToSecureAndCsrfCookieHonorsIt() {
		new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class).run(context -> {
			CookieProperties properties = context.getBean(CookieProperties.class);
			assertThat(properties.secure()).isTrue();
			var repository = new SecurityConfiguration().csrfTokenRepository(properties);
			var request = new MockHttpServletRequest();
			var response = new MockHttpServletResponse();
			repository.saveToken(repository.generateToken(request), request, response);
			assertThat(response.getCookie("XSRF-TOKEN").getSecure()).isTrue();
			assertThat(response.getCookie("XSRF-TOKEN").isHttpOnly()).isFalse();
		});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(CookieProperties.class)
	static class PropertiesConfiguration { }
}
