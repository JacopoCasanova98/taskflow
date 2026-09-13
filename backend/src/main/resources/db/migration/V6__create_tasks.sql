CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    column_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(4000),
    priority VARCHAR(6) NOT NULL,
    due_date DATE,
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_tasks_column FOREIGN KEY (column_id) REFERENCES columns(id) ON DELETE CASCADE,
    CONSTRAINT ck_tasks_title_not_blank CHECK (title ~ '[^[:space:]]'),
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_tasks_position CHECK (position >= 0),
    CONSTRAINT uq_tasks_column_position UNIQUE (column_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX idx_tasks_column_position_id ON tasks (column_id, position, id);
