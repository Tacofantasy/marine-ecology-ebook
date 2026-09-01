ALTER TABLE categories
    ADD COLUMN parent_scope_id BIGINT GENERATED ALWAYS AS (COALESCE(parent_id, 0)) STORED AFTER parent_id,
    ADD UNIQUE KEY uk_categories_parent_scope_name (parent_scope_id, name);

INSERT INTO categories (parent_id, name, sort_order, status)
SELECT root.id, '海洋生态基础', 1, 'PUBLISHED'
FROM categories root
WHERE root.parent_id IS NULL
  AND root.name = '海洋生态'
  AND NOT EXISTS (
      SELECT 1
      FROM categories existing
      WHERE existing.parent_id = root.id
        AND existing.name = '海洋生态基础'
  );

UPDATE ebooks ebook
JOIN categories root
    ON root.id = ebook.category_id
   AND root.parent_id IS NULL
   AND root.name = '海洋生态'
JOIN categories child
    ON child.parent_id = root.id
   AND child.name = '海洋生态基础'
SET ebook.category_id = child.id
WHERE ebook.title = '认识海洋生态系统';
