package com.taskflow.column.persistence;

import java.util.Objects;
import com.taskflow.board.persistence.BoardEntity;
import com.taskflow.column.domain.ColumnName;
import com.taskflow.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "columns")
public class ColumnEntity extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "board_id", nullable = false, updatable = false)
	private BoardEntity board;

	@Column(nullable = false, length = ColumnName.MAX_LENGTH)
	private String name;

	@Column(nullable = false)
	private int position;

	protected ColumnEntity() {
	}

	public ColumnEntity(BoardEntity board, String name, int position) {
		this.board = Objects.requireNonNull(board, "Column board is required.");
		this.name = ColumnName.requireValid(name);
		assignPosition(position);
	}

	public BoardEntity getBoard() {
		return board;
	}

	public String getName() {
		return name;
	}

	public int getPosition() {
		return position;
	}

	public void rename(String name) {
		this.name = ColumnName.requireValid(name);
	}

	/** Called within the parent Board's mutation lock and write transaction. */
	public void assignPosition(int position) {
		if (position < 0) {
			throw new IllegalArgumentException("Column position must be non-negative.");
		}
		this.position = position;
	}
}
