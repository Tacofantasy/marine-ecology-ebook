package com.marine.ecobook.stats.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marine.ecobook.stats.model.DailyStat;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DailyStatMapper extends BaseMapper<DailyStat> {

    /**
     * 幂等快照写入：stat_date 为主键，冲突时用本次计算值覆盖。
     * 服务层限制历史快照的再次写入；当日重跑不会产生重复行或数值叠加。
     */
    @Insert("""
            INSERT INTO daily_stats
                (stat_date, total_view_count, total_like_count, view_delta, like_delta,
                 published_ebook_count, active_user_count, total_word_count)
            VALUES
                (#{statDate}, #{totalViewCount}, #{totalLikeCount}, #{viewDelta}, #{likeDelta},
                 #{publishedEbookCount}, #{activeUserCount}, #{totalWordCount})
            ON DUPLICATE KEY UPDATE
                total_view_count = VALUES(total_view_count),
                total_like_count = VALUES(total_like_count),
                view_delta = VALUES(view_delta),
                like_delta = VALUES(like_delta),
                published_ebook_count = VALUES(published_ebook_count),
                active_user_count = VALUES(active_user_count),
                total_word_count = VALUES(total_word_count),
                updated_at = CURRENT_TIMESTAMP
            """)
    int upsert(DailyStat stat);

    @Select("SELECT COALESCE(SUM(view_count), 0) FROM ebooks")
    long sumTotalViewCount();

    @Select("SELECT COUNT(*) FROM likes")
    long countTotalLikes();

    @Select("SELECT COUNT(*) FROM likes WHERE created_at >= #{start} AND created_at < #{end}")
    long countLikesBetween(@Param("start") java.time.LocalDateTime start,
                           @Param("end") java.time.LocalDateTime end);

    @Select("SELECT COUNT(*) FROM ebooks WHERE status = 'PUBLISHED'")
    long countPublishedEbooks();

    @Select("SELECT COUNT(*) FROM users WHERE status = 1 AND deleted_at IS NULL")
    long countActiveUsers();

    /**
     * 已发布电子书下已发布章节的正文字数总和，用于估算预计阅读时长。
     */
    @Select("""
            SELECT COALESCE(SUM(c.word_count), 0)
            FROM chapters c
            JOIN ebooks e ON c.ebook_id = e.id
            WHERE e.status = 'PUBLISHED' AND c.status = 'PUBLISHED'
            """)
    long sumPublishedWordCount();

    @Select("SELECT * FROM daily_stats WHERE stat_date >= #{from} AND stat_date <= #{to} ORDER BY stat_date ASC")
    List<DailyStat> selectBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
