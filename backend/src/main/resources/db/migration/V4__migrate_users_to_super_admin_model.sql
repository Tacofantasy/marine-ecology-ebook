ALTER TABLE users
    MODIFY role ENUM('SUPER_ADMIN', 'ADMIN', 'USER') NOT NULL DEFAULT 'USER',
    ADD COLUMN display_name VARCHAR(64) NULL AFTER email,
    ADD COLUMN deleted_at DATETIME NULL AFTER status;

UPDATE users
SET display_name = username
WHERE display_name IS NULL;

UPDATE users
SET role = 'SUPER_ADMIN'
WHERE username = 'admin'
  AND role = 'ADMIN';
