ALTER TABLE users ADD COLUMN avatar bytea;
ALTER TABLE users ADD COLUMN credential_version bigint NOT NULL DEFAULT 0 CHECK (credential_version >= 0);
