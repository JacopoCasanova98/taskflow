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

	@Test
	void refreshCookieSettingAndClearingShareBothSecurePolicies() {
		for (boolean secure : new boolean[] {true, false}) {
			var helper = new com.taskflow.auth.api.RefreshCookie(new CookieProperties(secure),
					new com.taskflow.auth.application.RefreshSessionProperties(java.time.Duration.ofDays(30)));
			var issued = helper.issue("opaque");
			var cleared = helper.clear();
			assertThat(cleared.getName()).isEqualTo(issued.getName()).isEqualTo("TASKFLOW_REFRESH");
			assertThat(cleared.getPath()).isEqualTo(issued.getPath()).isEqualTo("/api/auth");
			assertThat(cleared.getDomain()).isNull();
			assertThat(issued.getDomain()).isNull();
			assertThat(cleared.isSecure()).isEqualTo(issued.isSecure()).isEqualTo(secure);
			assertThat(cleared.isHttpOnly()).isEqualTo(issued.isHttpOnly()).isTrue();
			assertThat(cleared.getSameSite()).isEqualTo(issued.getSameSite()).isEqualTo("Strict");
			assertThat(cleared.getMaxAge()).isEqualTo(java.time.Duration.ZERO);
			assertThat(cleared.getValue()).isEmpty();
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(CookieProperties.class)
	static class PropertiesConfiguration { }
}
