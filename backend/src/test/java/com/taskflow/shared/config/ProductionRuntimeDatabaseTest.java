package com.taskflow.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionRuntimeDatabaseTest {
	private ApplicationContextRunner runner(String username, String url) {
		return new ApplicationContextRunner().withUserConfiguration(DatabaseCredentialsConfiguration.class)
				.withBean(DataSource.class, () -> mock(DataSource.class))
				.withBean(JdbcConnectionDetails.class, () -> new JdbcConnectionDetails() {
					@Override public String getUsername() { return username; }
					@Override public String getPassword() { return "NOT-A-SECRET-fixture"; }
					@Override public String getJdbcUrl() { return url; }
				})
				.withPropertyValues("taskflow.database.production=true", "spring.flyway.enabled=false",
						"spring.jpa.hibernate.ddl-auto=validate");
	}

	@Test
	void productionRejectsMasterMigratorAndAutomaticDdl() {
		for (String user : new String[] {"taskflowadmin", "taskflow_migrator", "postgres"}) {
			runner(user, "jdbc:postgresql://database.invalid/taskflow").run(context -> assertThat(context).hasFailed());
		}
		runner("taskflow_app", "jdbc:postgresql://database.invalid/taskflow")
				.withPropertyValues("spring.flyway.enabled=true").run(context -> assertThat(context).hasFailed());
		runner("taskflow_app", "jdbc:postgresql://database.invalid/taskflow")
				.withPropertyValues("spring.jpa.hibernate.ddl-auto=update").run(context -> assertThat(context).hasFailed());
	}

	@Test
	void productionRejectsNonVerifyingUrlWhileLocalConfigurationIsUnaffected() {
		runner("taskflow_app", "jdbc:postgresql://database.invalid/taskflow")
				.run(context -> assertThat(context).hasFailed());
		runner("local-fixture", "jdbc:postgresql://database.invalid/taskflow")
				.withPropertyValues("taskflow.database.production=false", "spring.flyway.enabled=true")
				.run(context -> assertThat(context).hasNotFailed());
	}
}
