package com.taskflow.operations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;

import com.taskflow.shared.config.ProductionDatabaseContract;
import org.flywaydb.core.Flyway;

/** One-shot entry point in the existing backend JAR; never starts Spring or HTTP. */
public final class DatabaseMigration {
	private DatabaseMigration() { }

	public static void main(String[] args) {
		try {
			run(System.getenv());
		} catch (Exception exception) {
			// No driver exception, SQL, URL or credential content on the operator console.
			System.err.println("TaskFlow migration failed; do not deploy the backend.");
			System.exit(1);
		}
	}

	public static void run(Map<String, String> inputs) throws Exception {
		String url = inputs.get("TASKFLOW_DB_URL");
		ProductionDatabaseContract.validateUrl(url);
		ProductionDatabaseContract.validateTrustFile(Path.of(ProductionDatabaseContract.CA_PATH));
		String passwordFile = inputs.get("TASKFLOW_MIGRATION_PASSWORD_FILE");
		if (passwordFile == null || passwordFile.isBlank()) {
			throw new IllegalArgumentException("Migration password file is required.");
		}
		String password = Files.readString(Path.of(passwordFile));
		if (password.isBlank()) {
			throw new IllegalArgumentException("Migration password is required.");
		}
		migrate(url, password);
	}

	// Package-private for isolated PostgreSQL tests; the operator entry always enforces TLS.
	static void migrate(String url, String password) throws Exception {
		try (Connection connection = DriverManager.getConnection(url, "taskflow_migrator", password)) {
			try {
				Flyway.configure().dataSource(url, "taskflow_migrator", password)
						.locations("classpath:db/migration").defaultSchema("public")
						.cleanDisabled(true).validateMigrationNaming(true).load().migrate();
			} finally {
				// Default table DML also applies when Flyway creates its history table.
				// Remove mutation rights even after a failed migration; retain read visibility.
				try (var statement = connection.createStatement()) {
					var result = statement.executeQuery("SELECT to_regclass('public.flyway_schema_history') IS NOT NULL");
					result.next();
					if (result.getBoolean(1)) {
						statement.execute("REVOKE INSERT, UPDATE, DELETE ON public.flyway_schema_history FROM taskflow_app");
					}
				}
			}
		}
	}
}
