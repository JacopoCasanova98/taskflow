package com.taskflow.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;

import com.taskflow.shared.security.jwt.JwtConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class DemoRuntimeConfigurationTest {
	private Map<String, Object> fixture() {
		return new HashMap<>(Map.of("TASKFLOW_DB_URL", "jdbc:postgresql://database.invalid:5432/taskflow?sslmode=require",
				"TASKFLOW_DB_USERNAME", "taskflow_app", "TASKFLOW_DB_PASSWORD", "NOT-A-SECRET-test-fixture",
				"TASKFLOW_JWT_SECRET_BASE64", Base64.getEncoder().encodeToString(new byte[32])));
	}

	private ApplicationContextRunner runner(Map<String, Object> environment) {
		return new ApplicationContextRunner().withInitializer(context -> {
			context.getEnvironment().getPropertySources().remove("systemProperties");
			context.getEnvironment().getPropertySources().replace("systemEnvironment",
					new SystemEnvironmentPropertySource("systemEnvironment", environment));
			new ConfigDataApplicationContextInitializer().initialize(context);
		}).withPropertyValues("spring.config.location=classpath:/application.yml", "spring.config.import=",
				"spring.profiles.active=demo")
				.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
				.withUserConfiguration(DatabaseCredentialsConfiguration.class, DemoDatabaseConfiguration.class,
						JwtConfiguration.class, TimeConfiguration.class);
	}

	@Test
	void demoBindsSecureConfigurationWithoutConnectingToDatabase() {
		runner(fixture()).run(context -> {
			assertThat(context).hasNotFailed();
			var env = context.getEnvironment();
			assertThat(env.getProperty("server.port", Integer.class)).isEqualTo(8080);
			assertThat(env.getProperty("taskflow.database.production", Boolean.class, false)).isFalse();
			assertThat(env.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
			assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
			assertThat(env.getProperty("taskflow.security.cookies.secure", Boolean.class)).isTrue();
			assertThat(env.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
			assertThat(env.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
		});
	}

	@Test
	void portDefaultsAndEnvironmentOverrideWorkForLocalAndDemo() {
		for (String profile : new String[] {"default", "demo"}) {
			for (String port : new String[] {"", "10000"}) {
				Map<String, Object> env = fixture();
				if (!port.isEmpty()) { env.put("PORT", port); }
				runner(env).withPropertyValues("spring.profiles.active=" + profile).run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getEnvironment().getProperty("server.port", Integer.class))
							.isEqualTo(port.isEmpty() ? 8080 : 10000);
					assertThat(context.containsBean("demoDatabaseValidator")).isEqualTo("demo".equals(profile));
				});
			}
		}
	}

	@Test
	void missingOrBlankCredentialsAndJwtFail() {
		for (String name : new String[] {"TASKFLOW_DB_USERNAME", "TASKFLOW_DB_PASSWORD", "TASKFLOW_JWT_SECRET_BASE64"}) {
			Map<String, Object> env = fixture();
			env.remove(name);
			runner(env).run(context -> assertThat(context).hasFailed());
			env.put(name, "");
			runner(env).run(context -> assertThat(context).hasFailed());
		}
	}

	@Test
	void urlContractRejectsPlaintextCredentialsOverridesAndWrongDatabase() {
		for (String url : new String[] {"", "jdbc:postgresql://database.invalid:5432/taskflow",
				"jdbc:postgresql://database.invalid:5432/taskflow?sslmode=disable",
				"jdbc:postgresql://database.invalid:5432/other?sslmode=require",
				"jdbc:postgresql://user@database.invalid:5432/taskflow?sslmode=require",
				fixture().get("TASKFLOW_DB_URL") + "&password=NOT-A-SECRET",
				fixture().get("TASKFLOW_DB_URL") + "&sslmode=disable"}) {
			Map<String, Object> env = fixture();
			env.put("TASKFLOW_DB_URL", url);
			runner(env).run(context -> assertThat(context).hasFailed());
		}
		Map<String, Object> env = fixture();
		env.remove("TASKFLOW_DB_URL");
		runner(env).run(context -> assertThat(context).hasFailed());
		env.put("TASKFLOW_DB_URL", fixture().get("TASKFLOW_DB_URL") + "&channelBinding=require");
		runner(env).run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void wrongRoleAndProductionFlagAreRejected() {
		Map<String, Object> env = fixture();
		env.put("TASKFLOW_DB_USERNAME", "postgres");
		runner(env).run(context -> assertThat(context).hasFailed());
		runner(fixture()).withPropertyValues("taskflow.database.production=true")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void migrationAndSchemaValidationOverridesAreRejected() {
		for (String override : new String[] {"spring.flyway.enabled=false",
				"spring.jpa.hibernate.ddl-auto=update", "spring.jpa.hibernate.ddl-auto=none"}) {
			runner(fixture()).withPropertyValues(override).run(context -> assertThat(context).hasFailed());
		}
	}

	@Test
	void demoGuardRejectsMissingConnectionUrlAndProductionEvenInIsolation() {
		for (boolean production : new boolean[] {false, true}) {
			new ApplicationContextRunner().withUserConfiguration(DemoDatabaseConfiguration.class)
					.withPropertyValues("spring.profiles.active=demo", "spring.jpa.hibernate.ddl-auto=validate",
							"taskflow.database.production=" + production)
					.withBean(DataSource.class, () -> mock(DataSource.class))
					.withBean(JdbcConnectionDetails.class, () -> new JdbcConnectionDetails() {
						@Override public String getUsername() { return "taskflow_app"; }
						@Override public String getPassword() { return "NOT-A-SECRET-test-fixture"; }
						@Override public String getJdbcUrl() { return null; }
					}).run(context -> assertThat(context).hasFailed());
		}
	}
}
