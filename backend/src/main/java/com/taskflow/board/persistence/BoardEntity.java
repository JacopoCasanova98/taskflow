package com.taskflow.board.persistence;

import java.util.Objects;
import java.util.UUID;
import com.taskflow.board.domain.BoardName;
import com.taskflow.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "boards")
public class BoardEntity extends BaseEntity {

	@Column(name = "owner_id", nullable = false, updatable = false)
	private UUID ownerId;

	@Column(nullable = false, length = BoardName.MAX_LENGTH)
	private String name;

	protected BoardEntity() {
	}

	public BoardEntity(UUID ownerId, String name) {
		this.ownerId = Objects.requireNonNull(ownerId, "Board owner is required.");
		this.name = BoardName.requireValid(name);
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public String getName() {
		return name;
	}

	public void rename(String name) {
		this.name = BoardName.requireValid(name);
	}
}
