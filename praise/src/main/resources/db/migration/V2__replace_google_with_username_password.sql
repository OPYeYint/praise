ALTER TABLE users DROP COLUMN google_sub;
ALTER TABLE users DROP COLUMN email;
ALTER TABLE users DROP COLUMN display_name;
ALTER TABLE users ADD COLUMN username VARCHAR(50) NOT NULL;
ALTER TABLE users ADD COLUMN password_hash VARCHAR(100) NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_username_key UNIQUE (username);
