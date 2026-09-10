CREATE TABLE boards (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_boards_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_boards_name_not_blank CHECK (name ~ '[^[:space:]]')
);

CREATE INDEX idx_boards_owner_id ON boards(owner_id);
