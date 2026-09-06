CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);
