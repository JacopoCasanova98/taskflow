package com.taskflow.shared.security.jwt;

import java.time.Clock;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {

	@Bean
	SecretKey jwtSecretKey(JwtProperties properties) {
		String encoded = properties.secretBase64();
		if (encoded == null || encoded.isBlank()) {
			throw new IllegalStateException("TaskFlow JWT signing secret is required.");
		}
		byte[] decoded;
		try {
			decoded = Base64.getDecoder().decode(encoded);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("TaskFlow JWT signing secret must be valid Base64.");
		}
		if (decoded.length != 32) {
			throw new IllegalStateException("TaskFlow JWT signing secret must decode to exactly 32 bytes.");
		}
		if (properties.issuer() == null || properties.issuer().isBlank()
				|| properties.audience() == null || properties.audience().isBlank()
				|| properties.accessTokenTtl() == null || properties.accessTokenTtl().isNegative()
				|| properties.accessTokenTtl().isZero()) {
			throw new IllegalStateException("TaskFlow JWT issuer, audience and positive token lifetime are required.");
		}
		return new SecretKeySpec(decoded, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties properties, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
				.macAlgorithm(MacAlgorithm.HS256).build();
		JwtTimestampValidator timestamps = new JwtTimestampValidator();
		timestamps.setClock(clock);
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
				new JwtClaimValidator<>("exp", value -> value != null),
				new JwtIssuerValidator(properties.issuer()), new JwtAudienceValidator(properties.audience())));
		return decoder;
	}
}
