-- V9：统计快照与章节字数支持
-- 1) daily_stats：每日统计快照表。
--    stat_date 为主键，快照写入统一使用 INSERT ... ON DUPLICATE KEY UPDATE（见 DailyStatMapper.upsert），
--    因此快照任务可以安全地重复执行/回补，重跑只会用最新值覆盖，不会产生重复行或叠加。
CREATE TABLE IF NOT EXISTS daily_stats (
    stat_date DATE NOT NULL,
    total_view_count BIGINT NOT NULL DEFAULT 0 COMMENT '全站累计阅读量',
    total_like_count BIGINT NOT NULL DEFAULT 0 COMMENT '全站累计点赞量',
    view_delta BIGINT NOT NULL DEFAULT 0 COMMENT '当日新增阅读量',
    like_delta BIGINT NOT NULL DEFAULT 0 COMMENT '当日新增点赞量',
    published_ebook_count BIGINT NOT NULL DEFAULT 0 COMMENT '已发布电子书数',
    active_user_count BIGINT NOT NULL DEFAULT 0 COMMENT '有效用户数',
    total_word_count BIGINT NOT NULL DEFAULT 0 COMMENT '已发布内容总字数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) chapters.word_count：章节正文字数（剥离 HTML 标签后的纯文本长度），
--    用于计算“预计阅读时长”（约 400 字/分钟）。
--    字段默认 0，下面的回填 UPDATE 为纯重算语句，天然可重复执行。
ALTER TABLE chapters
    ADD COLUMN word_count BIGINT NOT NULL DEFAULT 0 COMMENT '正文字数（纯文本）' AFTER source_note;

-- 回填存量章节字数：剥离标签（以空格替换避免相邻文字粘连）后取字符数。
UPDATE chapters
SET word_count = CHAR_LENGTH(TRIM(REGEXP_REPLACE(content, '<[^>]*>', ' ')))
WHERE content IS NOT NULL;
