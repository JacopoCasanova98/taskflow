package com.taskflow.shared.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** A narrow production JDBC contract; local development keeps its existing URL. */
public final class ProductionDatabaseContract {
	public static final String CA_PATH = "/opt/taskflow/trust/rds-ca-bundle.pem";

	private ProductionDatabaseContract() { }

	public static void validateUrl(String url) {
		try {
			if (url == null || !url.startsWith("jdbc:postgresql://")) {
				throw new IllegalArgumentException();
			}
			URI uri = URI.create(url.substring(5));
			if (uri.getHost() == null || uri.getPort() != 5432 || uri.getUserInfo() != null
					|| uri.getFragment() != null || !"/taskflow".equals(uri.getRawPath())) {
				throw new IllegalArgumentException();
			}
			Map<String, String> parameters = new HashMap<>();
			for (String part : uri.getRawQuery().split("&", -1)) {
				String[] pair = part.split("=", 2);
				if (pair.length != 2 || parameters.putIfAbsent(decode(pair[0]), decode(pair[1])) != null) {
					throw new IllegalArgumentException();
				}
			}
			// Whitelist also excludes credentials, sslfactory and hostname-verifier overrides.
			if (!parameters.equals(Map.of("sslmode", "verify-full", "sslrootcert", CA_PATH))) {
				throw new IllegalArgumentException();
			}
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("Production database URL requires taskflow, port 5432, verify-full and the configured CA path.");
		}
	}

	public static void validateTrustFile(Path path) {
		try {
			if (!Files.isRegularFile(path) || !Files.isReadable(path) || Files.size(path) == 0) {
				throw new IllegalArgumentException();
			}
		} catch (Exception exception) {
			throw new IllegalArgumentException("Production database CA file must be present, readable and non-empty.");
		}
	}

	private static String decode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}
}
