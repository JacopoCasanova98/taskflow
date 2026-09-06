package com.taskflow.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("Temporarily changes the JVM default locale")
class UserEmailNormalizerTest {

	@Test
	void trimsSurroundingWhitespace() {
		assertThat(UserEmailNormalizer.normalize(" \t user@example.com\r\n "))
				.isEqualTo("user@example.com");
	}

	@Test
	void lowercasesAsciiEmail() {
		assertThat(UserEmailNormalizer.normalize("USER@EXAMPLE.COM"))
				.isEqualTo("user@example.com");
	}

	@Test
	void normalizesIndependentlyOfDefaultLocale() {
		Locale original = Locale.getDefault();
		Locale originalDisplay = Locale.getDefault(Locale.Category.DISPLAY);
		Locale originalFormat = Locale.getDefault(Locale.Category.FORMAT);
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));

			assertThat(UserEmailNormalizer.normalize("IDENTITY@EXAMPLE.COM"))
					.isEqualTo("identity@example.com");
		} finally {
			Locale.setDefault(original);
			Locale.setDefault(Locale.Category.DISPLAY, originalDisplay);
			Locale.setDefault(Locale.Category.FORMAT, originalFormat);
		}
	}
}
