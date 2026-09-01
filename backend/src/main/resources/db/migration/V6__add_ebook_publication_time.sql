ALTER TABLE ebooks
    ADD COLUMN published_at DATETIME NULL AFTER status;

UPDATE ebooks
SET published_at = COALESCE(updated_at, created_at)
WHERE status = 'PUBLISHED'
  AND published_at IS NULL;
