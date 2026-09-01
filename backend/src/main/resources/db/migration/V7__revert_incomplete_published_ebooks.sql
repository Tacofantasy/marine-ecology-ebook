-- 演示电子书（V1 插入）缺少封面且简介不足 20 字，不满足发布完整性要求。
-- V6 仅为所有 PUBLISHED 记录补写了 published_at，未做发布完整性校验。
-- 将不满足发布条件的历史 PUBLISHED 电子书降为 DRAFT，并清除发布时间，
-- 避免公开接口向访客返回不完整电子书。
UPDATE ebooks
SET status        = 'DRAFT',
    published_at = NULL
WHERE status = 'PUBLISHED'
  AND (cover_url IS NULL
       OR CHAR_LENGTH(IFNULL(summary, '')) < 20
       OR source_note IS NULL);
