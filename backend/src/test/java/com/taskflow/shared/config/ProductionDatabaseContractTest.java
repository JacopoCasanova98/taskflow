package com.taskflow.shared.config;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductionDatabaseContractTest {
	private static final String URL = "jdbc:postgresql://database.invalid:5432/taskflow?sslmode=verify-full&sslrootcert="
			+ ProductionDatabaseContract.CA_PATH;
	@TempDir Path temporary;

	@Test
	void acceptsCanonicalAndEncodedCaPathWithoutOpeningConnection() {
		assertThatCode(() -> ProductionDatabaseContract.validateUrl(URL)).doesNotThrowAnyException();
		assertThatCode(() -> ProductionDatabaseContract.validateUrl(URL.replace("/opt/taskflow/trust/rds-ca-bundle.pem",
				"%2Fopt%2Ftaskflow%2Ftrust%2Frds-ca-bundle.pem"))).doesNotThrowAnyException();
	}

	@Test
	void rejectsWeakerModesCredentialsOverridesDuplicatesAndWrongTarget() {
		for (String mode : new String[] {"disable", "allow", "prefer", "require", "verify-ca"}) {
			assertThatThrownBy(() -> ProductionDatabaseContract.validateUrl(URL.replace("verify-full", mode)))
					.isInstanceOf(IllegalArgumentException.class);
		}
		for (String value : new String[] {"", "jdbc:postgresql://database.invalid/taskflow", URL + "&password=NOT-A-SECRET",
				URL + "&user=taskflowadmin", URL + "&sslmode=verify-full", URL + "&sslfactory=override",
				URL.replace(":5432", ":5433"), URL.replace("/taskflow?", "/other?"),
				URL.replace("database.invalid", "user@database.invalid"), URL + "#fragment",
				URL.replace("rds-ca-bundle.pem", "other.pem"), URL + "&", URL + "&sslhostnameverifier=override"}) {
			assertThatThrownBy(() -> ProductionDatabaseContract.validateUrl(value))
					.isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("NOT-A-SECRET");
		}
		assertThatThrownBy(() -> ProductionDatabaseContract.validateUrl(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void requiresAnExistingReadableNonEmptyTrustFile() throws Exception {
		Path fixture = temporary.resolve("public-trust-fixture.pem");
		assertThatThrownBy(() -> ProductionDatabaseContract.validateTrustFile(fixture)).isInstanceOf(IllegalArgumentException.class);
		Files.writeString(fixture, "");
		assertThatThrownBy(() -> ProductionDatabaseContract.validateTrustFile(fixture)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ProductionDatabaseContract.validateTrustFile(temporary)).isInstanceOf(IllegalArgumentException.class);
		Files.writeString(fixture, "PUBLIC-TRUST-PATH-FIXTURE-NOT-A-CERTIFICATE");
		assertThatCode(() -> ProductionDatabaseContract.validateTrustFile(fixture)).doesNotThrowAnyException();
		// File checks are not certificate/hostname verification; pgJDBC verifies the real chain at connection time.
	}
}
