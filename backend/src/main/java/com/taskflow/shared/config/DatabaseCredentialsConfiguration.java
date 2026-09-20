package com.taskflow.shared.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Reject absent credentials before datasource use, including unresolved local placeholders. */
@Configuration(proxyBeanMethods = false)
public class DatabaseCredentialsConfiguration {
	@Bean
	static BeanPostProcessor databaseCredentialsValidator(Environment environment,
			ObjectProvider<JdbcConnectionDetails> connectionDetails) {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) {
				if (bean instanceof DataSource) {
					JdbcConnectionDetails details = connectionDetails.getObject();
					for (String value : new String[] {details.getUsername(), details.getPassword()}) {
						try {
							if (value == null || environment.resolveRequiredPlaceholders(value).isBlank()) {
								throw new IllegalArgumentException();
							}
						} catch (IllegalArgumentException exception) {
							// Do not expose placeholder resolution details or credential values.
							throw new IllegalStateException("TaskFlow database username and password are required.");
						}
					}
				}
				return bean;
			}
		};
	}
}
