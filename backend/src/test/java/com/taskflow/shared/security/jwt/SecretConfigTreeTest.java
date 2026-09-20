package com.taskflow.shared.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import com.taskflow.shared.config.TimeConfiguration;
import com.taskflow.shared.config.DatabaseCredentialsConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class SecretConfigTreeTest {
	@TempDir Path tree;

	private String fixtureKey() {
		return Base64.getEncoder().encodeToString(new byte[32]);
	}

	private ApplicationContextRunner runner(Map<String, Object> environment) {
		return new ApplicationContextRunner()
				.withInitializer(context -> {
					context.getEnvironment().getPropertySources().remove("systemProperties");
					context.getEnvironment().getPropertySources().replace("systemEnvironment",
							new SystemEnvironmentPropertySource("systemEnvironment", environment));
					new ConfigDataApplicationContextInitializer().initialize(context);
				})
				.withPropertyValues("spring.config.location=classpath:/application.yml",
						"spring.config.import=optional:configtree:" + tree + "/")
				.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
				.withUserConfiguration(DatabaseCredentialsConfiguration.class,
						JwtConfiguration.class, TimeConfiguration.class);
	}

	private void writeTree() throws Exception {
		Files.writeString(tree.resolve("spring.datasource.username"), "fixture-tree-user");
		Files.writeString(tree.resolve("spring.datasource.password"), "NOT-A-SECRET-tree-fixture");
		Files.writeString(tree.resolve("taskflow.security.jwt.secret-base64"), fixtureKey());
	}

	@Test
	void mountedPropertiesBindWithoutEnvironmentSecrets() throws Exception {
		writeTree();
		runner(Map.of("TASKFLOW_DB_URL", "jdbc:postgresql://database.invalid/fixture")).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(DataSourceProperties.class).getUsername()).isEqualTo("fixture-tree-user");
			assertThat(context.getBean(DataSourceProperties.class).getPassword()).isEqualTo("NOT-A-SECRET-tree-fixture");
			assertThat(context.getBean(JwtProperties.class).secretBase64()).isEqualTo(fixtureKey());
		});
	}

	@Test
	void localEnvironmentContractWorksWithoutMountedFiles() {
		runner(Map.of("TASKFLOW_DB_URL", "jdbc:postgresql://database.invalid/fixture",
				"TASKFLOW_DB_USERNAME", "fixture-local-user", "TASKFLOW_DB_PASSWORD", "NOT-A-SECRET-local-fixture",
				"TASKFLOW_JWT_SECRET_BASE64", fixtureKey())).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(DataSourceProperties.class).getUsername()).isEqualTo("fixture-local-user");
			assertThat(context.getBean(DataSourceProperties.class).getPassword()).isEqualTo("NOT-A-SECRET-local-fixture");
			assertThat(context.getBean(JwtProperties.class).secretBase64()).isEqualTo(fixtureKey());
		});
	}

	@Test
	void treeOverridesApplicationPlaceholderFallbacks() throws Exception {
		writeTree();
		runner(Map.of("TASKFLOW_DB_URL", "jdbc:postgresql://database.invalid/fixture",
				"TASKFLOW_DB_USERNAME", "unused-local", "TASKFLOW_DB_PASSWORD", "unused-local",
				"TASKFLOW_JWT_SECRET_BASE64", "invalid-unused-local")).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(DataSourceProperties.class).getUsername()).isEqualTo("fixture-tree-user");
			assertThat(context.getBean(DataSourceProperties.class).getPassword()).isEqualTo("NOT-A-SECRET-tree-fixture");
			assertThat(context.getBean(JwtProperties.class).secretBase64()).isEqualTo(fixtureKey());
		});
	}

	@Test
	void missingRequiredFilesFailWithoutLocalFallbacks() throws Exception {
		for (String name : new String[] {"spring.datasource.username", "spring.datasource.password",
				"taskflow.security.jwt.secret-base64"}) {
			writeTree();
			Files.delete(tree.resolve(name));
			runner(Map.of("TASKFLOW_DB_URL", "jdbc:postgresql://database.invalid/fixture"))
					.run(context -> assertThat(context).hasFailed());
		}
	}

	@Test
	void absentOptionalDirectoryDoesNotSupplyInsecureDefaults() {
		runner(Map.of("TASKFLOW_DB_URL", "jdbc:postgresql://database.invalid/fixture"))
				.withPropertyValues("spring.config.import=optional:configtree:" + tree.resolve("absent") + "/")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void serviceConnectionCredentialsRemainSupported() {
		runner(Map.of("TASKFLOW_JWT_SECRET_BASE64", fixtureKey()))
				.withBean(JdbcConnectionDetails.class, () -> new JdbcConnectionDetails() {
					@Override public String getUsername() { return "fixture-service-user"; }
					@Override public String getPassword() { return "NOT-A-SECRET-service-fixture"; }
					@Override public String getJdbcUrl() { return "jdbc:postgresql://database.invalid/fixture"; }
				})
				.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void emptyMountedCredentialsFail() throws Exception {
		for (String name : new String[] {"spring.datasource.username", "spring.datasource.password",
				"taskflow.security.jwt.secret-base64"}) {
			writeTree();
			Files.writeString(tree.resolve(name), "");
			runner(Map.of("TASKFLOW_DB_URL", "jdbc:postgresql://database.invalid/fixture"))
					.run(context -> assertThat(context).hasFailed());
		}
	}

}
