package com.taskflow.user.persistence;

import com.taskflow.shared.persistence.BaseEntity;
import com.taskflow.user.domain.UserEmailNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
public class UserEntity extends BaseEntity {

	@Column(nullable = false, length = 254)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	protected UserEntity() {
	}

	public UserEntity(String email, String passwordHash) {
		this.email = UserEmailNormalizer.normalize(email);
		this.passwordHash = passwordHash;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}
}
