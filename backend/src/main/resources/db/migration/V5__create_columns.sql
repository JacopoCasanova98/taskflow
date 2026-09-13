CREATE TABLE columns (
    id UUID PRIMARY KEY,
    board_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_columns_board FOREIGN KEY (board_id) REFERENCES boards(id) ON DELETE CASCADE,
    CONSTRAINT ck_columns_name_not_blank CHECK (name ~ '[^[:space:]]'),
    CONSTRAINT ck_columns_position_non_negative CHECK (position >= 0),
    CONSTRAINT uq_columns_board_position UNIQUE (board_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX idx_columns_board_position_id ON columns(board_id, position, id);
