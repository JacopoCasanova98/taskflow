package com.taskflow.shared.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Demo-only fail-closed boundary. The AWS production contract is never relaxed. */
@Configuration(proxyBeanMethods = false)
@Profile("demo")
public class DemoDatabaseConfiguration {
	@Bean
	static BeanPostProcessor demoDatabaseValidator(Environment environment,
			ObjectProvider<JdbcConnectionDetails> connectionDetails) {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) {
				if (bean instanceof DataSource) {
					JdbcConnectionDetails details = connectionDetails.getObject();
					String url = details.getJdbcUrl();
					// Deliberately narrow: no embedded credentials, TLS overrides or duplicate options.
					if (environment.getProperty("taskflow.database.production", Boolean.class, false)
							|| !environment.getProperty("spring.flyway.enabled", Boolean.class, true)
							|| !"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
							|| !"taskflow_app".equals(details.getUsername())
							|| url == null || !url.matches("jdbc:postgresql://[A-Za-z0-9.-]+:5432/taskflow"
									+ "\\?sslmode=require(?:&channelBinding=require)?")) {
						throw new IllegalStateException("Demo requires taskflow, taskflow_app, TLS, Flyway and schema validation without the production flag.");
					}
				}
				return bean;
			}
		};
	}
}
